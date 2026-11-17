package com.agrigenius.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A farmer's chat conversation. Always accessed through owner-scoped queries. */
@Entity
@Table(name = "chat_sessions")
public class ChatSession {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "title", length = 120)
    private String title;

    @Column(name = "language", nullable = false, length = 5)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ChatSessionStatus status;

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ChatSession() {
        // for JPA
    }

    public ChatSession(UUID userId, String language, String title, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId);
        this.language = Objects.requireNonNull(language);
        this.title = title;
        this.status = ChatSessionStatus.ACTIVE;
        this.messageCount = 0;
        this.createdAt = now;
    }

    /** Records {@code messages} new messages (a question and its answer, or a failed attempt). */
    public void recordMessages(int messages, Instant at) {
        this.messageCount += messages;
        this.lastMessageAt = at;
    }

    public boolean isActive() {
        return status == ChatSessionStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getLanguage() {
        return language;
    }

    public ChatSessionStatus getStatus() {
        return status;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    @Override
    public String toString() {
        return "ChatSession[id=" + id + ", status=" + status + ", messageCount=" + messageCount + "]";
    }
}
