package com.agrigenius.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One chat message. {@code toString()} never includes the content: message text must not reach logs.
 * {@code sources} and {@code toolsUsed} are stored as JSON arrays (JSONB).
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10, updatable = false)
    private ChatRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private ChatMessageStatus status;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "language", length = 5)
    private String language;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sources", nullable = false)
    private String sourcesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tools_used", nullable = false)
    private String toolsUsedJson;

    @Column(name = "uncertainty", length = 500)
    private String uncertainty;

    @Column(name = "model", length = 60)
    private String model;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "cache_read_tokens")
    private Integer cacheReadTokens;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatMessage() {
        // for JPA
    }

    private ChatMessage(UUID sessionId, UUID userId, ChatRole role, ChatMessageStatus status, String content,
            String language, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.sessionId = Objects.requireNonNull(sessionId);
        this.userId = Objects.requireNonNull(userId);
        this.role = role;
        this.status = status;
        this.content = Objects.requireNonNull(content);
        this.language = language;
        this.sourcesJson = "[]";
        this.toolsUsedJson = "[]";
        this.createdAt = createdAt;
    }

    public static ChatMessage user(UUID sessionId, UUID userId, String content, String language, Instant at) {
        return new ChatMessage(sessionId, userId, ChatRole.USER, ChatMessageStatus.OK, content, language, at);
    }

    public static ChatMessage assistant(UUID sessionId, UUID userId, ChatMessageStatus status, String content,
            String language, Instant at) {
        return new ChatMessage(sessionId, userId, ChatRole.ASSISTANT, status, content, language, at);
    }

    /** Provenance and usage of an assistant answer. */
    public void describeAnswer(String sourcesJson, String toolsUsedJson, String uncertainty, String model,
            Integer inputTokens, Integer outputTokens, Integer cacheReadTokens, Integer latencyMs) {
        this.sourcesJson = sourcesJson == null ? "[]" : sourcesJson;
        this.toolsUsedJson = toolsUsedJson == null ? "[]" : toolsUsedJson;
        this.uncertainty = uncertainty;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cacheReadTokens = cacheReadTokens;
        this.latencyMs = latencyMs;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public ChatRole getRole() {
        return role;
    }

    public ChatMessageStatus getStatus() {
        return status;
    }

    public String getContent() {
        return content;
    }

    public String getLanguage() {
        return language;
    }

    public String getSourcesJson() {
        return sourcesJson;
    }

    public String getToolsUsedJson() {
        return toolsUsedJson;
    }

    public String getUncertainty() {
        return uncertainty;
    }

    public String getModel() {
        return model;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public Integer getCacheReadTokens() {
        return cacheReadTokens;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "ChatMessage[id=" + id + ", role=" + role + ", status=" + status + "]";
    }
}
