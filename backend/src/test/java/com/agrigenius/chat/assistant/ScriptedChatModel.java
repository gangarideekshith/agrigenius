package com.agrigenius.chat.assistant;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/** Test double: returns a scripted reply (or fails / hangs) and records the prompt it was sent. Never calls a provider. */
final class ScriptedChatModel implements ChatModel {

    final AtomicReference<Prompt> lastPrompt = new AtomicReference<>();
    private volatile String text = "";
    private volatile String finishReason = "end_turn";
    private volatile RuntimeException failure;
    private volatile Duration delay = Duration.ZERO;

    ScriptedChatModel replying(String json) {
        this.text = json;
        return this;
    }

    ScriptedChatModel finishing(String reason) {
        this.finishReason = reason;
        return this;
    }

    ScriptedChatModel failing(RuntimeException ex) {
        this.failure = ex;
        return this;
    }

    ScriptedChatModel hanging(Duration d) {
        this.delay = d;
        return this;
    }

    /**
     * Like AnthropicChatModel, expose tool-calling options: ChatClient builds each request from
     * {@code getOptions().mutate()} and attaches tools and tool context only to a tool-calling builder.
     */
    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder().build();
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return getOptions();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        lastPrompt.set(prompt);
        if (!delay.isZero()) {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted");
            }
        }
        if (failure != null) {
            throw failure;
        }
        Generation generation = new Generation(new AssistantMessage(text),
                ChatGenerationMetadata.builder().finishReason(finishReason).build());
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("claude-opus-5-5")
                .usage(new DefaultUsage(1200, 150, 1350, null, 800L, 0L))
                .build();
        return new ChatResponse(List.of(generation), metadata);
    }

    static String json(String answer, List<String> cited, boolean insufficient, boolean outOfScope, String uncertainty) {
        StringBuilder ids = new StringBuilder("[");
        for (int i = 0; i < cited.size(); i++) {
            ids.append(i == 0 ? "" : ",").append('"').append(cited.get(i)).append('"');
        }
        ids.append(']');
        return "{\"answer\":\"" + answer + "\",\"citedSourceIds\":" + ids + ",\"insufficientInformation\":" + insufficient
                + ",\"outOfScope\":" + outOfScope + ",\"uncertainty\":" + (uncertainty == null ? "null" : "\"" + uncertainty + "\"")
                + "}";
    }
}
