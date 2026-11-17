package com.agrigenius.chat.assistant;

import com.agrigenius.chat.application.ChatAssistant;
import com.agrigenius.chat.config.ChatProperties;
import com.agrigenius.farmer.application.FarmerProfileService;
import com.agrigenius.knowledge.application.KnowledgeRetriever;
import com.agrigenius.knowledge.config.KnowledgeProperties;
import com.agrigenius.recommendation.application.RecommendationService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the assistant at startup: the Spring AI implementation when a {@link ChatModel} is
 * configured ({@code CHAT_PROVIDER=anthropic} with a key), otherwise {@link DisabledChatAssistant}.
 * {@link ObjectProvider} is used because the ChatModel comes from auto-configuration, which is
 * processed after this class (so {@code @ConditionalOnBean} would not see it).
 */
@Configuration
public class ChatAssistantConfig {

    /** Bounded pool for provider calls so a slow provider cannot exhaust request threads. */
    @Bean(destroyMethod = "shutdownNow")
    ExecutorService chatProviderExecutor() {
        AtomicInteger n = new AtomicInteger();
        return new ThreadPoolExecutor(4, 16, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), r -> {
            Thread t = new Thread(r, "chat-provider-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    FarmerContextTools farmerContextTools(FarmerProfileService profiles, RecommendationService recommendations) {
        return new FarmerContextTools(profiles, recommendations);
    }

    @Bean
    ChatAssistant chatAssistant(ObjectProvider<ChatModel> chatModel, ObjectProvider<KnowledgeRetriever> retriever,
            FarmerContextTools tools, ExecutorService chatProviderExecutor, ChatProperties chat,
            KnowledgeProperties knowledge) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            return new DisabledChatAssistant();
        }
        return new SpringAiChatAssistant(model, retriever.getIfAvailable(), tools, ChatPromptBuilder.fromClasspath(),
                chatProviderExecutor, chat.providerTimeout(), chat.effort(), knowledge.topK());
    }
}
