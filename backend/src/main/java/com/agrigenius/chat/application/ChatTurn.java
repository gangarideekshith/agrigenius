package com.agrigenius.chat.application;

import java.util.List;
import java.util.UUID;

/**
 * Input for one assistant turn. {@code userId} comes only from the verified JWT and is what tools
 * use to scope data; {@code history} is the session's recent messages, oldest first.
 */
public record ChatTurn(UUID userId, UUID sessionId, String language, String userMessage, List<HistoryEntry> history) {

    /** {@code role} is USER or ASSISTANT. */
    public record HistoryEntry(String role, String content) {
    }
}
