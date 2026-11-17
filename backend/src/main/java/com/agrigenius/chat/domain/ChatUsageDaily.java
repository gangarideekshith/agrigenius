package com.agrigenius.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Per-farmer, per-UTC-day chat usage. Written only through the atomic upsert in the repository. */
@Entity
@Table(name = "chat_usage_daily")
public class ChatUsageDaily {

    @EmbeddedId
    private ChatUsageDailyId id;

    @Column(name = "requests", nullable = false)
    private int requests;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    protected ChatUsageDaily() {
        // for JPA
    }

    public ChatUsageDailyId getId() {
        return id;
    }

    public int getRequests() {
        return requests;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }
}
