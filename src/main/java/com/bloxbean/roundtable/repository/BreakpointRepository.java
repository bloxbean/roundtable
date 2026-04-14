package com.bloxbean.roundtable.repository;

import com.bloxbean.roundtable.model.Breakpoint;
import com.bloxbean.roundtable.model.Topic;
import com.bloxbean.roundtable.model.TopicStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BreakpointRepository extends JpaRepository<Breakpoint, Long> {
    Optional<Breakpoint> findByTopicAndAtPhase(Topic topic, TopicStatus atPhase);
}
