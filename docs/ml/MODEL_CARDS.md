# ML Model Cards and Data Register

No model should be described as production-ready until its data, evaluation and inference behavior are documented.

For each model record:
- Model name, task, owner and version.
- Dataset name, source URL/provider, license, collection date and known bias/limitations.
- Feature names, units, allowed ranges and preprocessing.
- Train/validation/test methodology and leakage controls.
- Baseline, candidate algorithms and evaluation metrics.
- Confusion matrix or regression metrics as appropriate.
- Calibration/confidence behavior and abstention threshold.
- Artifact checksum/location and inference API version.
- Known limitations, monitoring and retraining trigger.

## Planned models
| Capability | Candidate approach | Status |
|---|---|---|
| Crop recommendation | Random Forest vs XGBoost, calibrated (ADR-0005) | In development (demo) |
| Disease image classification | CNN / MobileNetV2 / EfficientNet candidate | Not started |
| Market forecasting | Time-series baseline after data assessment | Not started |

Use documented, licensed, relevant datasets. Separate agronomic suitability from profitability. Never treat a sample dataset as live ground truth.

## Crop recommendation model

| Item | Value |
|---|---|
| Name | `crop-recommendation-rf` (final name follows the selected algorithm) |
| Task | Multi-class classification: rank the 5 most suitable crops for given soil and climate inputs |
| Owner | AgriGenius ML service (`ml-service/`) |
| Version | 1.0.0 (trained 2026-11-17T00:41:17Z, seed 42) |
| Status | **In development (demo)**: not validated for field use |
| Decisions | ADR-0005 (model and dataset), ADR-0004 (internal API key) |
| Inference API | `POST /v1/crop-recommendation/predict`, `GET /v1/crop-recommendation/model` (contract v1, fixtures in `docs/api/contracts/crop-recommendation/`) |

### Dataset

| Item | Value |
|---|---|
| Name | "Crop Recommendation" dataset (public) |
| Shape | 2,200 rows; 22 crops; columns `N, P, K, temperature, humidity, ph, rainfall, label` |
| Source | Public dataset repository (exact URL and download date recorded when obtained) |
| Licence | **Apache 2.0**, as declared by the author on Kaggle (checked 2026-11-17). File obtained from the author's GitHub repository `Gladiator07/Harvestify` (repository code is GPL-3.0); not byte-verified against the Kaggle file. Committed as `ml-service/data/raw/Crop_recommendation.csv` with `NOTICE.md`. |
| Data label | `DEMO_PUBLIC_DATASET` (carried in every artifact and API response) |
| Storage | `ml-service/data/raw/` only if redistribution is allowed; otherwise gitignored `ml-service/data/local/` with SHA-256 recorded here |
| Known bias and limitations | Perfectly balanced (100 rows per crop), unlike real farm distributions; provenance and collection method unclear; units not documented (N, P, K assumed kg/ha); rainfall/temperature are aggregates, not field measurements; not specific to Indian districts or seasons |
| Tests | A small **synthetic** fixture dataset (clearly labelled) is used by unit tests; it is never used to train a released model |

### Features

| Feature | Unit (assumed) | Plausible input range (API) | Preprocessing |
|---|---|---|---|
| `nitrogen` | kg/ha | 0–300 | none (tree models) |
| `phosphorus` | kg/ha | 0–300 | none |
| `potassium` | kg/ha | 0–300 | none |
| `temperature_c` | °C | −5 to 55 | none |
| `humidity_pct` | % | 0–100 | none |
| `ph` | pH | 3.0–10.0 | none |
| `rainfall_mm` | mm | 0–5,000 | none |

Inputs outside the **training** min–max range produce the `INPUT_OUTSIDE_TRAINING_RANGE` warning. Soil type, moisture, season, irrigation, previous crop and location are stored by the backend but not used by this model.

### Training and evaluation

- Stratified 80/20 train/hold-out split, fixed seed 42; stratified 5-fold cross-validation on the training split.
- Candidates: Random Forest and XGBoost; selection by mean cross-validation **macro-F1**.
- Leakage controls: hold-out set never used for selection or calibration; duplicate rows checked across splits; per-class percentiles computed on the training split only.
- Calibration: sigmoid (`CalibratedClassifierCV`) fitted within cross-validation.
- Reproducibility: same seed and data give the same metrics and metadata (tested).

Measured on 2026-11-17 with `python -m training.train_crop_model --csv data/raw/Crop_recommendation.csv --out models/crop_recommendation --version 1.0.0 --data-label DEMO_PUBLIC_DATASET --seed 42 --min-macro-f1 0.90` (scikit-learn 1.9.1, XGBoost 3.4.1, NumPy 2.5.5, pandas 3.1.1). Training split 1,760 rows, hold-out 440 rows (stratified 80/20).

| Metric | Value |
|---|---|
| CV macro-F1 (mean ± std) | 0.9937 ± 0.0056 (5-fold, training split); XGBoost 0.9920 |
| Hold-out macro-F1 | 0.9932 |
| Hold-out accuracy | 0.9932 |
| Interpretation | Near-perfect scores reflect a small, balanced, curated dataset with well-separated classes. They are **not** evidence of field accuracy; expect much lower performance on real farm measurements. |
| Confusion matrix | Full matrix in `metadata.json`. 3 of 440 hold-out rows misclassified: blackgram → maize, lentil → mothbeans, rice → jute (1 each). Lowest per-class F1: blackgram, lentil, rice 0.974; jute, maize 0.976. |
| Selected algorithm | Random Forest (300 trees), sigmoid-calibrated (CalibratedClassifierCV, cv=5) |

### Confidence and abstention

- Calibrated probability per candidate; `suitabilityScore` = round(probability × 100).
- Confidence: **HIGH ≥ 0.60, MEDIUM ≥ 0.30, LOW < 0.30** (configurable in the backend).
- The recommendation is `LOW_CONFIDENCE` (with an advisory to consult a local agriculture officer or KVK) when the top candidate is LOW or any input is outside the training range.
- Economics (yield, cost, revenue, profit) are **not provided**: no verified data.

### Artifact

| Item | Value |
|---|---|
| Location | `ml-service/models/crop_recommendation/1.0.0/model.joblib` + `metadata.json` |
| Model SHA-256 | `945ff6e8dae70e83f23a7eaa1d79c21799d58046b6b0b955e8ec4661c15f9003` (this local build; a retrain produces a new artifact checksum, which is recorded per prediction). Artifact is ~51 MB and is not committed. |
| Dataset SHA-256 | `808e1d84a544647f1ab2fdae04f434b1842692d6be09eee18a5c2d43eb78d77c` |
| Integrity | SHA-256 verified against `metadata.json` before loading; the service refuses a mismatched artifact (joblib files can execute code) |

### Monitoring and retraining

- Every prediction stores `model_name`, `model_version`, `model_sha256`, the input snapshot and timestamps (backend table `crop_recommendation_requests`).
- Track the share of `LOW_CONFIDENCE` and `INPUT_OUTSIDE_TRAINING_RANGE` results; a sustained rise means farmers' inputs differ from the training data.
- Retrain and bump the version when a licensed, Indian-sourced dataset becomes available, when library versions change materially, or when expert review finds systematic errors.
