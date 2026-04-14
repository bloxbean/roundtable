package com.bloxbean.roundtable.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bloxbean.roundtable.model.MessageType;
import com.bloxbean.roundtable.service.MessageService;
import com.bloxbean.roundtable.service.TopicService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MessageTools {

    private final MessageService messageService;
    private final TopicService topicService;
    private final ObjectMapper objectMapper;

    public MessageTools(MessageService messageService, TopicService topicService, ObjectMapper objectMapper) {
        this.messageService = messageService;
        this.topicService = topicService;
        this.objectMapper = objectMapper;
    }

    @McpTool(name = "send_message", description = "Send a message to a topic. The message type determines "
            + "how the workflow state machine reacts. Turn enforcement and freshness checks are applied. "
            + "Types: PLAN (submit a plan), REVISION (submit revised plan), "
            + "REVIEW (provide feedback requesting changes, same effect as REJECTION), "
            + "APPROVAL (approve the current version), REJECTION (reject with feedback), CHAT (general discussion)")
    public String sendMessage(
            @McpArg(description = "Topic name") String topicName,
            @McpArg(description = "Your agent name (must be registered on the topic)") String senderName,
            @McpArg(description = "Message content") String content,
            @McpArg(description = "Message type: PLAN, REVISION, REVIEW, APPROVAL, REJECTION, or CHAT") String type,
            @McpArg(description = "ID of the last message you read via get_messages. "
                    + "Used to verify you have seen all relevant messages before acting. "
                    + "Pass 0 for the first message on a new topic.") long lastSeenMessageId) {
        var msgType = MessageType.valueOf(type.toUpperCase());
        var msg = messageService.sendMessage(topicName, senderName, content, msgType, lastSeenMessageId);
        var topic = topicService.getByName(topicName);
        return toJson(Map.of(
                "status", "sent",
                "messageId", msg.getId(),
                "round", msg.getRound(),
                "topicStatus", topic.getStatus().name()
        ));
    }

    @McpTool(name = "get_messages", description = "Get messages from a topic. Use sinceId for efficient polling: "
            + "pass the ID of the last message you received to get only newer messages. "
            + "This also updates your heartbeat to signal you are still active.")
    public String getMessages(
            @McpArg(description = "Topic name") String topicName,
            @McpArg(description = "Your agent name (for heartbeat tracking)", required = false) String agentName,
            @McpArg(description = "Only return messages with ID greater than this. Pass 0 or omit for all messages.", required = false) Long sinceId) {
        var messages = messageService.getMessages(topicName, agentName, sinceId);
        var topic = topicService.getByName(topicName);

        var result = new LinkedHashMap<String, Object>();
        result.put("topicStatus", topic.getStatus().name());
        result.put("currentRound", topic.getCurrentRound());
        result.put("messageCount", messages.size());
        if (!messages.isEmpty()) {
            result.put("headMessageId", messages.getLast().getId());
        }
        result.put("messages", messages.stream().map(m -> {
            var map = new LinkedHashMap<String, Object>();
            map.put("id", m.getId());
            map.put("senderName", m.getSenderName());
            map.put("senderRole", m.getSenderRole());
            map.put("type", m.getType().name());
            map.put("phase", m.getPhase());
            map.put("round", m.getRound());
            map.put("content", m.getContent());
            map.put("createdAt", m.getCreatedAt().toString());
            return map;
        }).toList());
        return toJson(result);
    }

    @McpTool(name = "get_latest", description = "Get the most recent message on a topic, optionally filtered by sender role. "
            + "Useful to quickly check the latest plan or review without fetching all messages.")
    public String getLatest(
            @McpArg(description = "Topic name") String topicName,
            @McpArg(description = "Filter by sender role (e.g. 'planner', 'reviewer'). Omit for the absolute latest.", required = false) String role) {
        var msg = messageService.getLatest(topicName, role);
        if (msg == null) {
            return toJson(Map.of("status", "no_messages", "topic", topicName));
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("id", msg.getId());
        result.put("senderName", msg.getSenderName());
        result.put("senderRole", msg.getSenderRole());
        result.put("type", msg.getType().name());
        result.put("round", msg.getRound());
        result.put("content", msg.getContent());
        result.put("createdAt", msg.getCreatedAt().toString());
        return toJson(result);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"JSON serialization failed: " + e.getMessage() + "\"}";
        }
    }
}
