package com.bloxbean.roundtable.service;

import com.bloxbean.roundtable.model.*;
import com.bloxbean.roundtable.repository.AgentRegistrationRepository;
import com.bloxbean.roundtable.repository.MessageRepository;
import com.bloxbean.roundtable.repository.TopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    // Turn enforcement matrix: state -> role -> allowed message types
    private static final Map<TopicStatus, Map<String, Set<MessageType>>> ALLOWED_ACTIONS = Map.of(
            TopicStatus.WAITING_FOR_INPUT, Map.of(
                    "planner", Set.of(MessageType.PLAN, MessageType.CHAT),
                    "reviewer", Set.of(MessageType.CHAT)
            ),
            TopicStatus.IN_REVIEW, Map.of(
                    "planner", Set.of(MessageType.CHAT),
                    "reviewer", Set.of(MessageType.REVIEW, MessageType.APPROVAL, MessageType.REJECTION, MessageType.CHAT)
            ),
            TopicStatus.REVISION_REQUESTED, Map.of(
                    "planner", Set.of(MessageType.REVISION, MessageType.CHAT),
                    "reviewer", Set.of(MessageType.CHAT)
            ),
            TopicStatus.BREAKPOINT, Map.of(
                    "planner", Set.of(MessageType.CHAT),
                    "reviewer", Set.of(MessageType.CHAT)
            ),
            TopicStatus.STALLED, Map.of(
                    "planner", Set.of(MessageType.PLAN, MessageType.CHAT),
                    "reviewer", Set.of(MessageType.CHAT)
            ),
            TopicStatus.APPROVED, Map.of(
                    "planner", Set.of(MessageType.CHAT),
                    "reviewer", Set.of(MessageType.CHAT)
            )
    );

    private final MessageRepository messageRepo;
    private final TopicRepository topicRepo;
    private final AgentRegistrationRepository agentRepo;
    private final TopicService topicService;
    private final AgentService agentService;
    private final WorkflowEngine workflowEngine;

    public MessageService(MessageRepository messageRepo, TopicRepository topicRepo,
                          AgentRegistrationRepository agentRepo, TopicService topicService,
                          AgentService agentService, WorkflowEngine workflowEngine) {
        this.messageRepo = messageRepo;
        this.topicRepo = topicRepo;
        this.agentRepo = agentRepo;
        this.topicService = topicService;
        this.agentService = agentService;
        this.workflowEngine = workflowEngine;
    }

    /**
     * Send a message to a topic. This is the main entry point with full validation.
     */
    public synchronized Message sendMessage(String topicName, String senderName, String content,
                                            MessageType type, long lastSeenMessageId) {
        var topic = topicService.getByName(topicName);

        // 1. Reject messages to completed topics
        if (topic.getStatus() == TopicStatus.COMPLETED) {
            throw new IllegalStateException("TOPIC_COMPLETED: Topic '" + topicName + "' is completed, no further messages accepted");
        }

        // 2. Verify agent is registered and active
        var agent = agentRepo.findByTopicAndName(topic, senderName)
                .orElseThrow(() -> new IllegalStateException("NOT_REGISTERED: Agent '" + senderName
                        + "' is not registered on topic '" + topicName + "'"));
        if (!agent.isActive()) {
            throw new IllegalStateException("NOT_REGISTERED: Agent '" + senderName + "' is not active on topic '" + topicName + "'");
        }

        // 3. Turn enforcement
        enforceTurn(topic, agent.getRole(), type);

        // 4. Freshness check (skip for CHAT and SYSTEM messages)
        if (type != MessageType.CHAT && type != MessageType.SYSTEM) {
            checkFreshness(topic, type, lastSeenMessageId);
        }

        // 5. Persist message
        var message = new Message(topic, senderName, agent.getRole(), type, content,
                topic.getCurrentRound(), topic.getCurrentPhase(), lastSeenMessageId);
        message = messageRepo.save(message);
        log.info("Message {} sent by {} ({}) on topic '{}': type={}", message.getId(),
                senderName, agent.getRole(), topicName, type);

        // 6. Trigger workflow engine
        TopicStatus newStatus = switch (type) {
            case PLAN, REVISION -> workflowEngine.onPlanOrRevision(topic, message);
            case REVIEW, APPROVAL, REJECTION -> workflowEngine.onReviewVerdict(topic, message);
            default -> topic.getStatus();
        };

        return message;
    }

    /**
     * Get messages for polling. Updates agent heartbeat.
     */
    @Transactional(readOnly = false)
    public List<Message> getMessages(String topicName, String agentName, Long sinceId) {
        var topic = topicService.getByName(topicName);

        // Update heartbeat
        if (agentName != null) {
            agentService.updateHeartbeat(topic, agentName);
        }

        if (sinceId != null && sinceId > 0) {
            return messageRepo.findByTopicAndIdGreaterThanOrderByIdAsc(topic, sinceId);
        }
        return messageRepo.findByTopicOrderByIdAsc(topic);
    }

    /**
     * Get the latest message on a topic, optionally filtered by sender role.
     */
    @Transactional(readOnly = true)
    public Message getLatest(String topicName, String role) {
        var topic = topicService.getByName(topicName);
        if (role != null && !role.isBlank()) {
            return messageRepo.findFirstByTopicAndSenderRoleOrderByIdDesc(topic, role)
                    .orElse(null);
        }
        return messageRepo.findFirstByTopicOrderByIdDesc(topic).orElse(null);
    }

    private void enforceTurn(Topic topic, String role, MessageType type) {
        var stateRules = ALLOWED_ACTIONS.get(topic.getStatus());
        if (stateRules == null) {
            throw new IllegalStateException("INVALID_STATE_TRANSITION: No actions allowed in state " + topic.getStatus());
        }

        var allowedTypes = stateRules.get(role.toLowerCase());
        if (allowedTypes == null) {
            // Unknown role: only CHAT allowed
            if (type != MessageType.CHAT) {
                throw new IllegalStateException("INVALID_STATE_TRANSITION: Role '" + role
                        + "' cannot send " + type + " in state " + topic.getStatus()
                        + ". Only CHAT is allowed.");
            }
            return;
        }

        if (!allowedTypes.contains(type)) {
            throw new IllegalStateException("INVALID_STATE_TRANSITION: Role '" + role
                    + "' cannot send " + type + " while topic is in " + topic.getStatus()
                    + " state. Allowed types: " + allowedTypes);
        }
    }

    private void checkFreshness(Topic topic, MessageType type, long lastSeenMessageId) {
        if (type == MessageType.PLAN || type == MessageType.REVISION) {
            // Planner must have seen all review/rejection messages in the current round
            var roundMessages = messageRepo.findByTopicAndRoundAndTypeIn(topic, topic.getCurrentRound(),
                    List.of(MessageType.REVIEW, MessageType.REJECTION, MessageType.APPROVAL));
            if (!roundMessages.isEmpty()) {
                long maxId = roundMessages.stream().mapToLong(Message::getId).max().orElse(0);
                if (lastSeenMessageId < maxId) {
                    var missedIds = roundMessages.stream()
                            .filter(m -> m.getId() > lastSeenMessageId)
                            .map(m -> String.valueOf(m.getId()))
                            .toList();
                    throw new IllegalStateException("STALE_STATE: You have not seen all feedback in round "
                            + topic.getCurrentRound() + ". " + missedIds.size()
                            + " unread message(s) since your lastSeenMessageId=" + lastSeenMessageId
                            + ". Missed message IDs: [" + String.join(", ", missedIds) + "]"
                            + ". Read messages before submitting.");
                }
            }
        } else if (type == MessageType.REVIEW || type == MessageType.APPROVAL || type == MessageType.REJECTION) {
            // Reviewer must have seen the PLAN/REVISION that started the current round
            var versionMsg = messageRepo.findFirstByTopicAndRoundAndTypeInOrderByIdDesc(topic,
                    topic.getCurrentRound(), List.of(MessageType.PLAN, MessageType.REVISION));
            if (versionMsg.isPresent() && lastSeenMessageId < versionMsg.get().getId()) {
                throw new IllegalStateException("STALE_STATE: You have not seen the latest plan/revision (message "
                        + versionMsg.get().getId() + ") for round " + topic.getCurrentRound()
                        + ". Your lastSeenMessageId=" + lastSeenMessageId
                        + ". Read the latest version before reviewing.");
            }
        }
    }
}
