package com.agrigenius.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeChunkerTest {

    private final KnowledgeChunker chunker = new KnowledgeChunker();

    private static String words(String prefix, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(prefix).append(i).append(i % 12 == 11 ? ". " : " ");
        }
        return sb.toString();
    }

    @Test
    void markdownChunksKeepTheirHeadingPath() {
        String md = "# Rice\n\nIntro text about rice.\n\n## Pests\n\nStem borer text.\n\n### Scouting\n\nScout weekly.\n";

        List<String> chunks = chunker.chunk(md, true);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).startsWith("# Rice\n\n").contains("Intro text");
        assertThat(chunks.get(1)).startsWith("# Rice > ## Pests\n\n").contains("Stem borer");
        assertThat(chunks.get(2)).startsWith("# Rice > ## Pests > ### Scouting\n\n").contains("Scout weekly");
    }

    @Test
    void siblingHeadingsReplaceEachOther() {
        List<KnowledgeChunker.Section> sections = KnowledgeChunker.sections("# A\n\nx\n## B\ny\n## C\nz\n# D\nw\n");

        assertThat(sections).extracting(KnowledgeChunker.Section::heading)
                .containsExactly("", "# A", "# A > ## B", "# A > ## C", "# D");
    }

    @Test
    void longSectionsAreSplitWithOverlapFromThePreviousChunk() {
        String md = "# Long\n\n" + words("word", 1200);

        List<String> chunks = chunker.chunk(md, true);

        assertThat(chunks).hasSizeGreaterThan(1);
        for (int i = 1; i < chunks.size(); i++) {
            String previousBody = chunks.get(i - 1).substring("# Long\n\n".length());
            String tail = previousBody.substring(previousBody.length() - 20).strip();
            assertThat(chunks.get(i)).startsWith("# Long\n\n").contains(tail.substring(tail.indexOf(' ') + 1));
        }
    }

    @Test
    void overlapIsAboutFifteenPercentAndStartsAtAWord() {
        String text = words("token", 200).strip();

        String tail = KnowledgeChunker.overlapTail(text);

        assertThat(tail.length()).isBetween((int) (text.length() * 0.12), (int) (text.length() * 0.16));
        assertThat(text).endsWith(tail);
        assertThat(tail).startsWith("token");
    }

    @Test
    void plainTextHasNoHeadingAndEmptyInputHasNoChunks() {
        assertThat(chunker.chunk("Just one plain paragraph about soil.", false))
                .containsExactly("Just one plain paragraph about soil.");
        assertThat(chunker.chunk("   \n\n", false)).isEmpty();
        assertThat(chunker.chunk("# Only a heading\n", true)).isEmpty();
    }
}
