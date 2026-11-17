package com.agrigenius.chat.application;

/**
 * Produces the assistant's reply for one farmer turn (retrieval, LLM call, tools).
 * Implementations never persist anything; {@code ChatService} owns storage, ownership and limits.
 *
 * @throws ChatDisabledException            when no chat provider is configured
 * @throws ChatProviderUnavailableException when the provider fails or times out
 */
public interface ChatAssistant {

    AssistantReply reply(ChatTurn turn);
}
