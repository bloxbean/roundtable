package com.bloxbean.roundtable.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "topics")
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TopicStatus status = TopicStatus.WAITING_FOR_INPUT;

    @Column(nullable = false)
    private int currentRound = 0;

    private String lockedByAgent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuorumPolicy quorumPolicy = QuorumPolicy.ALL;

    @Column(nullable = false)
    private int roundTimeoutSeconds = 300;

    @Column(nullable = false)
    private String phases = "PLANNING,IMPLEMENTATION";

    @Column(nullable = false)
    private int currentPhaseIndex = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "topic", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AgentRegistration> agents = new ArrayList<>();

    @OneToMany(mappedBy = "topic", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Message> messages = new ArrayList<>();

    @OneToMany(mappedBy = "topic", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Round> rounds = new ArrayList<>();

    @OneToMany(mappedBy = "topic", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Breakpoint> breakpoints = new ArrayList<>();

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Topic() {}

    public Topic(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TopicStatus getStatus() { return status; }
    public void setStatus(TopicStatus status) { this.status = status; }

    public int getCurrentRound() { return currentRound; }
    public void setCurrentRound(int currentRound) { this.currentRound = currentRound; }

    public String getLockedByAgent() { return lockedByAgent; }
    public void setLockedByAgent(String lockedByAgent) { this.lockedByAgent = lockedByAgent; }

    public QuorumPolicy getQuorumPolicy() { return quorumPolicy; }
    public void setQuorumPolicy(QuorumPolicy quorumPolicy) { this.quorumPolicy = quorumPolicy; }

    public int getRoundTimeoutSeconds() { return roundTimeoutSeconds; }
    public void setRoundTimeoutSeconds(int roundTimeoutSeconds) { this.roundTimeoutSeconds = roundTimeoutSeconds; }

    public String getPhases() { return phases; }
    public void setPhases(String phases) { this.phases = phases; }

    public int getCurrentPhaseIndex() { return currentPhaseIndex; }
    public void setCurrentPhaseIndex(int currentPhaseIndex) { this.currentPhaseIndex = currentPhaseIndex; }

    public String getCurrentPhase() {
        var phaseList = phases.split(",");
        if (currentPhaseIndex < phaseList.length) return phaseList[currentPhaseIndex].trim();
        return phaseList[phaseList.length - 1].trim();
    }

    public String[] getPhaseList() {
        return java.util.Arrays.stream(phases.split(",")).map(String::trim).toArray(String[]::new);
    }

    public boolean hasNextPhase() {
        return currentPhaseIndex < getPhaseList().length - 1;
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public List<AgentRegistration> getAgents() { return agents; }
    public List<Message> getMessages() { return messages; }
    public List<Round> getRounds() { return rounds; }
    public List<Breakpoint> getBreakpoints() { return breakpoints; }
}
