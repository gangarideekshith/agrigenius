"""Top-k crop predictions with data-derived explanations and out-of-range warnings."""

import math
import time

import numpy as np
import pandas as pd

from app.crop_recommendation.model_loader import LoadedModel
from app.crop_recommendation.schemas import (
    CropFeatures,
    ExplanationItem,
    ModelRef,
    Prediction,
    PredictionWarning,
    PredictResponse,
)

MAX_EXPLANATION_ITEMS = 7
CLOSEST_FEATURES_WHEN_ALL_WITHIN = 2


def _floor6(value: float) -> float:
    """Round down to 6 decimals so the returned probabilities never sum above 1."""
    return min(1.0, max(0.0, math.floor(float(value) * 1_000_000) / 1_000_000))


def _explain(model: LoadedModel, crop: str, values: dict[str, float]) -> list[ExplanationItem]:
    percentiles = model.metadata.class_feature_percentiles.get(crop, {})
    known = [f for f in model.metadata.features if f in percentiles]
    if not known:
        return []

    def item(feature: str) -> ExplanationItem:
        p = percentiles[feature]
        return ExplanationItem(feature=feature, value=values[feature], p05=p.p05, p95=p.p95)

    outside = [f for f in known if values[f] < percentiles[f].p05 or values[f] > percentiles[f].p95]
    if outside:
        return [item(f) for f in outside[:MAX_EXPLANATION_ITEMS]]

    def edge_distance(feature: str) -> float:
        p = percentiles[feature]
        width = p.p95 - p.p05
        nearest = min(values[feature] - p.p05, p.p95 - values[feature])
        return nearest / width if width > 0 else 0.0

    closest = sorted(known, key=edge_distance)[:CLOSEST_FEATURES_WHEN_ALL_WITHIN]
    return [item(f) for f in closest]


def _warnings(model: LoadedModel, values: dict[str, float]) -> list[PredictionWarning]:
    warnings = []
    for feature in model.metadata.features:
        bounds = model.metadata.feature_ranges.get(feature)
        if bounds is not None and not (bounds.min <= values[feature] <= bounds.max):
            warnings.append(PredictionWarning(code="INPUT_OUTSIDE_TRAINING_RANGE", feature=feature))
    return warnings


def predict(model: LoadedModel, features: CropFeatures, top_k: int) -> PredictResponse:
    started = time.perf_counter()
    feature_names = model.metadata.features
    values = {name: float(getattr(features, name)) for name in feature_names}
    row = [[values[name] for name in feature_names]]
    if hasattr(model.estimator, "feature_names_in_"):
        x = pd.DataFrame(row, columns=feature_names)
    else:
        x = np.asarray(row, dtype=float)

    probabilities = model.estimator.predict_proba(x)[0]
    classes = [str(c) for c in model.estimator.classes_]
    order = np.argsort(-probabilities, kind="stable")[: min(top_k, len(classes))]

    predictions = [
        Prediction(
            crop=classes[i],
            probability=_floor6(probabilities[i]),
            explanation=_explain(model, classes[i], values),
        )
        for i in order
    ]
    meta = model.metadata
    return PredictResponse(
        model=ModelRef(name=meta.name, version=meta.version, sha256=model.sha256, data_label=meta.data_label),
        predictions=predictions,
        warnings=_warnings(model, values),
        inference_ms=int(round((time.perf_counter() - started) * 1000)),
    )
