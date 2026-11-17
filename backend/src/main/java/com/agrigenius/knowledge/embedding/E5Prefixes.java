package com.agrigenius.knowledge.embedding;

/**
 * multilingual-e5 models are trained with role prefixes: stored text must be embedded as
 * {@code "passage: ..."} and search text as {@code "query: ..."}. Without them retrieval quality drops.
 */
public final class E5Prefixes {

    public static final String QUERY = "query: ";
    public static final String PASSAGE = "passage: ";

    private E5Prefixes() {
    }

    public static String query(String text) {
        return QUERY + text;
    }

    public static String passage(String text) {
        return PASSAGE + text;
    }
}
