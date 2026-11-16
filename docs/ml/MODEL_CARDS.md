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
| Crop recommendation | Random Forest / XGBoost baseline | Not started |
| Disease image classification | CNN / MobileNetV2 / EfficientNet candidate | Not started |
| Market forecasting | Time-series baseline after data assessment | Not started |

Use documented, licensed, relevant datasets. Separate agronomic suitability from profitability. Never treat a sample dataset as live ground truth.
