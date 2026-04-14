package com.bloxbean.roundtable.service;

import com.bloxbean.roundtable.model.AgentRegistration;
import com.bloxbean.roundtable.model.Topic;
import com.bloxbean.roundtable.model.TopicStatus;
import com.bloxbean.roundtable.repository.AgentRegistrationRepository;
import com.bloxbean.roundtable.repository.TopicRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class AgentService {

    private static final String PLANNER_ROLE = "planner";

    private final AgentRegistrationRepository agentRepo;
    private final TopicRepository topicRepo;
    private final TopicService topicService;

    public AgentService(AgentRegistrationRepository agentRepo, TopicRepository topicRepo,
                        TopicService topicService) {
        this.agentRepo = agentRepo;
        this.topicRepo = topicRepo;
        this.topicService = topicService;
    }

    public AgentRegistration registerAgent(String topicName, String agentName, String role) {
        var topic = topicService.getByName(topicName);

        if (topic.getStatus() == TopicStatus.COMPLETED) {
            throw new IllegalStateException("TOPIC_COMPLETED: Topic '" + topicName + "' is completed");
        }

        // Check if already registered
        var existing = agentRepo.findByTopicAndName(topic, agentName);
        if (existing.isPresent() && existing.get().isActive()) {
            throw new IllegalArgumentException("Agent '" + agentName + "' is already registered on topic '" + topicName + "'");
        }

        // Exclusive planner enforcement
        if (PLANNER_ROLE.equalsIgnoreCase(role)) {
            var existingPlanner = agentRepo.findByTopicAndRoleAndActiveTrue(topic, PLANNER_ROLE);
            if (!existingPlanner.isEmpty()) {
                throw new IllegalStateException("PLANNER_ALREADY_ASSIGNED: Topic already has an active planner: "
                        + existingPlanner.getFirst().getName());
            }
            topic.setLockedByAgent(agentName);
            topicRepo.save(topic);
        }

        // Determine joinedAtRound: mid-round reviewers join next round
        int joinedAtRound = topic.getCurrentRound();
        if (!PLANNER_ROLE.equalsIgnoreCase(role) && topic.getStatus() == TopicStatus.IN_REVIEW) {
            joinedAtRound = topic.getCurrentRound() + 1;
        }

        // Reactivate if previously unregistered, or create new
        AgentRegistration reg;
        if (existing.isPresent()) {
            reg = existing.get();
            reg.setActive(true);
            reg.setRole(role);
            reg.setJoinedAtRound(joinedAtRound);
            reg.setLastPolledAt(Instant.now());
        } else {
            reg = new AgentRegistration(agentName, role, topic, joinedAtRound);
        }
        reg = agentRepo.save(reg);

        // Auto-start stalled topics when a reviewer registers
        if (!PLANNER_ROLE.equalsIgnoreCase(role) && topic.getStatus() == TopicStatus.STALLED) {
            topic.setStatus(TopicStatus.IN_REVIEW);
            topicRepo.save(topic);
        }

        return reg;
    }

    public void unregisterAgent(String topicName, String agentName) {
        var topic = topicService.getByName(topicName);
        var reg = agentRepo.findByTopicAndName(topic, agentName)
                .orElseThrow(() -> new IllegalArgumentException("Agent '" + agentName + "' not found on topic '" + topicName + "'"));

        reg.setActive(false);
        reg.setAutoPilotEnabled(false);
        agentRepo.save(reg);

        // Release planner lock if it was the planner
        if (agentName.equals(topic.getLockedByAgent())) {
            topic.setLockedByAgent(null);
            topicRepo.save(topic);
        }
    }

    @Transactional(readOnly = true)
    public List<AgentRegistration> listAgents(String topicName) {
        var topic = topicService.getByName(topicName);
        return agentRepo.findByTopicAndActiveTrue(topic);
    }

    @Transactional(readOnly = true)
    public List<AgentRegistration> listTopicsForAgent(String agentName) {
        return agentRepo.findByNameAndActiveTrue(agentName);
    }

    public void setAutoPilot(String topicName, String agentName, boolean enabled) {
        var topic = topicService.getByName(topicName);
        var reg = agentRepo.findByTopicAndName(topic, agentName)
                .orElseThrow(() -> new IllegalArgumentException("Agent '" + agentName + "' not found on topic '" + topicName + "'"));
        reg.setAutoPilotEnabled(enabled);
        agentRepo.save(reg);
    }

    public void updateHeartbeat(Topic topic, String agentName) {
        agentRepo.findByTopicAndName(topic, agentName).ifPresent(reg -> {
            reg.setLastPolledAt(Instant.now());
            agentRepo.save(reg);
        });
    }
}
