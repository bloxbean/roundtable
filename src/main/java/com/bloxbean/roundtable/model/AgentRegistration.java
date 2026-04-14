package com.bloxbean.roundtable.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "agent_registrations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"topic_id", "name"}))
public class AgentRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(nullable = false)
    private int joinedAtRound;

    private Instant lastPolledAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean autoPilotEnabled = false;

    @Column(nullable = false, updatable = false)
    private Instant registeredAt;

    @PrePersist
    void onCreate() {
        this.registeredAt = Instant.now();
        this.lastPolledAt = this.registeredAt;
    }

    public AgentRegistration() {}

    public AgentRegistration(String name, String role, Topic topic, int joinedAtRound) {
        this.name = name;
        this.role = role;
        this.topic = topic;
        this.joinedAtRound = joinedAtRound;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Topic getTopic() { return topic; }
    public void setTopic(Topic topic) { this.topic = topic; }

    public int getJoinedAtRound() { return joinedAtRound; }
    public void setJoinedAtRound(int joinedAtRound) { this.joinedAtRound = joinedAtRound; }

    public Instant getLastPolledAt() { return lastPolledAt; }
    public void setLastPolledAt(Instant lastPolledAt) { this.lastPolledAt = lastPolledAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isAutoPilotEnabled() { return autoPilotEnabled; }
    public void setAutoPilotEnabled(boolean autoPilotEnabled) { this.autoPilotEnabled = autoPilotEnabled; }

    public Instant getRegisteredAt() { return registeredAt; }
}
