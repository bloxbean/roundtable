package com.bloxbean.roundtable.mcp;

import com.bloxbean.roundtable.model.*;
import com.bloxbean.roundtable.service.AgentService;
import com.bloxbean.roundtable.service.MessageService;
import com.bloxbean.roundtable.service.TopicService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AutoPilotTool {

    private final TopicService topicService;
    private final AgentService agentService;
    private final MessageService messageService;

    public AutoPilotTool(TopicService topicService, AgentService agentService,
                         MessageService messageService) {
        this.topicService = topicService;
        this.agentService = agentService;
        this.messageService = messageService;
    }

    @McpTool(name = "start_auto_pilot", description = "Enable auto-pilot for an agent on a topic. "
            + "After calling this, use auto_pilot repeatedly to get instructions. "
            + "The agent must already be registered on the topic.")
    public String startAutoPilot(
            @McpArg(description = "Topic name") String topicName,
            @McpArg(description = "Your agent name") String agentName) {
        agentService.setAutoPilot(topicName, agentName, true);
        return "Auto-pilot ENABLED for '" + agentName + "' on topic '" + topicName + "'. "
                + "Now call auto_pilot(topicName, agentName, 0) and follow the instructions it returns. "
                + "Keep calling auto_pilot after each action until it says STOP or DONE.";
    }

    @McpTool(name = "stop_auto_pilot", description = "Disable auto-pilot for an agent. "
            + "The next auto_pilot call will tell the agent to stop looping. "
            + "The agent remains registered on the topic and can still use other tools manually.")
    public String stopAutoPilot(
            @McpArg(description = "Topic name") String topicName,
            @McpArg(description = "Agent name to stop") String agentName) {
        agentService.setAutoPilot(topicName, agentName, false);
        return "Auto-pilot DISABLED for '" + agentName + "' on topic '" + topicName + "'. "
                + "The agent will stop on its next auto_pilot call. "
                + "The agent is still registered and can use tools manually.";
    }

    @McpTool(name = "auto_pilot", description = "Call this tool repeatedly to participate in a topic automatically. "
            + "It returns the current state, any unread messages, and explicit instructions on what to do next. "
            + "Follow the instructions, take the recommended action, then call auto_pilot again. "
            + "Continue until the instructions say DONE.")
    public String autoPilot(
            @McpArg(description = "Topic name") String topicName,
            @McpArg(description = "Your agent name (must be registered)") String agentName,
            @McpArg(description = "ID of the last message you processed. Pass 0 on first call.", required = false) Long lastSeenMessageId) {

        long sinceId = lastSeenMessageId != null ? lastSeenMessageId : 0;

        var topic = topicService.getByName(topicName);
        var agents = agentService.listAgents(topicName);
        var agent = agents.stream()
                .filter(a -> a.getName().equals(agentName) && a.isActive())
                .findFirst()
                .orElse(null);

        if (agent == null) {
            return "STOP. Agent '" + agentName + "' is not registered on topic '" + topicName + "'. "
                    + "Do NOT call auto_pilot again. Call register_agent first if you want to join.";
        }

        if (!agent.isAutoPilotEnabled()) {
            return "STOP. Auto-pilot has been disabled for agent '" + agentName + "'. "
                    + "Do NOT call auto_pilot again. "
                    + "To re-enable, call start_auto_pilot(topicName, agentName).";
        }

        var newMessages = messageService.getMessages(topicName, agentName, sinceId);
        long headId = newMessages.isEmpty() ? sinceId :
                newMessages.stream().mapToLong(Message::getId).max().orElse(sinceId);

        var sb = new StringBuilder();
        sb.append("== AUTO-PILOT STATUS ==\n");
        sb.append("Topic: ").append(topicName).append("\n");
        sb.append("Your role: ").append(agent.getRole()).append("\n");
        sb.append("Current phase: ").append(topic.getCurrentPhase())
          .append(" (").append(topic.getCurrentPhaseIndex() + 1).append(" of ").append(topic.getPhaseList().length).append(")\n");
        sb.append("Topic status: ").append(topic.getStatus()).append("\n");
        sb.append("Current round: ").append(topic.getCurrentRound()).append("\n");
        sb.append("Your lastSeenMessageId: ").append(headId).append("\n\n");

        // Show unread messages
        if (!newMessages.isEmpty()) {
            sb.append("== NEW MESSAGES (").append(newMessages.size()).append(") ==\n");
            for (var msg : newMessages) {
                sb.append("[#").append(msg.getId()).append("] ")
                  .append(msg.getSenderName()).append(" (").append(msg.getSenderRole()).append(") ")
                  .append("type=").append(msg.getType()).append(":\n");
                sb.append(msg.getContent()).append("\n\n");
            }
        } else {
            sb.append("== NO NEW MESSAGES ==\n\n");
        }

        // Generate role-specific instructions based on state
        sb.append("== YOUR NEXT ACTION ==\n");
        String role = agent.getRole().toLowerCase();
        TopicStatus status = topic.getStatus();

        if (status == TopicStatus.COMPLETED) {
            sb.append("DONE. Topic is completed. No further action needed. Stop calling auto_pilot.");
            return sb.toString();
        }

        if (status == TopicStatus.APPROVED) {
            sb.append("DONE. Topic is approved. The plan has been accepted. Stop calling auto_pilot.");
            return sb.toString();
        }

        if (status == TopicStatus.BREAKPOINT) {
            sb.append("Topic is at a BREAKPOINT. Waiting for human approval.\n");
            sb.append("ACTION: Call auto_pilot again in a moment to check if the breakpoint was released.\n");
            sb.append("Pass lastSeenMessageId=").append(headId);
            return sb.toString();
        }

        if ("planner".equals(role)) {
            sb.append(generatePlannerInstructions(topic, status, headId, newMessages));
        } else {
            // reviewer or any other role
            sb.append(generateReviewerInstructions(topic, status, headId, newMessages, agentName));
        }

        return sb.toString();
    }

    private String generatePlannerInstructions(Topic topic, TopicStatus status,
                                                long headId, List<Message> newMessages) {
        var sb = new StringBuilder();
        String phase = topic.getCurrentPhase();
        String phaseLabel = phase.toLowerCase();

        switch (status) {
            case WAITING_FOR_INPUT, STALLED -> {
                sb.append("Phase '").append(phase).append("' is waiting for your submission.\n");
                sb.append("ACTION: Call send_message with these parameters:\n");
                sb.append("  topicName: \"").append(topic.getName()).append("\"\n");
                sb.append("  senderName: <your agent name>\n");
                sb.append("  content: <write your detailed ").append(phaseLabel).append(" here>\n");
                sb.append("  type: \"PLAN\"\n");
                sb.append("  lastSeenMessageId: ").append(headId).append("\n\n");
                sb.append("After sending, call auto_pilot again with lastSeenMessageId=<the returned messageId>.");
            }
            case IN_REVIEW -> {
                sb.append("Your ").append(phaseLabel).append(" is being reviewed. Wait for reviewers.\n");
                sb.append("ACTION: Call auto_pilot again in a moment to check for review results.\n");
                sb.append("Pass lastSeenMessageId=").append(headId);
            }
            case REVISION_REQUESTED -> {
                var feedback = newMessages.stream()
                        .filter(m -> m.getType() == MessageType.REJECTION || m.getType() == MessageType.REVIEW)
                        .toList();
                if (!feedback.isEmpty()) {
                    sb.append("Reviewers have requested changes to your ").append(phaseLabel).append(". Their feedback:\n\n");
                    for (var fb : feedback) {
                        sb.append("  [").append(fb.getSenderName()).append("]: ").append(fb.getContent()).append("\n\n");
                    }
                } else {
                    sb.append("Revision requested for ").append(phaseLabel).append(". Check the messages above for feedback.\n\n");
                }
                sb.append("ACTION: Address the feedback and call send_message with:\n");
                sb.append("  topicName: \"").append(topic.getName()).append("\"\n");
                sb.append("  senderName: <your agent name>\n");
                sb.append("  content: <your revised ").append(phaseLabel).append(" addressing the feedback above>\n");
                sb.append("  type: \"REVISION\"\n");
                sb.append("  lastSeenMessageId: ").append(headId).append("\n\n");
                sb.append("After sending, call auto_pilot again with lastSeenMessageId=<the returned messageId>.");
            }
            default -> {
                sb.append("No action needed from you right now.\n");
                sb.append("ACTION: Call auto_pilot again to check status. Pass lastSeenMessageId=").append(headId);
            }
        }
        return sb.toString();
    }

    private String generateReviewerInstructions(Topic topic, TopicStatus status,
                                                 long headId, List<Message> newMessages,
                                                 String agentName) {
        var sb = new StringBuilder();
        String phase = topic.getCurrentPhase();
        String phaseLabel = phase.toLowerCase();

        switch (status) {
            case WAITING_FOR_INPUT, STALLED -> {
                sb.append("Waiting for the planner to submit their ").append(phaseLabel).append(".\n");
                sb.append("ACTION: Call auto_pilot again in a moment to check. Pass lastSeenMessageId=").append(headId);
            }
            case IN_REVIEW -> {
                var planOrRevision = newMessages.stream()
                        .filter(m -> m.getType() == MessageType.PLAN || m.getType() == MessageType.REVISION)
                        .reduce((a, b) -> b)
                        .orElse(null);

                if (planOrRevision != null) {
                    sb.append("A ").append(phaseLabel).append(" submission needs your review ")
                      .append("(message #").append(planOrRevision.getId()).append(", phase: ").append(phase).append(").\n\n");
                    sb.append("CONTENT TO REVIEW:\n");
                    sb.append("---\n");
                    sb.append(planOrRevision.getContent());
                    sb.append("\n---\n\n");
                    sb.append("ACTION: Review the ").append(phaseLabel).append(" above carefully, then call send_message with:\n");
                    sb.append("  topicName: \"").append(topic.getName()).append("\"\n");
                    sb.append("  senderName: \"").append(agentName).append("\"\n");
                    sb.append("  content: <your review comments for this ").append(phaseLabel).append(">\n");
                    sb.append("  type: \"APPROVAL\" if the ").append(phaseLabel).append(" is sound, or \"REJECTION\" with specific feedback\n");
                    sb.append("  lastSeenMessageId: ").append(headId).append("\n\n");
                    sb.append("After sending, call auto_pilot again with lastSeenMessageId=<the returned messageId>.");
                } else {
                    sb.append("Topic is IN_REVIEW but no new ").append(phaseLabel).append(" in your unread messages.\n");
                    sb.append("You may have already reviewed this round.\n");
                    sb.append("ACTION: Call auto_pilot again to check. Pass lastSeenMessageId=").append(headId);
                }
            }
            case REVISION_REQUESTED -> {
                sb.append("Waiting for the planner to revise their ").append(phaseLabel).append(".\n");
                sb.append("ACTION: Call auto_pilot again in a moment to check. Pass lastSeenMessageId=").append(headId);
            }
            default -> {
                sb.append("No action needed from you right now.\n");
                sb.append("ACTION: Call auto_pilot again to check. Pass lastSeenMessageId=").append(headId);
            }
        }
        return sb.toString();
    }
}
