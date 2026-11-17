package com.agrigenius.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agrigenius.knowledge.SyntheticCorpus;
import com.agrigenius.knowledge.domain.KnowledgeDocument.DataLabel;
import com.agrigenius.knowledge.embedding.ModelFileCache;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnowledgeManifestLoaderTest {

    private static final Set<String> CROPS = Set.of("rice", "maize");

    @TempDir
    Path dir;

    private String sha;

    @BeforeEach
    void writeDocument() throws IOException {
        Files.writeString(dir.resolve("rice.md"), "# Rice\n\nSome reviewed text.\n");
        sha = ModelFileCache.sha256(dir.resolve("rice.md"));
    }

    private static String entry(String overrides) {
        return """
                  - title: "Rice guide"
                    publisher: "Publisher"
                    url: "https://example.org/rice"
                    licence: "CC-BY-4.0"
                    language: "en"
                    crops: ["rice"]
                    topics: ["pests"]
                    file: "rice.md"
                    sha256: "%s"
                """ + overrides;
    }

    private List<ManifestEntry> load(String documents, boolean nonCommercial, boolean synthetic) throws IOException {
        Files.writeString(dir.resolve("manifest.yaml"), "documents:\n" + documents.replace("%s", sha));
        return new KnowledgeManifestLoader(nonCommercial, synthetic).load(dir, CROPS);
    }

    private List<String> problems(String documents) throws IOException {
        try {
            load(documents, true, false);
        } catch (KnowledgeManifestException e) {
            return e.problems();
        }
        throw new AssertionError("expected the manifest to be rejected");
    }

    private static String replace(String field, String value) {
        return entry("").replaceFirst("(?m)^(\\s+)" + field + ": .*$", "$1" + field + ": " + value);
    }

    @Test
    void validManifestLoadsWithDefaultsAndResolvedFile() throws IOException {
        List<ManifestEntry> entries = load(entry(""), true, false);

        assertThat(entries).singleElement().satisfies(e -> {
            assertThat(e.title()).isEqualTo("Rice guide");
            assertThat(e.crops()).containsExactly("rice");
            assertThat(e.dataLabel()).isEqualTo(DataLabel.VERIFIED_SOURCE);
            assertThat(e.file()).isEqualTo(dir.resolve("rice.md").toAbsolutePath().normalize());
        });
    }

    @Test
    void missingRequiredFieldsAreAllReported() throws IOException {
        List<String> problems = problems("  - file: \"rice.md\"\n");

        assertThat(problems).anyMatch(p -> p.contains("'title' is required"))
                .anyMatch(p -> p.contains("'publisher' is required"))
                .anyMatch(p -> p.contains("'url' is required"))
                .anyMatch(p -> p.contains("'licence' is required"))
                .anyMatch(p -> p.contains("'language' is required"))
                .anyMatch(p -> p.contains("'sha256' is required"));
    }

    @Test
    void unsupportedLanguageIsRejected() throws IOException {
        assertThat(problems(replace("language", "\"fr\""))).anyMatch(p -> p.contains("language must be one of"));
    }

    @Test
    void licenceOutsideAllowlistIsRejected() throws IOException {
        assertThat(problems(replace("licence", "\"All rights reserved\""))).anyMatch(p -> p.contains("not on the allowlist"));
    }

    @Test
    void nonCommercialLicenceDependsOnPolicy() throws IOException {
        String doc = replace("licence", "\"CC-BY-NC-SA-3.0-IGO\"");
        assertThat(load(doc, true, false)).hasSize(1);
        assertThatThrownBy(() -> load(doc, false, false))
                .isInstanceOf(KnowledgeManifestException.class)
                .hasMessageContaining("non-commercial licence");
    }

    @Test
    void syntheticDataIsRefusedUnlessAllowedAndNeedsItsOwnLicence() throws IOException {
        String synthetic = entry("    data_label: \"SYNTHETIC_TEST_DATA\"\n")
                .replace("CC-BY-4.0", KnowledgeManifestLoader.SYNTHETIC_LICENCE);
        assertThatThrownBy(() -> load(synthetic, true, false)).hasMessageContaining("not allowed outside tests");
        assertThat(load(synthetic, true, true)).singleElement()
                .extracting(ManifestEntry::dataLabel).isEqualTo(DataLabel.SYNTHETIC_TEST_DATA);

        String wrongLicence = entry("    data_label: \"SYNTHETIC_TEST_DATA\"\n");
        assertThatThrownBy(() -> load(wrongLicence, true, true)).hasMessageContaining("must use licence");
        String misusedLicence = entry("").replace("CC-BY-4.0", KnowledgeManifestLoader.SYNTHETIC_LICENCE);
        assertThatThrownBy(() -> load(misusedLicence, true, true)).hasMessageContaining("not on the allowlist");
    }

    @Test
    void unknownDataLabelIsRejected() throws IOException {
        assertThat(problems(entry("    data_label: \"GUESSWORK\"\n"))).anyMatch(p -> p.contains("data_label must be"));
    }

    @Test
    void checksumMustMatchAndBeWellFormed() throws IOException {
        assertThat(problems(entry("").replace("%s", "a".repeat(64)))).anyMatch(p -> p.contains("does not match"));
        assertThat(problems(entry("").replace("%s", "ABC"))).anyMatch(p -> p.contains("64 lowercase hex"));
    }

    @Test
    void filesMustExistStayInsideTheFolderAndBeMarkdownOrText() throws IOException {
        assertThat(problems(replace("file", "\"missing.md\""))).anyMatch(p -> p.contains("file not found"));
        assertThat(problems(replace("file", "\"../outside.md\""))).anyMatch(p -> p.contains("inside the knowledge folder"));
        Files.writeString(dir.resolve("guide.pdf"), "x");
        assertThat(problems(replace("file", "\"guide.pdf\""))).anyMatch(p -> p.contains("only .md and .txt"));
    }

    @Test
    void cropsMustBeKnownCodes() throws IOException {
        assertThat(problems(replace("crops", "[\"dragonfruit\"]"))).anyMatch(p -> p.contains("unknown crop code"));
    }

    @Test
    void urlMustBeAbsoluteHttp() throws IOException {
        assertThat(problems(replace("url", "\"ftp://example.org/x\""))).anyMatch(p -> p.contains("http(s) URL"));
        assertThat(problems(replace("url", "\"/relative\""))).anyMatch(p -> p.contains("http(s) URL"));
    }

    @Test
    void duplicateUrlsAndChecksumsAreRejected() throws IOException {
        assertThat(problems(entry("") + entry(""))).anyMatch(p -> p.contains("duplicate url"))
                .anyMatch(p -> p.contains("duplicate sha256"));
    }

    @Test
    void missingOrMalformedManifestIsRejected() throws IOException {
        assertThatThrownBy(() -> new KnowledgeManifestLoader(true, false).load(dir, CROPS))
                .hasMessageContaining("manifest not found");
        Files.writeString(dir.resolve("manifest.yaml"), "items: []\n");
        assertThatThrownBy(() -> new KnowledgeManifestLoader(true, false).load(dir, CROPS))
                .hasMessageContaining("'documents' list");
        Files.writeString(dir.resolve("manifest.yaml"), "documents: [\n");
        assertThatThrownBy(() -> new KnowledgeManifestLoader(true, false).load(dir, CROPS))
                .hasMessageContaining("not valid YAML");
    }

    @Test
    void yamlTagsCannotInstantiateObjects() throws IOException {
        Files.writeString(dir.resolve("manifest.yaml"), "documents: !!javax.script.ScriptEngineManager []\n");
        assertThatThrownBy(() -> new KnowledgeManifestLoader(true, false).load(dir, CROPS))
                .isInstanceOf(KnowledgeManifestException.class);
    }

    @Test
    void syntheticCorpusTemplateIsValidWhenSyntheticDataIsAllowed(@TempDir Path corpus) throws IOException {
        SyntheticCorpus.copyTo(corpus);
        List<ManifestEntry> entries = new KnowledgeManifestLoader(true, true).load(corpus, CROPS);

        assertThat(entries).hasSize(3).allMatch(e -> e.dataLabel() == DataLabel.SYNTHETIC_TEST_DATA)
                .allMatch(e -> e.title().startsWith("SYNTHETIC TEST DOCUMENT"));
    }
}
