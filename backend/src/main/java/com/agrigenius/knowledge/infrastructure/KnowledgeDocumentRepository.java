package com.agrigenius.knowledge.infrastructure;

import com.agrigenius.knowledge.domain.KnowledgeDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {

    Optional<KnowledgeDocument> findBySha256(String sha256);

    Optional<KnowledgeDocument> findFirstBySourceUrlAndStatus(String sourceUrl, KnowledgeDocument.Status status);

    List<KnowledgeDocument> findByStatus(KnowledgeDocument.Status status);
}
