package com.bloxbean.roundtable.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "round_reviewers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"round_id", "agent_name"}))
public class RoundReviewer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private Round round;

    @Column(name = "agent_name", nullable = false)
    private String agentName;

    private String verdict;

    private Long reviewMessageId;

    private Instant respondedAt;

    public RoundReviewer() {}

    public RoundReviewer(Round round, String agentName) {
        this.round = round;
        this.agentName = agentName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Round getRound() { return round; }
    public void setRound(Round round) { this.round = round; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public Long getReviewMessageId() { return reviewMessageId; }
    public void setReviewMessageId(Long reviewMessageId) { this.reviewMessageId = reviewMessageId; }

    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }
}
