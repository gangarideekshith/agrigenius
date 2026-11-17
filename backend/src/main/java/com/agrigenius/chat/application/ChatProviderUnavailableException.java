package com.agrigenius.chat.application;

import com.agrigenius.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** The LLM provider failed, timed out or rejected the request. Never carries provider details. */
public class ChatProviderUnavailableException extends ApiException {

    public ChatProviderUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_PROVIDER_UNAVAILABLE", "Service unavailable",
                "The chat assistant is temporarily unavailable. Please try again later.");
    }
}
