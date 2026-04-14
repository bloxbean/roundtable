package com.bloxbean.roundtable.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bloxbean.roundtable.service.AgentService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AgentTools {

    private final AgentService agentService;
    private final ObjectMapper objectMapper;

    public AgentTools(AgentService agentService, ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.objectMapper = objectMapper;
    }

    @McpTool(name = "register_agent", description = "Register an agent on a topic with a specific role. "
            + "Roles: 'planner' (one per topic, submits plans/revisions), 'reviewer' (reviews and approves/rejects). "
            + "Other roles like 'security-auditor' act as reviewers. Call this when joining a coordination topic.")
    public String registerAgent(
            @McpArg(description = "Topic name to join") String topicName,
            @McpArg(description = "Unique agent name, e.g. 'claude-planner' or 'codex-reviewer'") String agentName,
            @McpArg(description = "Agent role: 'planner', 'reviewer', or custom role") String role) {
        var reg = agentService.registerAgent(topicName, agentName, role);
        return toJson(Map.of(
                "status", "registered",
                "agent", reg.getName(),
                "role", reg.getRole(),
                "topic", topicName,
                "joinedAtRound", reg.getJoinedAtRound()
        ));
    }

    @McpTool(name = "unregister_agent", description = "Unregister an agent from a topic. "
            + "If the agent was the planner, the planner lock is released.")
    public String unregisterAgent(
            @McpArg(description = "Topic name") String topicName,
            @McpArg(description = "Agent name to remove") String agentName) {
        agentService.unregisterAgent(topicName, agentName);
        return toJson(Map.of("status", "unregistered", "agent", agentName, "topic", topicName));
    }

    @McpTool(name = "list_agents", description = "List all active agents registered on a topic with their roles")
    public String listAgents(
            @McpArg(description = "Topic name") String topicName) {
        var agents = agentService.listAgents(topicName);
        var list = agents.stream().map(a -> {
            var map = new LinkedHashMap<String, Object>();
            map.put("name", a.getName());
            map.put("role", a.getRole());
            map.put("joinedAtRound", a.getJoinedAtRound());
            map.put("registeredAt", a.getRegisteredAt().toString());
            return map;
        }).toList();
        return toJson(list);
    }

    @McpTool(name = "my_topics", description = "List all topics this agent is currently registered on, "
            + "with role, status, auto-pilot state, and current round for each.")
    public String myTopics(
            @McpArg(description = "Your agent name") String agentName) {
        var registrations = agentService.listTopicsForAgent(agentName);
        if (registrations.isEmpty()) {
            return toJson(java.util.Map.of("agent", agentName, "topics", java.util.List.of(),
                    "message", "Not registered on any topics."));
        }
        var topics = registrations.stream().map(r -> {
            var topic = r.getTopic();
            var map = new LinkedHashMap<String, Object>();
            map.put("topicName", topic.getName());
            map.put("role", r.getRole());
            map.put("topicStatus", topic.getStatus().name());
            map.put("currentRound", topic.getCurrentRound());
            map.put("autoPilotEnabled", r.isAutoPilotEnabled());
            map.put("joinedAtRound", r.getJoinedAtRound());
            if (topic.getDescription() != null) map.put("description", topic.getDescription());
            return map;
        }).toList();
        return toJson(java.util.Map.of("agent", agentName, "topicCount", topics.size(), "topics", topics));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"JSON serialization failed: " + e.getMessage() + "\"}";
        }
    }
}
