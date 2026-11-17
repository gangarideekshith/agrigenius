package com.agrigenius.integration.ml.crop;

/**
 * Port to the private ML service's crop recommendation model (contract:
 * docs/api/contracts/crop-recommendation). Implementations throw
 * {@link MlServiceUnavailableException} or {@link MlServiceBadResponseException}.
 */
public interface CropModelClient {

    CropPrediction predict(CropFeatures features, java.util.UUID requestId, int topK);

    CropModelInfo modelInfo();
}
