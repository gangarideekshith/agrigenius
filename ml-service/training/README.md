# Crop recommendation training

Offline pipeline that produces the versioned artifact the ML service loads. It is never run by the web app.

## Dataset

Expected CSV layout (the public "Crop Recommendation" dataset): `N,P,K,temperature,humidity,ph,rainfall,label`.

**Licence: pending approval.** Do not download or commit the dataset until its licence has been confirmed (see `docs/ml/MODEL_CARDS.md`). Until then, keep any copy outside Git, for example `data/local/crop_recommendation.csv` (gitignored), and record its SHA-256 in the model card.

For development and tests, `training.synthetic.generate()` produces **synthetic** data (invented Gaussian clusters). Artifacts trained on it must use `--data-label SYNTHETIC_TEST_DATA` and must never be shown to farmers.

## Train (from `ml-service/`)

```bash
.venv/Scripts/python.exe -m training.train_crop_model --csv data/local/crop_recommendation.csv --out models/crop_recommendation --version 1.0.0 --data-label DEMO_PUBLIC_DATASET --seed 42 --min-macro-f1 0.90
```

Steps:
1. Validate the CSV: required columns, numeric features, no missing values, non-empty labels, at least 10 unique rows per class.
2. Drop exact duplicate rows **before** splitting, so no row is in both train and test.
3. Stratified 80/20 split (seeded).
4. Compare a Random Forest (300 trees) and XGBoost by 5-fold stratified CV macro-F1 on the training split. A tie goes to the Random Forest.
5. Calibrate the winner (`CalibratedClassifierCV`, sigmoid, 5-fold) so probabilities can be used as confidence.
6. Evaluate on the holdout. If macro-F1 is below `--min-macro-f1`, exit with code 1 and write nothing.
7. Write `models/crop_recommendation/<version>/model.joblib` and `metadata.json` atomically (staging directory, then rename). An existing version is never overwritten without `--force`.

## Artifact

- `model.joblib`: calibrated classifier. Input is a 2-D float array with columns in `metadata.features` order; `classes_` are the crop label strings (sorted); `predict_proba` follows `classes_`.
- `metadata.json`: name, version, data_label, features, classes, feature_ranges, class_feature_percentiles (p05/p95 per crop and feature, from the training split), metrics (holdout macro-F1, accuracy, CV mean/std, per-class F1, confusion matrix), candidate CV scores, trained_at, dataset_sha256, model_sha256, library versions, seed, row counts.

**Loading safely:** joblib files can execute code when loaded. The service must compare `sha256(model.joblib)` with `metadata.model_sha256` (and the pinned value it expects) before calling `joblib.load`, and only load artifacts produced by this pipeline. If XGBoost wins, the pickle references `training.artifact.EncodedLabelClassifier`, so the `training` package must be importable (run the service from `ml-service/`).

## Determinism

The same CSV, seed and library versions give identical metrics, percentiles and `predict_proba` outputs (all estimators use `n_jobs=1` and fixed `random_state`). The `model.joblib` bytes, and so `model_sha256`, can still differ between runs and library versions; verify integrity with the checksum written for that artifact, not across retrains.
