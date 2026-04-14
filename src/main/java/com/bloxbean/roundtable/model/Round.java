package com.bloxbean.roundtable.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rounds",
        uniqueConstraints = @UniqueConstraint(columnNames = {"topic_id", "round_number"}))
public class Round {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    private Long versionMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoundStatus status = RoundStatus.ACTIVE;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant deadlineAt;

    @OneToMany(mappedBy = "round", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoundReviewer> reviewers = new ArrayList<>();

    @PrePersist
    void onCreate() {
        this.startedAt = Instant.now();
    }

    public Round() {}

    public Round(Topic topic, int roundNumber, Long versionMessageId, Instant deadlineAt) {
        this.topic = topic;
        this.roundNumber = roundNumber;
        this.versionMessageId = versionMessageId;
        this.deadlineAt = deadlineAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Topic getTopic() { return topic; }
    public void setTopic(Topic topic) { this.topic = topic; }

    public int getRoundNumber() { return roundNumber; }
    public void setRoundNumber(int roundNumber) { this.roundNumber = roundNumber; }

    public Long getVersionMessageId() { return versionMessageId; }
    public void setVersionMessageId(Long versionMessageId) { this.versionMessageId = versionMessageId; }

    public RoundStatus getStatus() { return status; }
    public void setStatus(RoundStatus status) { this.status = status; }

    public Instant getStartedAt() { return startedAt; }
    public Instant getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(Instant deadlineAt) { this.deadlineAt = deadlineAt; }

    public List<RoundReviewer> getReviewers() { return reviewers; }
}
