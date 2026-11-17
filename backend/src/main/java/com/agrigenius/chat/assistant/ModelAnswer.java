package com.agrigenius.chat.assistant;

import java.util.List;

/**
 * The JSON shape the model must return. Parsed with Spring AI's {@code BeanOutputConverter};
 * anything that does not parse is treated as {@code NO_SOURCES} and never shown to the farmer.
 */
public record ModelAnswer(
        String answer,
        List<String> citedSourceIds,
        boolean insufficientInformation,
        boolean outOfScope,
        String uncertainty) {
}
