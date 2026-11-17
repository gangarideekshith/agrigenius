package com.agrigenius.chat.assistant;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Records which tools ran during one turn (tools may run on other threads). Names only, never data. */
final class ToolUsageCollector {

    private final Set<String> names = new LinkedHashSet<>();

    synchronized void record(String toolName) {
        names.add(toolName);
    }

    synchronized List<String> names() {
        return List.copyOf(new ArrayList<>(names));
    }
}
