package com.agrigenius.knowledge.application;

import com.agrigenius.knowledge.domain.KnowledgeDocument.DataLabel;
import java.nio.file.Path;
import java.util.List;

/** One validated {@code manifest.yaml} entry; {@code file} is resolved inside the source directory. */
public record ManifestEntry(
        String title,
        String publisher,
        String url,
        String licence,
        String language,
        List<String> crops,
        List<String> topics,
        Path file,
        String sha256,
        DataLabel dataLabel) {
}
