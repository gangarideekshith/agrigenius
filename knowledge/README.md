# Agricultural knowledge corpus

Reviewed source documents for the chatbot's retrieval-augmented generation (ADR-0006, ADR-0007). Ingestion reads `manifest.yaml` in this folder; a file not listed there is never ingested.

## `manifest.yaml`

```yaml
documents:
  - title: "Rice: Integrated Pest Management"      # required
    publisher: "Example Agricultural University"   # required
    url: "https://example.org/rice-ipm"            # required, canonical source
    licence: "CC-BY-4.0"                           # required, must be on the allowlist
    language: "en"                                 # en | hi | te | kn | ta
    crops: ["rice"]                                # crop codes from the crops table
    topics: ["pests", "diseases"]
    file: "rice-ipm.md"                            # path relative to this folder
    sha256: "<64 hex characters>"                  # checksum of the file; ingestion refuses a mismatch
```

## Licence allowlist

- Public domain
- `CC-BY-4.0`
- `CC-BY-SA-4.0`
- `GODL-India` (Government Open Data Licence – India)
- FAO material under `CC-BY-NC-SA-3.0-IGO`, **only while AgriGenius stays non-commercial**

Any other licence is refused at ingestion.

## Synthetic test corpus

Until the owner approves real sources (decision C3), tests use a small corpus that is clearly labelled **SYNTHETIC — NOT AGRICULTURAL ADVICE**. It is never ingested into a real database and never served as advice.

## Review process

1. Propose a document with its manifest entry (source, licence, language, crops, topics).
2. A reviewer checks the licence, the publisher's authority and that the content is current.
3. Record the SHA-256, then commit the file and its manifest entry together.
4. Ingest. Retire outdated documents by setting their status to `RETIRED`; don't delete their history.


## Licence tokens accepted by the ingestion validator

`PUBLIC-DOMAIN`, `CC-BY-4.0`, `CC-BY-SA-4.0`, `GODL-India`, `CC-BY-NC-SA-3.0-IGO` (non-commercial; allowed only while `agrigenius.knowledge.allow-non-commercial-licences=true`). `SYNTHETIC-TEST-ONLY` is reserved for test documents with `data_label: SYNTHETIC_TEST_DATA` and is rejected unless `agrigenius.knowledge.allow-synthetic=true` (tests only). `data_label` defaults to `VERIFIED_SOURCE`. Supported formats: `.md`, `.txt`.

Run: `EMBEDDING_PROVIDER=e5-onnx VECTOR_STORE_TYPE=pgvector ./mvnw spring-boot:run -Dspring-boot.run.profiles=local,ingest` (no web server is started). Candidate sources: `docs/knowledge/CANDIDATE_SOURCES.md` (owner decision C3).
