package com.agrigenius.knowledge.embedding;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads a model file once into a local cache and verifies its SHA-256 before it is ever loaded
 * (ONNX and tokenizer files are parsed by native code). Uses {@link HttpURLConnection}, not the JDK
 * HttpClient, because NIO sockets are unreliable on some Windows machines (see DEVELOPMENT.md).
 */
public final class ModelFileCache {

    private static final Logger log = LoggerFactory.getLogger(ModelFileCache.class);

    private ModelFileCache() {
    }

    /**
     * Returns the cached file, downloading it if missing. When {@code expectedSha256} is set, both a
     * cached and a freshly downloaded file must match it; a mismatching download is deleted and an
     * exception is thrown.
     */
    public static Path fetch(URI source, String expectedSha256, Path cacheDir, String fileName) throws IOException {
        Files.createDirectories(cacheDir);
        Path target = cacheDir.resolve(fileName);
        if (Files.exists(target)) {
            if (expectedSha256 == null || expectedSha256.equalsIgnoreCase(sha256(target))) {
                return target;
            }
            log.warn("Cached model file {} failed its checksum; downloading again", fileName);
            Files.delete(target);
        }
        Path partial = cacheDir.resolve(fileName + ".part");
        long started = System.nanoTime();
        log.info("Downloading model file {} (one-time) into {}", fileName, cacheDir);
        URLConnection connection = source.toURL().openConnection();
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(120_000);
        if (connection instanceof HttpURLConnection http) {
            http.setInstanceFollowRedirects(true);
            int status = http.getResponseCode();
            if (status / 100 != 2) {
                throw new IOException("Model download failed for " + fileName + ": HTTP " + status);
            }
        }
        String actual;
        MessageDigest digest = newSha256();
        try (InputStream in = new DigestInputStream(connection.getInputStream(), digest);
                OutputStream out = Files.newOutputStream(partial)) {
            in.transferTo(out);
        }
        actual = HexFormat.of().formatHex(digest.digest());
        if (expectedSha256 != null && !expectedSha256.equalsIgnoreCase(actual)) {
            Files.deleteIfExists(partial);
            throw new IOException("Checksum mismatch for downloaded model file " + fileName);
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        log.info("Model file {} ready ({} bytes, {} s)", fileName, Files.size(target),
                (System.nanoTime() - started) / 1_000_000_000);
        return target;
    }

    public static String sha256(Path file) throws IOException {
        MessageDigest digest = newSha256();
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
