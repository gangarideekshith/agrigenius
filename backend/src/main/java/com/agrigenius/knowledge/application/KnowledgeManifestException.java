package com.agrigenius.knowledge.application;

import java.util.List;

/** The manifest (or a file it lists) is invalid; nothing is ingested. Lists every problem found. */
public class KnowledgeManifestException extends RuntimeException {

    private final List<String> problems;

    public KnowledgeManifestException(List<String> problems) {
        super("Knowledge manifest rejected (" + problems.size() + " problem(s)):\n - " + String.join("\n - ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
