package com.bloxbean.roundtable.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bloxbean.roundtable.service.WorkflowEngine;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WorkflowTools {

    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;

    public WorkflowTools(WorkflowEngine workflowEngine, ObjectMapper objectMapper) {
        this.workflowEngine = workflowEngine;
        this.objectMapper = objectMapper;
    }

    @McpTool(name = "approve", description = "Approve the current state of a topic. If the topic is at a "
            + "BREAKPOINT, this releases it and advances to the guarded phase. "
            + "Use this as a human operator to control workflow progression.")
    public String approve(
            @McpArg(description = "Topic name") String topicName) {
        var newStatus = workflowEngine.approve(topicName);
        return toJson(Map.of(
                "status", "approved",
                "topic", topicName,
                "newTopicStatus", newStatus.name()
        ));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"JSON serialization failed: " + e.getMessage() + "\"}";
        }
    }
}
