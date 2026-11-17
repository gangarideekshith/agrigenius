# Crop recommendation: internal ML contract fixtures

Shared examples of the private Spring Boot → ML service contract (`POST /v1/crop-recommendation/predict`,
`GET /v1/crop-recommendation/model`). **Both** test suites load these files:

- `ml-service` pytest: responses produced by the service must validate against the same shapes,
  and request fixtures must be accepted.
- `backend` JUnit: the RestClient adapter must parse every response fixture and send requests
  that match `predict-request.json` field-for-field.

Rules: snake_case JSON; every request carries the `X-Internal-Api-Key` header (not shown);
probabilities are 0–1 and sorted descending; `crop` values are the model's class labels and must
exist in the backend `crops` table. Change a fixture only together with both implementations.
