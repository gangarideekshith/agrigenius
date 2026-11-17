package com.agrigenius.chat.application;

import com.agrigenius.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** No chat provider is configured (CHAT_PROVIDER=none or no API key). */
public class ChatDisabledException extends ApiException {

    public ChatDisabledException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_DISABLED", "Service unavailable",
                "The chat assistant is not configured yet.");
    }
}
