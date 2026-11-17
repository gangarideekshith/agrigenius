package com.agrigenius.chat.application;

import com.agrigenius.chat.domain.ChatMessage;

/** Outcome of one successful send: the stored question, the stored answer and the remaining daily allowance. */
public record SendResult(ChatMessage userMessage, ChatMessage assistantMessage, String language,
        int remainingMessagesToday) {
}
