package com.agrigenius.integration.ml;

/** The ML service could not be reached or returned an unusable response. */
public class MlServiceException extends RuntimeException {

    public MlServiceException(String message) {
        super(message);
    }

    public MlServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
