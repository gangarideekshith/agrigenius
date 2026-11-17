package com.agrigenius.integration.ml.crop;

import com.agrigenius.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** The ML service answered, but with something the backend cannot use (4xx, malformed or invalid body). */
public class MlServiceBadResponseException extends ApiException {

    public MlServiceBadResponseException(String detail) {
        super(HttpStatus.BAD_GATEWAY, "ML_SERVICE_ERROR", "Bad gateway", detail);
    }
}
