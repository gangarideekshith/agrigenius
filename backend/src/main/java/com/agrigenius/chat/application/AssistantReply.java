package com.agrigenius.chat.application;

import java.util.List;

/** The assistant's answer plus provenance and usage, ready to be stored as an ASSISTANT message. */
public record AssistantReply(
        ReplyStatus status,
        String content,
        List<SourceRef> sources,
        List<String> toolsUsed,
        String uncertainty,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Integer cacheReadTokens) {

    public enum ReplyStatus { OK, REFUSED, NO_SOURCES }

    public record SourceRef(String title, String publisher, String url, String licence) {
    }
}
