package com.agrigenius.knowledge.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Which embedding configuration activates for each EMBEDDING_PROVIDER value (no model is loaded here). */
class EmbeddingProviderSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TransformersProviderGuard.class);

    @Test
    void transformersValueFailsFastWithTheSupportedAlternative() {
        runner.withPropertyValues("spring.ai.model.embedding=transformers")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("EMBEDDING_PROVIDER=e5-onnx"));
    }

    @Test
    void otherValuesDoNotTriggerTheGuard() {
        runner.withPropertyValues("spring.ai.model.embedding=none")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(TransformersProviderGuard.class));
        runner.withPropertyValues("spring.ai.model.embedding=e5-onnx")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void e5ConfigurationOnlyActivatesForItsOwnValue() {
        new ApplicationContextRunner().withUserConfiguration(EmbeddingModelConfig.class)
                .withPropertyValues("spring.ai.model.embedding=none")
                .run(context -> assertThat(context).doesNotHaveBean(EmbeddingModelConfig.class));
    }
}
