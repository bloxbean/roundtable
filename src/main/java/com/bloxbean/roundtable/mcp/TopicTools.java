package com.bloxbean.roundtable.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bloxbean.roundtable.model.QuorumPolicy;
import com.bloxbean.roundtable.model.TopicStatus;
import com.bloxbean.roundtable.service.AgentService;
import com.bloxbean.roundtable.service.TopicService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TopicTools {

    private final TopicService topicService;
    private final AgentService agentService;
    private final ObjectMapper objectMapper;

    public TopicTools(TopicService topicService, AgentService agentService, ObjectMapper objectMapper) {
        this.topicService = topicService;
        this.agentService = agentService;
        this.objectMapper = objectMapper;
    }

    @McpTool(name = "create_topic", description = "Create a new coordination topic for agents to collaborate on. "
            + "Topics progress through phases (default: PLANNING,IMPLEMENTATION). "
            + "Each phase has its own review rounds. When a phase is approved, the next phase begins.")
    public String createTopic(
            @McpArg(description = "Unique name for the topic") String name,
            @McpArg(description = "Description of what this topic is about", required = false) String description,
            @McpArg(description = "Quorum policy: ALL (every reviewer must respond), MAJORITY, or ANY_ONE. Default: ALL", required = false) String quorumPolicy,
            @McpArg(description = "Timeout in seconds for each review round. Default: 300", required = false) Integer roundTimeoutSeconds,
            @McpArg(description = "Comma-separated list of phases. Default: PLANNING,IMPLEMENTATION. Example: PLANNING,IMPLEMENTATION,TESTING", required = false) String phases) {
        var policy = quorumPolicy != null ? QuorumPolicy.valueOf(quorumPolicy.toUpperCase()) : QuorumPolicy.ALL;
        var timeout = roundTimeoutSeconds != null ? roundTimeoutSeconds : 300;
        var topic = topicService.createTopic(name, description, policy, timeout, phases);
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "created");
        result.put("name", topic.getName());
        result.put("id", topic.getId());
        result.put("phases", topic.getPhaseList());
        result.put("currentPhase", topic.getCurrentPhase());
        result.put("quorumPolicy", topic.getQuorumPolicy().name());
        result.put("roundTimeoutSeconds", topic.getRoundTimeoutSeconds());
        result.put("topicStatus", topic.getStatus().name());
        return toJson(result);
    }

    @McpTool(name = "list_topics", description = "List all coordination topics with their current status")
    public String listTopics() {
        var topics = topicService.listTopics();
        var summaries = topics.stream().map(t -> {
            var map = new LinkedHashMap<String, Object>();
            map.put("name", t.getName());
            map.put("status", t.getStatus().name());
            map.put("currentRound", t.getCurrentRound());
            map.put("quorumPolicy", t.getQuorumPolicy().name());
            if (t.getDescription() != null) map.put("description", t.getDescription());
            return map;
        }).toList();
        return toJson(summaries);
    }

    @McpTool(name = "get_topic_status", description = "Get detailed status of a topic including current state, "
            + "round number, registered agents, and pending reviewers")
    public String getTopicStatus(
            @McpArg(description = "Topic name") String topicName) {
        var topic = topicService.getByName(topicName);
        var agents = agentService.listAgents(topicName);

        var detail = new LinkedHashMap<String, Object>();
        detail.put("name", topic.getName());
        detail.put("status", topic.getStatus().name());
        detail.put("currentPhase", topic.getCurrentPhase());
        detail.put("phaseProgress", (topic.getCurrentPhaseIndex() + 1) + " of " + topic.getPhaseList().length);
        detail.put("phases", topic.getPhaseList());
        detail.put("currentRound", topic.getCurrentRound());
        detail.put("quorumPolicy", topic.getQuorumPolicy().name());
        detail.put("lockedByAgent", topic.getLockedByAgent());
        if (topic.getDescription() != null) detail.put("description", topic.getDescription());
        detail.put("agents", agents.stream().map(a -> Map.of(
                "name", a.getName(),
                "role", a.getRole(),
                "active", a.isActive(),
                "joinedAtRound", a.getJoinedAtRound()
        )).toList());
        detail.put("messageCount", topic.getMessages().size());
        return toJson(detail);
    }

    @McpTool(name = "set_breakpoint", description = "Set a breakpoint so the topic pauses before reaching a given phase. "
            + "When the breakpoint is hit, the topic enters BREAKPOINT state and waits for approve() call. "
            + "Valid phases: IN_REVIEW, APPROVED, COMPLETED")
    public String setBreakpoint(
            @McpArg(description = "Topic name") String topicName,
            @McpArg(description = "Phase to pause at: IN_REVIEW, APPROVED, or COMPLETED") String atPhase) {
        var phase = TopicStatus.valueOf(atPhase.toUpperCase());
        topicService.setBreakpoint(topicName, phase);
        return toJson(Map.of("status", "breakpoint_set", "topic", topicName, "atPhase", phase.name()));
    }

    @McpTool(name = "remove_breakpoint", description = "Remove a previously set breakpoint from a topic")
    public String removeBreakpoint(
            @McpArg(description = "Topic name") String topicName,
            @McpArg(description = "Phase to remove breakpoint from") String atPhase) {
        var phase = TopicStatus.valueOf(atPhase.toUpperCase());
        topicService.removeBreakpoint(topicName, phase);
        return toJson(Map.of("status", "breakpoint_removed", "topic", topicName, "atPhase", phase.name()));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"JSON serialization failed: " + e.getMessage() + "\"}";
        }
    }
}
