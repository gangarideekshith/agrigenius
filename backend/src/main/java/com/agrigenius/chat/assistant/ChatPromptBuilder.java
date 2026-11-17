package com.agrigenius.chat.assistant;

import com.agrigenius.chat.application.ChatTurn;
import com.agrigenius.knowledge.application.RetrievedChunk;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.ClassPathResource;

/**
 * Builds the message list for one turn (ADR-0007).
 *
 * <p>The system prompt is fixed text: only the session language, the tool list and the output format
 * are substituted, never anything the farmer typed. Retrieved sources and the question go into the
 * final user message inside XML-escaped, clearly labelled blocks. All messages are passed to Spring AI
 * as {@link Message} objects, so no text goes through a template engine (braces in farmer text or
 * sources cannot break or alter the request).
 */
final class ChatPromptBuilder {

    static final Map<String, String> LANGUAGE_NAMES = Map.of(
            "en", "English",
            "hi", "Hindi (हिन्दी)",
            "te", "Telugu (తెలుగు)",
            "kn", "Kannada (ಕನ್ನಡ)",
            "ta", "Tamil (தமிழ்)");

    private final String systemTemplate;

    ChatPromptBuilder(String systemTemplate) {
        this.systemTemplate = systemTemplate;
    }

    static ChatPromptBuilder fromClasspath() {
        try (InputStream in = new ClassPathResource("prompts/chat-system.st").getInputStream()) {
            return new ChatPromptBuilder(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new UncheckedIOException("Missing prompts/chat-system.st", ex);
        }
    }

    String systemPrompt(String language, List<String> toolNames, String outputFormat) {
        return systemTemplate
                .replace("<<LANGUAGE>>", LANGUAGE_NAMES.getOrDefault(language, "English"))
                .replace("<<TOOLS>>", String.join(", ", toolNames))
                .replace("<<FORMAT>>", outputFormat);
    }

    /** Sources are numbered S1..Sn in retrieval order; the same ids are used to validate citations. */
    String userBlock(String question, List<RetrievedChunk> sources) {
        StringBuilder sb = new StringBuilder("<retrieved_sources>\n");
        for (int i = 0; i < sources.size(); i++) {
            RetrievedChunk s = sources.get(i);
            sb.append("<source id=\"S").append(i + 1)
                    .append("\" title=\"").append(escape(s.title()))
                    .append("\" publisher=\"").append(escape(s.publisher()))
                    .append("\" licence=\"").append(escape(s.licence()))
                    .append("\">")
                    .append(escape(s.content()))
                    .append("</source>\n");
        }
        if (sources.isEmpty()) {
            sb.append("(no verified sources were found for this question)\n");
        }
        sb.append("</retrieved_sources>\n\n<farmer_question>")
                .append(escape(question))
                .append("</farmer_question>");
        return sb.toString();
    }

    List<Message> messages(ChatTurn turn, List<RetrievedChunk> sources, List<String> toolNames, String outputFormat) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt(turn.language(), toolNames, outputFormat)));
        if (turn.history() != null) {
            for (ChatTurn.HistoryEntry h : turn.history()) {
                if (h == null || h.content() == null) {
                    continue;
                }
                messages.add("ASSISTANT".equals(h.role()) ? new AssistantMessage(h.content()) : new UserMessage(h.content()));
            }
        }
        messages.add(new UserMessage(userBlock(turn.userMessage(), sources)));
        return messages;
    }

    static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
