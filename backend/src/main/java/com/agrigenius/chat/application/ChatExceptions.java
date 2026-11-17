package com.agrigenius.chat.application;

import com.agrigenius.common.error.ApiException;
import java.time.Duration;
import org.springframework.http.HttpStatus;

/** Chat-specific API errors. Each maps to a stable {@code code} in the problem+json response. */
public final class ChatExceptions {

    private ChatExceptions() {
    }

    /** Unknown session id, or a session that belongs to another farmer (deliberately indistinguishable). */
    public static class SessionNotFound extends ApiException {
        public SessionNotFound() {
            super(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found", "Chat session not found.");
        }
    }

    /** The farmer already has the maximum number of active sessions. */
    public static class SessionLimitReached extends ApiException {
        public SessionLimitReached(int max) {
            super(HttpStatus.CONFLICT, "CHAT_SESSION_LIMIT_REACHED", "Conflict",
                    "You can have at most " + max + " chat sessions. Delete an old session to start a new one.");
        }
    }

    /** The session has reached its message limit. */
    public static class SessionFull extends ApiException {
        public SessionFull(int max) {
            super(HttpStatus.CONFLICT, "CHAT_SESSION_FULL", "Conflict",
                    "This session has reached its limit of " + max + " messages. Please start a new session.");
        }
    }

    /** Message content is empty or too long after removing control characters. */
    public static class InvalidContent extends ApiException {
        private final String fieldMessage;

        public InvalidContent(String fieldMessage) {
            super(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid request", "Request validation failed.");
            this.fieldMessage = fieldMessage;
        }

        public String fieldMessage() {
            return fieldMessage;
        }
    }

    /** The farmer has used today's message allowance; resets at UTC midnight. */
    public static class DailyLimitReached extends ApiException {
        private final Duration retryAfter;

        public DailyLimitReached(int limit, Duration retryAfter) {
            super(HttpStatus.TOO_MANY_REQUESTS, "CHAT_DAILY_LIMIT_REACHED", "Too many requests",
                    "You have used all " + limit + " chat messages for today. Please try again tomorrow.");
            this.retryAfter = retryAfter.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(1) : retryAfter;
        }

        public Duration retryAfter() {
            return retryAfter;
        }
    }

    /** The platform-wide daily token budget is spent; chat resumes the next UTC day. */
    public static class BudgetExhausted extends ApiException {
        public BudgetExhausted() {
            super(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_BUDGET_EXHAUSTED", "Service unavailable",
                    "The chat assistant has reached today's usage limit. Please try again tomorrow.");
        }
    }
}
