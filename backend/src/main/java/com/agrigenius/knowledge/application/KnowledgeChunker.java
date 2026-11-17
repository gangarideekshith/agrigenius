package com.agrigenius.knowledge.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

/**
 * Splits a Markdown or plain-text document into retrieval chunks of about {@link #TARGET_TOKENS} tokens.
 *
 * <ul>
 *   <li>Markdown is split into sections at headings first; each chunk starts with its heading path
 *       (for example {@code "# Rice > ## Pests"}) so it stays meaningful on its own.</li>
 *   <li>Spring AI's {@link TokenTextSplitter} has no overlap, so each chunk after the first in a section
 *       is prefixed with roughly the last {@link #OVERLAP_RATIO} of the previous chunk, cut at a word
 *       boundary.</li>
 * </ul>
 */
public class KnowledgeChunker {

    static final int TARGET_TOKENS = 400;
    static final double OVERLAP_RATIO = 0.15;

    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(TARGET_TOKENS)
            .withMinChunkSizeChars(200)
            .withMinChunkLengthToEmbed(5)
            .withKeepSeparator(true)
            .build();

    public List<String> chunk(String text, boolean markdown) {
        List<String> chunks = new ArrayList<>();
        for (Section section : markdown ? sections(text) : List.of(new Section("", text))) {
            String body = section.body().strip();
            if (body.isEmpty()) {
                continue;
            }
            String previous = null;
            for (Document piece : splitter.split(new Document(body))) {
                String content = piece.getText().strip();
                if (content.isEmpty()) {
                    continue;
                }
                StringBuilder chunk = new StringBuilder();
                if (!section.heading().isEmpty()) {
                    chunk.append(section.heading()).append("\n\n");
                }
                if (previous != null) {
                    chunk.append(overlapTail(previous)).append(' ');
                }
                chunk.append(content);
                chunks.add(chunk.toString());
                previous = content;
            }
        }
        return chunks;
    }

    /** About the last 15% of {@code text}, starting at a word boundary. */
    static String overlapTail(String text) {
        int length = (int) Math.ceil(text.length() * OVERLAP_RATIO);
        if (length <= 0 || length >= text.length()) {
            return text;
        }
        int start = text.length() - length;
        int space = text.indexOf(' ', start);
        return (space < 0 ? text.substring(start) : text.substring(space + 1)).strip();
    }

    record Section(String heading, String body) {
    }

    /** Splits Markdown at ATX headings, tracking the heading path ("# A > ## B"). */
    static List<Section> sections(String markdown) {
        List<Section> sections = new ArrayList<>();
        List<String> path = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        String heading = "";
        for (String line : markdown.split("\\R", -1)) {
            int level = headingLevel(line);
            if (level > 0) {
                sections.add(new Section(heading, body.toString()));
                body.setLength(0);
                while (path.size() >= level) {
                    path.removeLast();
                }
                while (path.size() < level - 1) {
                    path.add("");
                }
                path.add(line.strip());
                heading = String.join(" > ", path.stream().filter(p -> !p.isEmpty()).toList());
            } else {
                body.append(line).append('\n');
            }
        }
        sections.add(new Section(heading, body.toString()));
        return sections;
    }

    private static int headingLevel(String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        return level >= 1 && level <= 6 && line.length() > level && line.charAt(level) == ' ' ? level : 0;
    }
}
