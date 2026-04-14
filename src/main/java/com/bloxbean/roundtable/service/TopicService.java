package com.bloxbean.roundtable.service;

import com.bloxbean.roundtable.model.Breakpoint;
import com.bloxbean.roundtable.model.QuorumPolicy;
import com.bloxbean.roundtable.model.Topic;
import com.bloxbean.roundtable.model.TopicStatus;
import com.bloxbean.roundtable.repository.BreakpointRepository;
import com.bloxbean.roundtable.repository.TopicRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class TopicService {

    private final TopicRepository topicRepository;
    private final BreakpointRepository breakpointRepository;

    public TopicService(TopicRepository topicRepository, BreakpointRepository breakpointRepository) {
        this.topicRepository = topicRepository;
        this.breakpointRepository = breakpointRepository;
    }

    public Topic createTopic(String name, String description, QuorumPolicy quorumPolicy, int roundTimeoutSeconds) {
        return createTopic(name, description, quorumPolicy, roundTimeoutSeconds, null);
    }

    public Topic createTopic(String name, String description, QuorumPolicy quorumPolicy, int roundTimeoutSeconds, String phases) {
        if (topicRepository.existsByName(name)) {
            throw new IllegalArgumentException("Topic '" + name + "' already exists");
        }
        var topic = new Topic(name, description);
        topic.setQuorumPolicy(quorumPolicy);
        topic.setRoundTimeoutSeconds(roundTimeoutSeconds);
        if (phases != null && !phases.isBlank()) {
            topic.setPhases(phases);
        }
        return topicRepository.save(topic);
    }

    @Transactional(readOnly = true)
    public List<Topic> listTopics() {
        return topicRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Topic getByName(String name) {
        return topicRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Topic '" + name + "' not found"));
    }

    public void setBreakpoint(String topicName, TopicStatus atPhase) {
        var topic = getByName(topicName);
        if (breakpointRepository.findByTopicAndAtPhase(topic, atPhase).isPresent()) {
            throw new IllegalArgumentException("Breakpoint already set at phase " + atPhase);
        }
        breakpointRepository.save(new Breakpoint(topic, atPhase));
    }

    public void removeBreakpoint(String topicName, TopicStatus atPhase) {
        var topic = getByName(topicName);
        var bp = breakpointRepository.findByTopicAndAtPhase(topic, atPhase)
                .orElseThrow(() -> new IllegalArgumentException("No breakpoint at phase " + atPhase));
        breakpointRepository.delete(bp);
    }
}
