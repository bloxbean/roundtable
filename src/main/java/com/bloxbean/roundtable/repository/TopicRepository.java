package com.bloxbean.roundtable.repository;

import com.bloxbean.roundtable.model.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {
    Optional<Topic> findByName(String name);
    boolean existsByName(String name);
}
