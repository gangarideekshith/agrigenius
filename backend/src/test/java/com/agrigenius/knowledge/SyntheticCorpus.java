package com.agrigenius.knowledge;

import com.agrigenius.knowledge.embedding.ModelFileCache;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Copies the synthetic test corpus into a folder and writes its manifest.yaml with real checksums. */
public final class SyntheticCorpus {

    private static final Pattern SHA_PLACEHOLDER = Pattern.compile("\\{\\{sha256:([^}]+)}}");

    private SyntheticCorpus() {
    }

    public static Path copyTo(Path dir) throws IOException {
        Path source;
        try {
            source = Path.of(SyntheticCorpus.class.getResource("/knowledge-synthetic").toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        Files.createDirectories(dir);
        try (Stream<Path> files = Files.list(source)) {
            for (Path file : files.toList()) {
                Files.copy(file, dir.resolve(file.getFileName()));
            }
        }
        writeManifest(dir);
        return dir;
    }

    /** (Re)writes manifest.yaml from the template using the current file contents. */
    public static void writeManifest(Path dir) throws IOException {
        writeManifest(dir, Files.readString(dir.resolve("manifest.template.yaml")));
    }

    public static void writeManifest(Path dir, String template) throws IOException {
        Matcher m = SHA_PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, ModelFileCache.sha256(dir.resolve(m.group(1))));
        }
        m.appendTail(out);
        Files.writeString(dir.resolve("manifest.yaml"), out.toString());
    }

    public static List<String> fileNames() {
        return List.of("paddy-leaf-spots.md", "maize-watering.md", "soil-sampling.txt");
    }
}
