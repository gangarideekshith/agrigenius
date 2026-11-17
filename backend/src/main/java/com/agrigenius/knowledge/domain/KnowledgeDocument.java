package com.agrigenius.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One curated source document in the knowledge base. Its chunks live in {@code knowledge_chunks}. */
@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument {

    public enum Status { ACTIVE, RETIRED }

    public enum DataLabel { VERIFIED_SOURCE, SYNTHETIC_TEST_DATA }

    @Id
    private UUID id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 200)
    private String publisher;

    @Column(name = "source_url", nullable = false, length = 500)
    private String sourceUrl;

    @Column(nullable = false, length = 60)
    private String licence;

    @Column(nullable = false, length = 5)
    private String language;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> crops;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> topics;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_label", nullable = false, length = 30)
    private DataLabel dataLabel;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    @Version
    private long version;

    protected KnowledgeDocument() {
    }

    public KnowledgeDocument(UUID id) {
        this.id = id;
        this.status = Status.ACTIVE;
    }

    /** Copies the manifest description of the document and marks it ACTIVE. */
    public void describe(String title, String publisher, String sourceUrl, String licence, String language,
            List<String> crops, List<String> topics, DataLabel dataLabel, String sha256) {
        this.title = title;
        this.publisher = publisher;
        this.sourceUrl = sourceUrl;
        this.licence = licence;
        this.language = language;
        this.crops = List.copyOf(crops);
        this.topics = List.copyOf(topics);
        this.dataLabel = dataLabel;
        this.sha256 = sha256;
        this.status = Status.ACTIVE;
    }

    public void ingested(int chunkCount, Instant at) {
        this.chunkCount = chunkCount;
        this.ingestedAt = at;
    }

    public void retire() {
        this.status = Status.RETIRED;
        this.chunkCount = 0;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getPublisher() {
        return publisher;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getLicence() {
        return licence;
    }

    public String getLanguage() {
        return language;
    }

    public List<String> getCrops() {
        return crops;
    }

    public List<String> getTopics() {
        return topics;
    }

    public DataLabel getDataLabel() {
        return dataLabel;
    }

    public String getSha256() {
        return sha256;
    }

    public Status getStatus() {
        return status;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }
}
