package com.agrigenius.chat.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.agrigenius.chat.application.AssistantReply;
import com.agrigenius.chat.application.AssistantReply.ReplyStatus;
import com.agrigenius.chat.application.ChatDisabledException;
import com.agrigenius.chat.application.ChatProviderUnavailableException;
import com.agrigenius.chat.application.ChatTurn;
import com.agrigenius.farmer.application.FarmerProfileService;
import com.agrigenius.knowledge.application.KnowledgeRetriever;
import com.agrigenius.knowledge.application.RetrievedChunk;
import com.agrigenius.recommendation.application.RecommendationService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

class SpringAiChatAssistantTest {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final UUID userId = UUID.randomUUID();
    private final List<RetrievedChunk> twoSources = List.of(
            new RetrievedChunk(UUID.randomUUID(), "Rice pests", "Pub A", "https://a.example/rice", "CC-BY-4.0", "SYNTHETIC_TEST_DATA",
                    "Brown spot appears as oval brown lesions.", 0.91),
            new RetrievedChunk(UUID.randomUUID(), "Paddy water", "Pub B", "https://b.example/water", "CC-BY-SA-4.0",
                    "SYNTHETIC_TEST_DATA", "Keep 2-5 cm standing water.", 0.85));

    @AfterEach
    void stop() {
        executor.shutdownNow();
    }

    private SpringAiChatAssistant assistant(ScriptedChatModel model, KnowledgeRetriever retriever, Duration timeout) {
        FarmerContextTools tools = new FarmerContextTools(mock(FarmerProfileService.class), mock(RecommendationService.class));
        return new SpringAiChatAssistant(model, retriever, tools, ChatPromptBuilder.fromClasspath(), executor, timeout, "low", 5);
    }

    private ChatTurn turn(String language, String question) {
        return new ChatTurn(userId, UUID.randomUUID(), language, question, List.of());
    }

    @Test
    void okAnswerKeepsOnlyValidCitationsAndUsage() {
        ScriptedChatModel model = new ScriptedChatModel()
                .replying(ScriptedChatModel.json("Likely brown spot. Confirm with your KVK.", List.of("S1", "S9", "s1"), false,
                        false, "Based on general guidance."));

        AssistantReply reply = assistant(model, (q, k) -> twoSources, Duration.ofSeconds(5)).reply(turn("en", "brown spots?"));

        assertThat(reply.status()).isEqualTo(ReplyStatus.OK);
        assertThat(reply.content()).isEqualTo("Likely brown spot. Confirm with your KVK.");
        assertThat(reply.sources()).extracting(AssistantReply.SourceRef::title).containsExactly("Rice pests");
        assertThat(reply.uncertainty()).isEqualTo("Based on general guidance.");
        assertThat(reply.model()).isEqualTo("claude-opus-5-5");
        assertThat(reply.inputTokens()).isEqualTo(1200);
        assertThat(reply.outputTokens()).isEqualTo(150);
        assertThat(reply.cacheReadTokens()).isEqualTo(800);
    }

    @Test
    void answerWithOnlyUnknownCitationsAndNoToolsIsDowngradedToFixedNoSourcesText() {
        ScriptedChatModel model = new ScriptedChatModel()
                .replying(ScriptedChatModel.json("Use 50 kg urea per acre", List.of("S7"), false, false, null));

        AssistantReply reply = assistant(model, (q, k) -> twoSources, Duration.ofSeconds(5)).reply(turn("en", "dose?"));

        assertThat(reply.status()).isEqualTo(ReplyStatus.NO_SOURCES);
        assertThat(reply.content()).doesNotContain("urea").contains("Kisan Call Centre");
        assertThat(reply.sources()).isEmpty();
    }

    @Test
    void insufficientInformationUsesTheLocalizedFixedMessage() {
        ScriptedChatModel model = new ScriptedChatModel()
                .replying(ScriptedChatModel.json("model text", List.of(), true, false, null));

        AssistantReply reply = assistant(model, (q, k) -> List.of(), Duration.ofSeconds(5)).reply(turn("te", "?"));

        assertThat(reply.status()).isEqualTo(ReplyStatus.NO_SOURCES);
        assertThat(reply.content()).isEqualTo(FallbackMessages.noSources("te")).doesNotContain("model text");
    }

    @Test
    void outOfScopeBecomesRefused() {
        ScriptedChatModel model = new ScriptedChatModel()
                .replying(ScriptedChatModel.json("Here is a poem", List.of(), false, true, null));

        AssistantReply reply = assistant(model, (q, k) -> twoSources, Duration.ofSeconds(5)).reply(turn("hi", "write a poem"));

        assertThat(reply.status()).isEqualTo(ReplyStatus.REFUSED);
        assertThat(reply.content()).isEqualTo(FallbackMessages.refused("hi"));
    }

