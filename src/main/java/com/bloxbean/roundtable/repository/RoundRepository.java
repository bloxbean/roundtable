package com.bloxbean.roundtable.repository;

import com.bloxbean.roundtable.model.Round;
import com.bloxbean.roundtable.model.RoundStatus;
import com.bloxbean.roundtable.model.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoundRepository extends JpaRepository<Round, Long> {
    Optional<Round> findByTopicAndRoundNumber(Topic topic, int roundNumber);
    Optional<Round> findByTopicAndStatus(Topic topic, RoundStatus status);
}
