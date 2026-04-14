package com.bloxbean.roundtable.repository;

import com.bloxbean.roundtable.model.AgentRegistration;
import com.bloxbean.roundtable.model.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentRegistrationRepository extends JpaRepository<AgentRegistration, Long> {
    List<AgentRegistration> findByTopicAndActiveTrue(Topic topic);
    Optional<AgentRegistration> findByTopicAndName(Topic topic, String name);
    List<AgentRegistration> findByTopicAndRoleAndActiveTrue(Topic topic, String role);
    boolean existsByTopicAndNameAndActiveTrue(Topic topic, String name);
    List<AgentRegistration> findByNameAndActiveTrue(String name);
}
