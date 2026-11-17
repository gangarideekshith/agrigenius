package com.agrigenius.chat.assistant;

import com.agrigenius.chat.application.AssistantReply;
import com.agrigenius.chat.application.ChatAssistant;
import com.agrigenius.chat.application.ChatDisabledException;
import com.agrigenius.chat.application.ChatTurn;

/** Used when no chat model is configured ({@code CHAT_PROVIDER=none}, the default): always 503 CHAT_DISABLED. */
public class DisabledChatAssistant implements ChatAssistant {

    @Override
    public AssistantReply reply(ChatTurn turn) {
        throw new ChatDisabledException();
    }
}
