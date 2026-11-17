package com.agrigenius.chat.assistant;

import com.agrigenius.chat.application.AssistantReply;
import com.agrigenius.chat.application.AssistantReply.ReplyStatus;
import com.agrigenius.chat.application.AssistantReply.SourceRef;
import com.agrigenius.chat.application.ChatAssistant;
import com.agrigenius.chat.application.ChatProviderUnavailableException;
import com.agrigenius.chat.application.ChatTurn;
import com.agrigenius.knowledge.application.KnowledgeRetriever;
import com.agrigenius.knowledge.application.RetrievedChunk;
import com.anthropic.models.messages.OutputConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicCacheOptions;
import org.springframework.ai.anthropic.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.converter.BeanOutputConverter;

/**
 * Answers one farmer turn with Spring AI's {@link ChatClient} (ADR-0006/0007): retrieval, a fixed
 * system prompt, owner-scoped read-only tools and a JSON answer whose citations are validated
 * against the sources actually retrieved.
 *
 * <p>Fixed fallback text is used for {@code NO_SOURCES} and {@code REFUSED}, for unparseable model
 * output, and for answers that cite nothing and used no tool, so unsupported advice never reaches the
 * farmer. Prompts, messages and source text are never logged.
 */
public class SpringAiChatAssistant implements ChatAssistant {

    private static final Logger log = LoggerFactory.getLogger(SpringAiChatAssistant.class);

    private final ChatModel chatModel;
    private final ChatClient chatClient;
    private final KnowledgeRetriever retriever;
    private final FarmerContextTools tools;
    private final ChatPromptBuilder promptBuilder;
    private final BeanOutputConverter<ModelAnswer> converter = new BeanOutputConverter<>(ModelAnswer.class);
    private final ExecutorService executor;
    private final Duration timeout;
    private final String effort;
    private final int topK;

    public SpringAiChatAssistant(ChatModel chatModel, KnowledgeRetriever retriever, FarmerContextTools tools,
            ChatPromptBuilder promptBuilder, ExecutorService executor, Duration timeout, String effort, int topK) {
        this.chatModel = chatModel;
        this.chatClient = ChatClient.create(chatModel);
        this.retriever = retriever;
        this.tools = tools;
        this.promptBuilder = promptBuilder;
        this.executor = executor;
        this.timeout = timeout;
        this.effort = effort;
        this.topK = topK;
    }

    @Override
    public AssistantReply reply(ChatTurn turn) {
        List<RetrievedChunk> sources = retrieve(turn.userMessage());
        ToolUsageCollector collector = new ToolUsageCollector();
        List<Message> messages = promptBuilder.messages(turn, sources, FarmerContextTools.TOOL_NAMES, converter.getFormat());

        Map<String, Object> toolContext = new LinkedHashMap<>();
        toolContext.put(FarmerContextTools.USER_ID_KEY, turn.userId());
        toolContext.put(FarmerContextTools.COLLECTOR_KEY, collector);

        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .messages(messages)
                .tools(tools)
                .toolContext(toolContext);
        // Spring AI 2.0 builds request options from chatModel.getOptions().mutate() and attaches tools only
        // when that is a tool-calling builder (AnthropicChatOptions is). Anthropic-specific knobs merge on top.
        if (chatModel instanceof AnthropicChatModel) {
            spec = spec.options(anthropicOptions());
        }

        ChatResponse response = callWithTimeout(spec);
        return toReply(turn.language(), response, sources, collector.names());
    }

