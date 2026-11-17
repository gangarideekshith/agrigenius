"""Private crop recommendation endpoints. Every route requires the internal API key."""

import logging
from typing import Annotated

from fastapi import APIRouter, Depends, Request

from app.core.errors import ApiError
from app.crop_recommendation.model_loader import LoadedModel
from app.crop_recommendation.predictor import predict
from app.crop_recommendation.schemas import ModelInfoResponse, PredictRequest, PredictResponse
from app.security.internal_api_key import require_internal_api_key

log = logging.getLogger(__name__)

router = APIRouter(
    prefix="/v1/crop-recommendation",
    tags=["crop-recommendation"],
    dependencies=[Depends(require_internal_api_key)],
)


def loaded_model(request: Request) -> LoadedModel:
    model = getattr(request.app.state, "crop_model", None)
    if model is None:
        raise ApiError(503, "MODEL_NOT_LOADED", "The crop recommendation model is not available.")
    return model


@router.post("/predict", response_model=PredictResponse, summary="Top-k crop predictions")
def predict_crops(body: PredictRequest, model: Annotated[LoadedModel, Depends(loaded_model)]) -> PredictResponse:
    response = predict(model, body.features, body.top_k)
    # Never log feature values: they are farm data.
    log.info(
        "Crop prediction request_id=%s model_version=%s candidates=%d warnings=%d inference_ms=%d",
        body.request_id,
        response.model.version,
        len(response.predictions),
        len(response.warnings),
        response.inference_ms,
    )
    return response


@router.get("/model", response_model=ModelInfoResponse, summary="Loaded model metadata")
def model_info(model: Annotated[LoadedModel, Depends(loaded_model)]) -> ModelInfoResponse:
    meta = model.metadata
    return ModelInfoResponse(
        name=meta.name,
        version=meta.version,
        sha256=model.sha256,
        data_label=meta.data_label,
        features=meta.features,
        classes=meta.classes,
        feature_ranges=meta.feature_ranges,
        metrics=meta.metrics,
        trained_at=meta.trained_at,
    )
