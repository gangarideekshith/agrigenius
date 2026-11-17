package com.agrigenius.knowledge.application;

import com.agrigenius.knowledge.domain.KnowledgeDocument;
import com.agrigenius.knowledge.domain.KnowledgeDocument.Status;
import com.agrigenius.knowledge.infrastructure.KnowledgeDocumentRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ingests the curated knowledge folder into {@code knowledge_documents} and the pgvector store.
 *
 * <p>The manifest is validated as a whole first; any problem rejects the run before anything changes.
 * Then, per document and in its own transaction (the vector store writes through the same DataSource):
 * same SHA-256 already ACTIVE = unchanged; changed file for an ACTIVE url = replace its chunks;
 * previously RETIRED file = reactivate; otherwise add. ACTIVE documents missing from the manifest are
 * RETIRED and their chunks deleted. Document text is never logged.
 */
@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);

    private final KnowledgeDocumentRepository documents;
    private final ObjectProvider<VectorStore> vectorStore;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final KnowledgeManifestLoader manifestLoader;
    private final KnowledgeChunker chunker = new KnowledgeChunker();

    public KnowledgeIngestionService(KnowledgeDocumentRepository documents, ObjectProvider<VectorStore> vectorStore,
            JdbcTemplate jdbc, TransactionTemplate transactions, Clock clock,
            @Value("${agrigenius.knowledge.allow-non-commercial-licences:true}") boolean allowNonCommercial,
            @Value("${agrigenius.knowledge.allow-synthetic:false}") boolean allowSynthetic) {
        this.documents = documents;
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
        this.manifestLoader = new KnowledgeManifestLoader(allowNonCommercial, allowSynthetic);
    }

    public record IngestionReport(int added, int replaced, int reactivated, int unchanged, int retired, int chunks) {
    }

    public IngestionReport ingest(Path sourceDir) {
        VectorStore store = vectorStore.getIfAvailable();
        if (store == null) {
            throw new IllegalStateException("Ingestion needs embeddings and the vector store: set "
                    + "EMBEDDING_PROVIDER=e5-onnx and VECTOR_STORE_TYPE=pgvector");
        }
        Set<String> crops = new HashSet<>(jdbc.queryForList("select code from crops", String.class));
        List<ManifestEntry> entries = manifestLoader.load(sourceDir, crops);

        int added = 0;
        int replaced = 0;
        int reactivated = 0;
        int unchanged = 0;
        int chunks = 0;
        Set<String> manifestUrls = new HashSet<>();
        for (ManifestEntry entry : entries) {
            manifestUrls.add(entry.url());
            Optional<KnowledgeDocument> sameFile = documents.findBySha256(entry.sha256());
            if (sameFile.isPresent() && sameFile.get().getStatus() == Status.ACTIVE) {
                unchanged++;
                continue;
            }
            Outcome outcome = transactions.execute(tx -> upsert(store, entry, sameFile));
            chunks += outcome.chunks();
            switch (outcome.kind()) {
                case ADDED -> added++;
                case REPLACED -> replaced++;
                case REACTIVATED -> reactivated++;
            }
        }
        int retired = 0;
        for (KnowledgeDocument active : documents.findByStatus(Status.ACTIVE)) {
            if (!manifestUrls.contains(active.getSourceUrl())) {
                transactions.executeWithoutResult(tx -> {
                    deleteChunks(store, active.getId());
                    KnowledgeDocument doc = documents.findById(active.getId()).orElseThrow();
                    doc.retire();
                    documents.save(doc);
                });
                retired++;
            }
        }
        IngestionReport report = new IngestionReport(added, replaced, reactivated, unchanged, retired, chunks);
        log.info("Knowledge ingestion finished: {}", report);
        return report;
    }

    private enum Kind { ADDED, REPLACED, REACTIVATED }

    private record Outcome(Kind kind, int chunks) {
    }

    private Outcome upsert(VectorStore store, ManifestEntry entry, Optional<KnowledgeDocument> sameFile) {
        Kind kind;
        KnowledgeDocument doc;
        if (sameFile.isPresent()) {
            doc = sameFile.get();
            kind = Kind.REACTIVATED;
            // the url may now be owned by another ACTIVE document; retire that one first
            documents.findFirstBySourceUrlAndStatus(entry.url(), Status.ACTIVE)
                    .filter(other -> !other.getId().equals(doc.getId()))
                    .ifPresent(other -> retire(store, other));
        } else {
            Optional<KnowledgeDocument> sameUrl = documents.findFirstBySourceUrlAndStatus(entry.url(), Status.ACTIVE);
            if (sameUrl.isPresent()) {
                doc = sameUrl.get();
                kind = Kind.REPLACED;
            } else {
                doc = new KnowledgeDocument(UUID.randomUUID());
                kind = Kind.ADDED;
            }
        }
        deleteChunks(store, doc.getId());
        doc.describe(entry.title(), entry.publisher(), entry.url(), entry.licence(), entry.language(),
                entry.crops(), entry.topics(), entry.dataLabel(), entry.sha256());

        List<String> texts = chunker.chunk(read(entry.file()), entry.file().toString().toLowerCase().endsWith(".md"));
        List<Document> chunkDocs = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            chunkDocs.add(new Document(chunkId(doc.getId(), i).toString(), texts.get(i), metadata(doc.getId(), i, entry)));
        }
        if (!chunkDocs.isEmpty()) {
            store.add(chunkDocs);
        }
        doc.ingested(chunkDocs.size(), clock.instant().truncatedTo(ChronoUnit.MICROS));
        documents.save(doc);
        log.info("Knowledge document {} {} ({} chunks)", doc.getId(), kind, chunkDocs.size());
        return new Outcome(kind, chunkDocs.size());
    }

    private void retire(VectorStore store, KnowledgeDocument doc) {
        deleteChunks(store, doc.getId());
        doc.retire();
        documents.save(doc);
    }

    private static void deleteChunks(VectorStore store, UUID documentId) {
        store.delete(ChunkMetadata.DOCUMENT_ID + " == '" + documentId + "'");
    }

    static UUID chunkId(UUID documentId, int index) {
        return UUID.nameUUIDFromBytes((documentId + ":" + index).getBytes(StandardCharsets.UTF_8));
    }

    static Map<String, Object> metadata(UUID documentId, int index, ManifestEntry entry) {
        Map<String, Object> m = new HashMap<>();
        m.put(ChunkMetadata.DOCUMENT_ID, documentId.toString());
        m.put(ChunkMetadata.CHUNK_INDEX, index);
        m.put(ChunkMetadata.LANGUAGE, entry.language());
        m.put(ChunkMetadata.CROPS, entry.crops());
        m.put(ChunkMetadata.TOPICS, entry.topics());
        m.put(ChunkMetadata.LICENCE, entry.licence());
        m.put(ChunkMetadata.TITLE, entry.title());
        m.put(ChunkMetadata.URL, entry.url());
        m.put(ChunkMetadata.PUBLISHER, entry.publisher());
        m.put(ChunkMetadata.DATA_LABEL, entry.dataLabel().name());
        return m;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read knowledge file " + file.getFileName(), e);
        }
    }
}
