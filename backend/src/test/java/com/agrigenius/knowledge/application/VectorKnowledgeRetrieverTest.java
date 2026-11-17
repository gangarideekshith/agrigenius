package com.agrigenius.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.agrigenius.knowledge.config.KnowledgeProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

@ExtendWith(MockitoExtension.class)
class VectorKnowledgeRetrieverTest {

    @Mock
    VectorStore store;

    private final KnowledgeProperties properties = new KnowledgeProperties(5, 0.75, 1000, "unused");

    private static Document hit(String text, double score) {
        UUID documentId = UUID.randomUUID();
        return Document.builder()
                .id(UUID.randomUUID().toString())
                .text(text)
                .metadata(Map.of(ChunkMetadata.DOCUMENT_ID, documentId.toString(), ChunkMetadata.TITLE, "Title",
                        ChunkMetadata.PUBLISHER, "Pub", ChunkMetadata.URL, "https://example.org/x",
                        ChunkMetadata.LICENCE, "CC-BY-4.0", ChunkMetadata.DATA_LABEL, "VERIFIED_SOURCE"))
                .score(score)
                .build();
    }

    @Test
    void searchUsesQueryPrefixThresholdAndCappedTopK() {
        given(store.similaritySearch(any(SearchRequest.class))).willReturn(List.of());
        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);

        new VectorKnowledgeRetriever(store, properties).retrieve("  brown spots on paddy ", 50);

        verify(store).similaritySearch(request.capture());
        assertThat(request.getValue().getQuery()).isEqualTo("query: brown spots on paddy");
        assertThat(request.getValue().getTopK()).isEqualTo(5);
        assertThat(request.getValue().getSimilarityThreshold()).isEqualTo(0.75);
    }

    @Test
    void mapsMetadataAndOrdersBestFirst() {
        given(store.similaritySearch(any(SearchRequest.class)))
                .willReturn(List.of(hit("second", 0.80), hit("first", 0.93)));

        List<RetrievedChunk> chunks = new VectorKnowledgeRetriever(store, properties).retrieve("q", 3);

        assertThat(chunks).extracting(RetrievedChunk::content).containsExactly("first", "second");
        assertThat(chunks.getFirst()).satisfies(c -> {
            assertThat(c.title()).isEqualTo("Title");
            assertThat(c.publisher()).isEqualTo("Pub");
            assertThat(c.url()).isEqualTo("https://example.org/x");
            assertThat(c.licence()).isEqualTo("CC-BY-4.0");
            assertThat(c.dataLabel()).isEqualTo("VERIFIED_SOURCE");
            assertThat(c.similarity()).isEqualTo(0.93);
            assertThat(c.documentId()).isNotNull();
        });
    }

    @Test
    void dropsLowestRankedChunksBeyondTheContextBudget() {
        given(store.similaritySearch(any(SearchRequest.class)))
                .willReturn(List.of(hit("a".repeat(600), 0.9), hit("b".repeat(300), 0.85), hit("c".repeat(300), 0.8)));

        List<RetrievedChunk> chunks = new VectorKnowledgeRetriever(store, properties).retrieve("q", 5);

        assertThat(chunks).extracting(c -> c.content().charAt(0)).containsExactly('a', 'b');
    }

    @Test
    void blankQueryDoesNotSearch() {
        assertThat(new VectorKnowledgeRetriever(store, properties).retrieve("  ", 5)).isEmpty();
        assertThat(new VectorKnowledgeRetriever(store, properties).retrieve(null, 5)).isEmpty();
        verify(store, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void noOpRetrieverFindsNothing() {
        assertThat(new NoOpKnowledgeRetriever().retrieve("anything", 5)).isEmpty();
    }
}
