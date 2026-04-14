package com.bloxbean.roundtable.controller;

import com.bloxbean.roundtable.model.Topic;
import com.bloxbean.roundtable.repository.MessageRepository;
import com.bloxbean.roundtable.repository.TopicRepository;
import com.bloxbean.roundtable.service.AgentService;
import com.bloxbean.roundtable.service.TopicService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/topics")
public class TopicController {

    private final TopicService topicService;
    private final AgentService agentService;
    private final TopicRepository topicRepo;
    private final MessageRepository messageRepo;

    public TopicController(TopicService topicService, AgentService agentService,
                           TopicRepository topicRepo, MessageRepository messageRepo) {
        this.topicService = topicService;
        this.agentService = agentService;
        this.topicRepo = topicRepo;
        this.messageRepo = messageRepo;
    }

    @GetMapping
    public java.util.List<Map<String, Object>> listTopics() {
        return topicService.listTopics().stream().map(this::toSummary).toList();
    }

    @GetMapping("/{name}")
    public ResponseEntity<Map<String, Object>> getTopic(@PathVariable String name) {
        try {
            var topic = topicService.getByName(name);
            return ResponseEntity.ok(toDetail(topic));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{name}/messages")
    public ResponseEntity<?> getMessages(@PathVariable String name,
                                         @RequestParam(required = false) Long sinceId) {
        try {
            var topic = topicService.getByName(name);
            var messages = sinceId != null && sinceId > 0
                    ? messageRepo.findByTopicAndIdGreaterThanOrderByIdAsc(topic, sinceId)
                    : messageRepo.findByTopicOrderByIdAsc(topic);
            return ResponseEntity.ok(messages.stream().map(m -> {
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
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{name}/agents")
    public ResponseEntity<?> getAgents(@PathVariable String name) {
        try {
            var agents = agentService.listAgents(name);
            return ResponseEntity.ok(agents.stream().map(a -> {
                var map = new LinkedHashMap<String, Object>();
                map.put("name", a.getName());
                map.put("role", a.getRole());
                map.put("active", a.isActive());
                map.put("autoPilotEnabled", a.isAutoPilotEnabled());
                map.put("joinedAtRound", a.getJoinedAtRound());
                map.put("lastPolledAt", a.getLastPolledAt() != null ? a.getLastPolledAt().toString() : null);
                map.put("registeredAt", a.getRegisteredAt().toString());
                return map;
            }).toList());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<?> deleteTopic(@PathVariable String name) {
        try {
            var topic = topicService.getByName(name);
            topicRepo.delete(topic);
            return ResponseEntity.ok(Map.of("status", "deleted", "topic", name));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{name}/agents/{agentName}")
    public ResponseEntity<?> unregisterAgent(@PathVariable String name, @PathVariable String agentName) {
        try {
            agentService.unregisterAgent(name, agentName);
            return ResponseEntity.ok(Map.of("status", "unregistered", "agent", agentName, "topic", name));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{name}/agents/{agentName}/autopilot")
    public ResponseEntity<?> toggleAutoPilot(@PathVariable String name, @PathVariable String agentName,
                                              @RequestParam boolean enabled) {
        try {
            agentService.setAutoPilot(name, agentName, enabled);
            return ResponseEntity.ok(Map.of("agent", agentName, "topic", name, "autoPilotEnabled", enabled));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private Map<String, Object> toSummary(Topic t) {
        var map = new LinkedHashMap<String, Object>();
        map.put("name", t.getName());
        map.put("description", t.getDescription());
        map.put("status", t.getStatus().name());
        map.put("currentPhase", t.getCurrentPhase());
        map.put("phases", t.getPhaseList());
        map.put("currentRound", t.getCurrentRound());
        map.put("quorumPolicy", t.getQuorumPolicy().name());
        map.put("agentCount", t.getAgents().stream().filter(a -> a.isActive()).count());
        map.put("messageCount", t.getMessages().size());
        map.put("createdAt", t.getCreatedAt().toString());
        return map;
    }

    private Map<String, Object> toDetail(Topic t) {
        var map = toSummary(t);
        map.put("lockedByAgent", t.getLockedByAgent());
        map.put("phaseProgress", (t.getCurrentPhaseIndex() + 1) + " of " + t.getPhaseList().length);
        map.put("roundTimeoutSeconds", t.getRoundTimeoutSeconds());
        return map;
    }
}
