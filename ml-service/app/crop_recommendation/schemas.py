"""Request/response models of the private crop recommendation API.

Mirrors docs/api/contracts/crop-recommendation/*.json (snake_case). Requests reject unknown
fields so contract drift fails loudly instead of being ignored.
"""

from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

FEATURES: tuple[str, ...] = (
    "nitrogen",
    "phosphorus",
    "potassium",
    "temperature_c",
    "humidity_pct",
    "ph",
    "rainfall_mm",
)


class _Strict(BaseModel):
    model_config = ConfigDict(extra="forbid")


class CropFeatures(_Strict):
    nitrogen: float = Field(ge=0, le=300)
    phosphorus: float = Field(ge=0, le=300)
    potassium: float = Field(ge=0, le=300)
    temperature_c: float = Field(ge=-10, le=60)
    humidity_pct: float = Field(ge=0, le=100)
    ph: float = Field(ge=0, le=14)
    rainfall_mm: float = Field(ge=0, le=5000)


class PredictRequest(_Strict):
    request_id: UUID
    top_k: int = Field(default=5, ge=1, le=10)
    features: CropFeatures


class ModelRef(BaseModel):
    name: str
    version: str
    sha256: str
    data_label: str


class ExplanationItem(BaseModel):
    feature: str
    value: float
    p05: float
    p95: float


class Prediction(BaseModel):
    crop: str
    probability: float = Field(ge=0, le=1)
    explanation: list[ExplanationItem]


class PredictionWarning(BaseModel):
    code: Literal["INPUT_OUTSIDE_TRAINING_RANGE"]
    feature: str


class PredictResponse(BaseModel):
    model: ModelRef
    predictions: list[Prediction]
    warnings: list[PredictionWarning]
    inference_ms: int


class FeatureRange(BaseModel):
    min: float
    max: float


class Percentiles(BaseModel):
    p05: float
    p95: float


class ModelMetrics(BaseModel):
    macro_f1: float
    accuracy: float
    cv_macro_f1_mean: float
    cv_macro_f1_std: float


class ModelMetadata(BaseModel):
    """Contents of ``metadata.json`` written by the training pipeline."""

    name: str = Field(min_length=1)
    version: str = Field(min_length=1)
    data_label: Literal["DEMO_PUBLIC_DATASET", "SYNTHETIC_TEST_DATA"]
    features: list[str]
    classes: list[str] = Field(min_length=1)
    feature_ranges: dict[str, FeatureRange]
    class_feature_percentiles: dict[str, dict[str, Percentiles]]
    metrics: ModelMetrics
    trained_at: str
    dataset_sha256: str
    model_sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    library_versions: dict[str, str]
    random_seed: int


class ModelInfoResponse(BaseModel):
    name: str
    version: str
    sha256: str
    data_label: str
    features: list[str]
    classes: list[str]
    feature_ranges: dict[str, FeatureRange]
    metrics: ModelMetrics
    trained_at: str
