package com.agrigenius.knowledge.application;

import java.util.UUID;

/** One retrieved knowledge chunk with the provenance needed for citation. */
public record RetrievedChunk(
        UUID documentId,
        String title,
        String publisher,
        String url,
        String licence,
        String dataLabel,
        String content,
        double similarity) {
}
