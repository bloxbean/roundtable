package com.bloxbean.roundtable.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(nullable = false)
    private String senderName;

    private String senderRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type;

    @Column(columnDefinition = "CLOB", nullable = false)
    private String content;

    @Column(nullable = false)
    private int round;

    private String phase;

    @Column(nullable = false)
    private long lastSeenMessageId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Message() {}

    public Message(Topic topic, String senderName, String senderRole,
                   MessageType type, String content, int round, String phase, long lastSeenMessageId) {
        this.topic = topic;
        this.senderName = senderName;
        this.senderRole = senderRole;
        this.type = type;
        this.content = content;
        this.round = round;
        this.phase = phase;
        this.lastSeenMessageId = lastSeenMessageId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Topic getTopic() { return topic; }
    public void setTopic(Topic topic) { this.topic = topic; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }

    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public long getLastSeenMessageId() { return lastSeenMessageId; }
    public void setLastSeenMessageId(long lastSeenMessageId) { this.lastSeenMessageId = lastSeenMessageId; }

    public Instant getCreatedAt() { return createdAt; }
}