    /** Effort and adaptive thinking (Claude Opus 5.5 cannot disable thinking); cache the fixed system prompt and tools. */
    private AnthropicChatOptions.Builder anthropicOptions() {
        AnthropicChatOptions.Builder builder = AnthropicChatOptions.builder();
        builder.effort(OutputConfig.Effort.of(effort));
        builder.thinkingAdaptive();
        builder.cacheOptions(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS).build());
        return builder;
    }

    private List<RetrievedChunk> retrieve(String question) {
        if (retriever == null) {
            return List.of();
        }
        try {
            List<RetrievedChunk> found = retriever.retrieve(question, topK);
            return found == null ? List.of() : List.copyOf(found);
        } catch (RuntimeException ex) {
            log.warn("Knowledge retrieval failed: {}", ex.getClass().getSimpleName());
            return List.of();
        }
    }

    private ChatResponse callWithTimeout(ChatClient.ChatClientRequestSpec spec) {
        Future<ChatResponse> future;
        try {
            future = executor.submit(() -> spec.call().chatResponse());
        } catch (RejectedExecutionException ex) {
            log.warn("Chat provider call rejected: executor saturated");
            throw new ChatProviderUnavailableException();
        }
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            log.warn("Chat provider call timed out after {} ms", timeout.toMillis());
            throw new ChatProviderUnavailableException();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            log.warn("Chat provider call failed: {}", cause.getClass().getSimpleName());
            throw new ChatProviderUnavailableException();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new ChatProviderUnavailableException();
        }
    }

    AssistantReply toReply(String language, ChatResponse response, List<RetrievedChunk> sources, List<String> toolsUsed) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            log.warn("Chat provider returned an empty response");
            throw new ChatProviderUnavailableException();
        }
        Generation result = response.getResult();
        String model = response.getMetadata() == null ? null : response.getMetadata().getModel();
        Integer in = null;
        Integer out = null;
        Integer cacheRead = null;
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (usage != null) {
            in = usage.getPromptTokens();
            out = usage.getCompletionTokens();
            Long cached = usage.getCacheReadInputTokens();
            cacheRead = cached == null ? null : Math.toIntExact(Math.min(cached, Integer.MAX_VALUE));
        }

        String finishReason = result.getMetadata() == null ? null : result.getMetadata().getFinishReason();
        if (finishReason != null && finishReason.toLowerCase(Locale.ROOT).contains("refusal")) {
            return fixed(ReplyStatus.REFUSED, FallbackMessages.refused(language), toolsUsed, model, in, out, cacheRead);
        }

        ModelAnswer answer;
        try {
            answer = converter.convert(result.getOutput().getText());
        } catch (RuntimeException ex) {
            log.warn("Chat model output could not be parsed: {}", ex.getClass().getSimpleName());
            return fixed(ReplyStatus.NO_SOURCES, FallbackMessages.noSources(language), toolsUsed, model, in, out, cacheRead);
        }
        if (answer == null) {
            return fixed(ReplyStatus.NO_SOURCES, FallbackMessages.noSources(language), toolsUsed, model, in, out, cacheRead);
        }
        if (answer.outOfScope()) {
            return fixed(ReplyStatus.REFUSED, FallbackMessages.refused(language), toolsUsed, model, in, out, cacheRead);
        }
        List<SourceRef> cited = validCitations(answer.citedSourceIds(), sources);
        boolean hasBasis = !cited.isEmpty() || !toolsUsed.isEmpty();
        if (answer.insufficientInformation() || !hasBasis || answer.answer() == null || answer.answer().isBlank()) {
            return fixed(ReplyStatus.NO_SOURCES, FallbackMessages.noSources(language), toolsUsed, model, in, out, cacheRead);
        }
        return new AssistantReply(ReplyStatus.OK, answer.answer().strip(), cited, toolsUsed,
                blankToNull(answer.uncertainty()), model, in, out, cacheRead);
    }

    /** Keeps only ids S1..Sn that exist in this turn's sources, de-duplicated, in citation order. */
    static List<SourceRef> validCitations(List<String> ids, List<RetrievedChunk> sources) {
        if (ids == null || ids.isEmpty() || sources.isEmpty()) {
            return List.of();
        }
        List<SourceRef> out = new ArrayList<>();
        List<Integer> seen = new ArrayList<>();
        for (String raw : ids) {
            if (raw == null) {
                continue;
            }
            String id = raw.strip().toUpperCase(Locale.ROOT);
            if (!id.matches("S[1-9][0-9]{0,2}")) {
                continue;
            }
            int index = Integer.parseInt(id.substring(1)) - 1;
            if (index < sources.size() && !seen.contains(index)) {
                seen.add(index);
                RetrievedChunk s = sources.get(index);
                out.add(new SourceRef(s.title(), s.publisher(), s.url(), s.licence()));
            }
        }
        return List.copyOf(out);
    }

    private static AssistantReply fixed(ReplyStatus status, String text, List<String> toolsUsed, String model,
            Integer in, Integer out, Integer cacheRead) {
        return new AssistantReply(status, text, List.of(), toolsUsed, null, model, in, out, cacheRead);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
