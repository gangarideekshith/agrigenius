package com.agrigenius.integration.ml.crop;

import com.agrigenius.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** The ML service could not be reached, timed out, or reported that it cannot serve (5xx). */
public class MlServiceUnavailableException extends ApiException {

    public MlServiceUnavailableException(String detail) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "ML_SERVICE_UNAVAILABLE", "Service unavailable", detail);
    }
}
