package com.agrigenius.knowledge.application;

/** Metadata keys stored with every knowledge chunk in {@code knowledge_chunks.metadata}. */
public final class ChunkMetadata {

    public static final String DOCUMENT_ID = "documentId";
    public static final String CHUNK_INDEX = "chunkIndex";
    public static final String LANGUAGE = "language";
    public static final String CROPS = "crops";
    public static final String TOPICS = "topics";
    public static final String LICENCE = "licence";
    public static final String TITLE = "title";
    public static final String URL = "url";
    public static final String PUBLISHER = "publisher";
    public static final String DATA_LABEL = "dataLabel";

    private ChunkMetadata() {
    }
}
