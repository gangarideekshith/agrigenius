package com.agrigenius.knowledge.application;

import com.agrigenius.knowledge.domain.KnowledgeDocument.DataLabel;
import com.agrigenius.knowledge.embedding.ModelFileCache;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Reads and validates {@code manifest.yaml} (format in knowledge/README.md). Validation is all-or-nothing:
 * every problem is collected and the whole manifest is rejected if there is any.
 *
 * <p>Licence policy (README allowlist): {@code PUBLIC-DOMAIN}, {@code CC-BY-4.0}, {@code CC-BY-SA-4.0},
 * {@code GODL-India}; FAO's {@code CC-BY-NC-SA-3.0-IGO} only while non-commercial use is allowed.
 * {@code SYNTHETIC-TEST-ONLY} is reserved for {@code data_label: SYNTHETIC_TEST_DATA}, which is itself
 * refused unless synthetic data is explicitly allowed (tests only).
 */
public class KnowledgeManifestLoader {

    public static final String MANIFEST_FILE = "manifest.yaml";
    public static final String SYNTHETIC_LICENCE = "SYNTHETIC-TEST-ONLY";
    static final Set<String> LICENCES = Set.of("PUBLIC-DOMAIN", "CC-BY-4.0", "CC-BY-SA-4.0", "GODL-India");
    static final String NON_COMMERCIAL_LICENCE = "CC-BY-NC-SA-3.0-IGO";
    static final Set<String> LANGUAGES = Set.of("en", "hi", "te", "kn", "ta");
    static final Set<String> EXTENSIONS = Set.of(".md", ".txt");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern TAG = Pattern.compile("[a-z0-9][a-z0-9_-]{0,39}");

    private final boolean allowNonCommercial;
    private final boolean allowSynthetic;

    public KnowledgeManifestLoader(boolean allowNonCommercial, boolean allowSynthetic) {
        this.allowNonCommercial = allowNonCommercial;
        this.allowSynthetic = allowSynthetic;
    }