    @Test
    void unparseableOutputNeverReachesTheFarmer() {
        ScriptedChatModel model = new ScriptedChatModel().replying("Sure! Spray 3 ml/litre of something. Not JSON.");

        AssistantReply reply = assistant(model, (q, k) -> twoSources, Duration.ofSeconds(5)).reply(turn("en", "q"));

        assertThat(reply.status()).isEqualTo(ReplyStatus.NO_SOURCES);
        assertThat(reply.content()).doesNotContain("Spray").isEqualTo(FallbackMessages.noSources("en"));
    }

    @Test
    void providerRefusalFinishReasonBecomesRefused() {
        ScriptedChatModel model = new ScriptedChatModel()
                .replying(ScriptedChatModel.json("x", List.of("S1"), false, false, null)).finishing("REFUSAL");

        AssistantReply reply = assistant(model, (q, k) -> twoSources, Duration.ofSeconds(5)).reply(turn("kn", "q"));

        assertThat(reply.status()).isEqualTo(ReplyStatus.REFUSED);
        assertThat(reply.content()).isEqualTo(FallbackMessages.refused("kn"));
    }

    @Test
    void providerFailureBecomesUnavailable() {
        ScriptedChatModel model = new ScriptedChatModel().failing(new IllegalStateException("401 secret-key-details"));

        assertThatThrownBy(() -> assistant(model, (q, k) -> twoSources, Duration.ofSeconds(5)).reply(turn("en", "q")))
                .isInstanceOf(ChatProviderUnavailableException.class)
                .hasMessageNotContaining("secret");
    }

    @Test
    void slowProviderTimesOut() {
        ScriptedChatModel model = new ScriptedChatModel().hanging(Duration.ofSeconds(5))
                .replying(ScriptedChatModel.json("late", List.of("S1"), false, false, null));
        long start = System.nanoTime();

        assertThatThrownBy(() -> assistant(model, (q, k) -> twoSources, Duration.ofMillis(200)).reply(turn("en", "q")))
                .isInstanceOf(ChatProviderUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void retrievalFailureStillCallsTheModelWithoutSources() {
        ScriptedChatModel model = new ScriptedChatModel()
                .replying(ScriptedChatModel.json("x", List.of(), true, false, null));
        KnowledgeRetriever broken = (q, k) -> {
            throw new IllegalStateException("db down");
        };

        AssistantReply reply = assistant(model, broken, Duration.ofSeconds(5)).reply(turn("en", "q"));

        assertThat(reply.status()).isEqualTo(ReplyStatus.NO_SOURCES);
        assertThat(model.lastPrompt.get()).isNotNull();
    }

    @Test
    void missingRetrieverIsTolerated() {
        ScriptedChatModel model = new ScriptedChatModel()
                .replying(ScriptedChatModel.json("x", List.of(), true, false, null));

        assertThat(assistant(model, null, Duration.ofSeconds(5)).reply(turn("en", "q")).status())
                .isEqualTo(ReplyStatus.NO_SOURCES);
    }

    @Test
    void promptCarriesFixedSystemTextToolsAndUserIdOnlyInToolContext() {
        ScriptedChatModel model = new ScriptedChatModel()
                .replying(ScriptedChatModel.json("x", List.of("S1"), false, false, null));

        assistant(model, (q, k) -> twoSources, Duration.ofSeconds(5)).reply(turn("ta", "MARKER-QUESTION-42"));

        Prompt prompt = model.lastPrompt.get();
        List<Message> messages = prompt.getInstructions();
        assertThat(messages.getFirst()).isInstanceOf(SystemMessage.class);
        assertThat(messages.getFirst().getText()).doesNotContain("MARKER-QUESTION-42").doesNotContain(userId.toString());
        assertThat(messages.getLast().getText()).contains("MARKER-QUESTION-42").doesNotContain(userId.toString());
        assertThat(prompt.getOptions()).isInstanceOf(ToolCallingChatOptions.class);
        ToolCallingChatOptions options = (ToolCallingChatOptions) prompt.getOptions();
        assertThat(options.getToolContext()).containsEntry(FarmerContextTools.USER_ID_KEY, userId);
        assertThat(options.getToolCallbacks()).extracting(ToolCallback::getToolDefinition)
                .extracting(d -> d.name())
                .containsExactlyInAnyOrderElementsOf(FarmerContextTools.TOOL_NAMES);
    }

    @Test
    void toolUseAloneIsEnoughBasisForAnOkAnswer() {
        SpringAiChatAssistant assistant = assistant(new ScriptedChatModel(), (q, k) -> List.of(), Duration.ofSeconds(5));
        ScriptedChatModel model = new ScriptedChatModel()
                .replying(ScriptedChatModel.json("Your last recommendation suggested rice.", List.of(), false, false, null));

        AssistantReply reply = assistant.toReply("en", model.call(new Prompt("x")), List.of(), List.of("getMyCropRecommendations"));

        assertThat(reply.status()).isEqualTo(ReplyStatus.OK);
        assertThat(reply.toolsUsed()).containsExactly("getMyCropRecommendations");
    }

    @Test
    void disabledAssistantAlwaysThrows() {
        assertThatThrownBy(() -> new DisabledChatAssistant().reply(turn("en", "q")))
                .isInstanceOf(ChatDisabledException.class);
    }
}
