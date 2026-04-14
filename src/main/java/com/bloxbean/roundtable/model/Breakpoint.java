package com.bloxbean.roundtable.model;

import jakarta.persistence.*;

@Entity
@Table(name = "breakpoints",
        uniqueConstraints = @UniqueConstraint(columnNames = {"topic_id", "at_phase"}))
public class Breakpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "at_phase", nullable = false)
    private TopicStatus atPhase;

    @Column(nullable = false)
    private boolean triggered = false;

    public Breakpoint() {}

    public Breakpoint(Topic topic, TopicStatus atPhase) {
        this.topic = topic;
        this.atPhase = atPhase;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Topic getTopic() { return topic; }
    public void setTopic(Topic topic) { this.topic = topic; }

    public TopicStatus getAtPhase() { return atPhase; }
    public void setAtPhase(TopicStatus atPhase) { this.atPhase = atPhase; }

    public boolean isTriggered() { return triggered; }
    public void setTriggered(boolean triggered) { this.triggered = triggered; }
}
