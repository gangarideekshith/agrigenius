# ADR-0005: Crop recommendation model, artifact format and dataset

- Status: Accepted. Dataset licence confirmed: **Apache 2.0** (Kaggle metadata for `atharvaingle/crop-recommendation-dataset`, checked 2026-11-17). The licence is the uploader's declaration; the data's original collection is not documented.
- Date: 2026-11-17
- Deciders: project owner (crop recommendation plan, Q1, Q3, Q5, Q8)

## Context

PROJECT_SPEC §3.2 asks for the top five crop candidates with a suitability score and explanation, separated from economics, and for every prediction to record the model version and input snapshot. No dataset or trained model exists yet, and CLAUDE.md forbids unverified data and invented scientific advice.

## Decision

### Dataset (Q1)

- Use the public **"Crop Recommendation" dataset**: 2,200 rows, 22 crops, columns `N, P, K, temperature, humidity, ph, rainfall, label`.
- **Use it only once its licence is confirmed** to allow our use. Until then it is not downloaded or committed; tests use a small synthetic fixture dataset that is labelled as synthetic.
- Every artifact and API response is labelled `DEMO_PUBLIC_DATASET`, and responses carry an advisory to confirm with a local agriculture officer or KVK.
- If the raw CSV may not be redistributed, it stays in the gitignored `ml-service/data/local/`; the model card records the source, download date and SHA-256.

### Inputs (Q3)

The model uses seven features: nitrogen, phosphorus, potassium (kg/ha assumed), temperature (°C), humidity (%), pH and rainfall (mm). Other inputs from the spec (soil type, moisture, season, irrigation, previous crop, location) are captured and stored for traceability but not used by this model version; responses list `featuresUsedByModel`.

### Model selection

- Compare a Random Forest and XGBoost with **stratified 5-fold cross-validation**, choosing by **macro-F1**; keep a stratified 20 % hold-out set for final metrics; fixed seed (42).
- Calibrate probabilities with **sigmoid calibration** (`CalibratedClassifierCV`) so they can be used as confidence.
- Return the **top 5** classes by calibrated probability.
- Explanations are data-derived: for each candidate, compare the farmer's value with that crop's 5th–95th percentile in the training data. No free-text agronomic claims.

### Confidence (Q8)

- Per candidate: **HIGH ≥ 0.60, MEDIUM ≥ 0.30, LOW < 0.30** (configurable in the backend).
- A recommendation is `LOW_CONFIDENCE` when the top candidate is LOW or any input lies outside the training range (`INPUT_OUTSIDE_TRAINING_RANGE`), and then carries an expert-escalation advisory.

### Economics (Q5)

Not provided. Each candidate returns `economics: { available: false, reason: "No verified market or cost data yet." }`. Profitability can never override agronomic unsuitability.

### Artifact format

- `model.joblib` plus `metadata.json` (features, units, classes, per-class percentiles, training ranges, metrics, confusion matrix, dataset SHA-256, model SHA-256, library versions, data label, trained-at time) under `ml-service/models/crop_recommendation/<version>/`.
- **The ML service verifies the model's SHA-256 against `metadata.json` before loading** and refuses to start serving predictions on a mismatch, because joblib/pickle files can execute arbitrary code when loaded. Only our own artifacts are ever loaded.
- **Not committed.** A calibrated forest is several MB and a joblib file is executable on load, so the artifact is generated from the committed dataset with the deterministic training command (locally and in CI) and `ml-service/models/` is gitignored. The dataset CSV (150 KB, Apache 2.0) is committed with a NOTICE and its SHA-256.

## Alternatives considered

- **Rules-only recommender** from published agronomy tables: transparent, but needs sourced, cited ranges we don't have yet.
- **Deep learning:** unjustified for seven tabular features.
- **No calibration:** raw tree-ensemble probabilities are poorly calibrated and would mislead confidence levels.

## Consequences and limitations

- The dataset is perfectly balanced (100 rows per crop), its provenance and units are unclear, and its reported accuracy is optimistic; results are demo output, not field-validated advice.
- Inputs are typed in by hand until the weather (M4) and farm (M2) modules exist.
- Retrain when a licensed, Indian-sourced dataset is available; bump the model version, and keep old results traceable through `model_version` and `model_sha256`.
