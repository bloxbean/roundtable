package com.bloxbean.roundtable.repository;

import com.bloxbean.roundtable.model.Round;
import com.bloxbean.roundtable.model.RoundReviewer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoundReviewerRepository extends JpaRepository<RoundReviewer, Long> {
    List<RoundReviewer> findByRound(Round round);
    Optional<RoundReviewer> findByRoundAndAgentName(Round round, String agentName);
    List<RoundReviewer> findByRoundAndVerdictIsNull(Round round);
}