    /**
     * @param sourceDir  folder containing {@code manifest.yaml} and the listed files
     * @param knownCrops crop codes from the {@code crops} table
     */
    public List<ManifestEntry> load(Path sourceDir, Set<String> knownCrops) {
        Path root = sourceDir.toAbsolutePath().normalize();
        Path manifest = root.resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifest)) {
            throw new KnowledgeManifestException(List.of("manifest not found: " + manifest));
        }
        Object parsed;
        try (Reader reader = Files.newBufferedReader(manifest)) {
            parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
        } catch (IOException | RuntimeException e) {
            throw new KnowledgeManifestException(List.of("manifest is not valid YAML: " + e.getMessage()));
        }
        if (!(parsed instanceof Map<?, ?> top) || !(top.get("documents") instanceof List<?> documents)) {
            throw new KnowledgeManifestException(List.of("manifest must have a 'documents' list"));
        }

        List<String> problems = new ArrayList<>();
        List<ManifestEntry> entries = new ArrayList<>();
        Set<String> urls = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        for (int i = 0; i < documents.size(); i++) {
            String where = "documents[" + i + "]";
            if (!(documents.get(i) instanceof Map<?, ?> raw)) {
                problems.add(where + ": must be a mapping");
                continue;
            }
            int before = problems.size();
            String title = text(raw, "title", 300, where, problems);
            String publisher = text(raw, "publisher", 200, where, problems);
            String url = text(raw, "url", 500, where, problems);
            String licence = text(raw, "licence", 60, where, problems);
            String language = text(raw, "language", 5, where, problems);
            List<String> crops = tags(raw, "crops", where, problems);
            List<String> topics = tags(raw, "topics", where, problems);
            String fileName = text(raw, "file", 300, where, problems);
            String sha256 = text(raw, "sha256", 64, where, problems);
            DataLabel label = dataLabel(raw, where, problems);

            if (url != null && !isHttpUrl(url)) {
                problems.add(where + ": url must be an absolute http(s) URL");
            }
            if (language != null && !LANGUAGES.contains(language)) {
                problems.add(where + ": language must be one of " + LANGUAGES);
            }
            if (licence != null && label != null) {
                checkLicence(licence, label, where, problems);
            }
            for (String crop : crops) {
                if (!knownCrops.contains(crop)) {
                    problems.add(where + ": unknown crop code '" + crop + "'");
                }
            }
            if (sha256 != null && !SHA256.matcher(sha256).matches()) {
                problems.add(where + ": sha256 must be 64 lowercase hex characters");
            }
            if (url != null && !urls.add(url)) {
                problems.add(where + ": duplicate url");
            }
            if (sha256 != null && !hashes.add(sha256)) {
                problems.add(where + ": duplicate sha256");
            }
            Path file = fileName == null ? null : resolveFile(root, fileName, where, problems);
            if (file != null && sha256 != null && SHA256.matcher(sha256).matches()) {
                try {
                    if (!sha256.equals(ModelFileCache.sha256(file))) {
                        problems.add(where + ": sha256 does not match " + fileName);
                    }
                } catch (IOException e) {
                    problems.add(where + ": cannot read " + fileName);
                }
            }
            if (problems.size() == before) {
                entries.add(new ManifestEntry(title, publisher, url, licence, language, crops, topics, file, sha256,
                        label));
            }
        }
        if (!problems.isEmpty()) {
            throw new KnowledgeManifestException(problems);
        }
        return List.copyOf(entries);
    }

    private void checkLicence(String licence, DataLabel label, String where, List<String> problems) {
        if (label == DataLabel.SYNTHETIC_TEST_DATA) {
            if (!SYNTHETIC_LICENCE.equals(licence)) {
                problems.add(where + ": synthetic test data must use licence " + SYNTHETIC_LICENCE);
            }
            if (!allowSynthetic) {
                problems.add(where + ": synthetic test data is not allowed outside tests");
            }
            return;
        }
        if (LICENCES.contains(licence)) {
            return;
        }
        if (NON_COMMERCIAL_LICENCE.equals(licence)) {
            if (!allowNonCommercial) {
                problems.add(where + ": non-commercial licence " + licence + " is not allowed");
            }
            return;
        }
        problems.add(where + ": licence '" + licence + "' is not on the allowlist");
    }

    private static Path resolveFile(Path root, String fileName, String where, List<String> problems) {
        Path file = root.resolve(fileName).normalize();
        if (!file.startsWith(root)) {
            problems.add(where + ": file must stay inside the knowledge folder");
            return null;
        }
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (EXTENSIONS.stream().noneMatch(lower::endsWith)) {
            problems.add(where + ": only .md and .txt files are supported");
            return null;
        }
        if (!Files.isRegularFile(file)) {
            problems.add(where + ": file not found: " + fileName);
            return null;
        }
        return file;
    }

    private static DataLabel dataLabel(Map<?, ?> raw, String where, List<String> problems) {
        Object value = raw.get("data_label");
        if (value == null) {
            return DataLabel.VERIFIED_SOURCE;
        }
        try {
            return DataLabel.valueOf(value.toString().strip());
        } catch (IllegalArgumentException e) {
            problems.add(where + ": data_label must be VERIFIED_SOURCE or SYNTHETIC_TEST_DATA");
            return null;
        }
    }

    private static String text(Map<?, ?> raw, String key, int maxLength, String where, List<String> problems) {
        Object value = raw.get(key);
        if (value == null || value.toString().isBlank()) {
            problems.add(where + ": '" + key + "' is required");
            return null;
        }
        String s = value.toString().strip();
        if (s.length() > maxLength) {
            problems.add(where + ": '" + key + "' is longer than " + maxLength + " characters");
            return null;
        }
        return s;
    }

    private static List<String> tags(Map<?, ?> raw, String key, String where, List<String> problems) {
        Object value = raw.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            problems.add(where + ": '" + key + "' must be a list");
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            String tag = item == null ? "" : item.toString().strip();
            if (!TAG.matcher(tag).matches()) {
                problems.add(where + ": invalid " + key + " value '" + tag + "'");
            } else {
                out.add(tag);
            }
        }
        return List.copyOf(out);
    }

    private static boolean isHttpUrl(String url) {
        try {
            URI uri = URI.create(url);
            return uri.isAbsolute() && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
