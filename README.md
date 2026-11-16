# AgriGenius – AI-Powered Smart Agriculture Management System

AgriGenius is a full-stack agriculture platform intended to help farmers make informed decisions using farm records, soil and weather information, machine learning, verified agricultural knowledge, and market intelligence.

**Project stage:** Planning / initial setup. Update this status as working features are implemented and tested.

## Product goal

Build a usable, secure, responsive agriculture platform—not a disconnected UI demo. The primary application backend is Java Spring Boot, the frontend is Angular, PostgreSQL is the main database, and Python is reserved for specialized ML inference/training services.

## Roadmap

### Phase 1 – Core farmer platform
1. Farmer registration, authentication, profile and multi-farm management.
2. AI-based crop recommendation with separate agronomic suitability and economic estimates.
3. Agriculture chatbot using Spring AI, RAG, verified sources and authorized application tools.
4. Farm-location weather forecasts, scheduled refresh, deduplicated alerts and notification history.

### Phase 2 – Advanced intelligence and farm management
1. Plant disease detection.
2. Soil health analysis and fertilizer recommendation.
3. Smart irrigation management.
4. Crop lifecycle and task management.
5. Market price intelligence and prediction.
6. Farm expense and financial management.

### Phase 3 – Complete agriculture ecosystem
1. Government schemes and agricultural services.
2. Farmer–buyer marketplace.
3. Admin dashboard.
4. Agricultural expert dashboard.
5. Integration, security hardening, testing, documentation and production deployment.

Do not start a later phase without explicit approval.

## Technology direction

- Frontend: Angular + TypeScript, standalone components, lazy routes, Reactive Forms, HttpClient.
- Backend: Java 21 baseline, Spring Boot 3.x, Spring MVC, Spring Data JPA, Spring Security, JWT, Maven.
- Database: PostgreSQL with Flyway migrations; Redis only where justified.
- ML: Python + FastAPI for specialized services; scikit-learn, Random Forest, XGBoost, TensorFlow/PyTorch and OpenCV where suitable.
- AI assistant: Spring AI, RAG, chat memory, tool calling and a vector store.
- UI: responsive farmer-first design, dark green/leaf green/white, charts and maps.
- Delivery: Git/GitHub, tests, Docker and deployment documentation.

Verify exact versions and compatibility during setup; do not silently upgrade or change the approved stack.

## Architecture rule

Angular calls Spring Boot. Spring Boot owns authentication, authorization, business rules and persistence. It calls Python ML services server-to-server. Never expose ML service URLs, provider secrets or privileged API credentials in browser code.

Start as a modular monolith. Split services only with a documented reason.

## Repository map

See `docs/PROJECT_SPEC.md`, `docs/IMPLEMENTATION_PLAN.md`, and `CLAUDE.md`. Architecture decisions belong in `docs/architecture/decisions/`. API contracts belong in `docs/api/`. Database design and migrations belong in `docs/database/`. ML data/model cards belong in `docs/ml/`.

## Local setup

Prerequisites depend on the modules being run: Git, Node/npm and Angular CLI, Java 21, Maven (or Maven Wrapper), PostgreSQL, Python 3 and pip; Docker Desktop is optional for local development.

Check installed versions before creating projects:

```powershell
git --version
node --version
npm --version
ng version
java -version
mvn -version
python --version
docker --version
```

Use `.env.example` as a template. Create local environment files from it and never commit secrets. The actual verified setup/run commands must be recorded in `docs/engineering/DEVELOPMENT.md` after the first successful setup.

## Development workflow

1. Read `CLAUDE.md` and the linked project docs.
2. Pick one approved, testable issue.
3. Ask Claude Code to plan the change before editing multiple files.
4. Implement only the selected issue and run relevant tests.
5. Update documentation and `docs/PROJECT_STATUS.md`.
6. Review `git diff`, then commit with a meaningful message.
7. Do not claim completion unless tests and acceptance criteria pass.

## Status and contribution

See `docs/PROJECT_STATUS.md` for the current milestone and verified status. Planned functionality is not implemented functionality. Sample data must be labelled. Never fabricate current weather, prices, government benefits or AI predictions.

## License

No license has been selected yet. Add a `LICENSE` file before publishing the repository as open source.
