# AgriGenius – Claude Code Project Instructions

This file is the concise operating manual for Claude Code. The detailed product and engineering specification is in `docs/PROJECT_SPEC.md`; execution order is in `docs/IMPLEMENTATION_PLAN.md`; current truth is in `docs/PROJECT_STATUS.md`.

## Mission

Build AgriGenius as a complete, secure, maintainable agriculture platform. Do not stop at mock screens or disconnected CRUD. Work in small verified increments and keep the repository runnable.

## Read first

At the beginning of a new task, read:
1. `docs/PROJECT_SPEC.md`
2. `docs/IMPLEMENTATION_PLAN.md`
3. `docs/PROJECT_STATUS.md`
4. `docs/engineering/DEVELOPMENT.md`
5. The relevant architecture/API/database/ML documents for the task.

Inspect the actual repository before assuming files, frameworks, versions, scripts, or implementation state.

## Product decisions and phases

Follow the three phases exactly as defined in `docs/PROJECT_SPEC.md`:
- Phase 1: farmer registration/profile/farm management; AI crop recommendation; AI agriculture chatbot; weather forecast and smart alerts.
- Phase 2: plant disease detection; soil health/fertilizer recommendation; smart irrigation; crop lifecycle/tasks; market price intelligence/prediction; farm expense/financial management.
- Phase 3: government schemes/services; farmer–buyer marketplace; admin dashboard; agricultural expert dashboard; final integration/security/testing/deployment.

Do not start Phase 2 or Phase 3 without explicit user approval. Do not silently drop, merge, or reorder product requirements.

## Approved technology direction

- Angular + TypeScript frontend.
- Java 21 baseline and compatible Spring Boot 3.x backend, Maven.
- PostgreSQL, Spring Data JPA/Hibernate and Flyway.
- Spring Security, secure JWT lifecycle, BCrypt and role-based access control.
- Python/FastAPI only for specialized ML services.
- Spring AI for the chatbot and RAG.
- Redis, Firebase, WebSockets, MQTT/ESP32 and cloud storage only when the requirement justifies them.

Check the repository and machine versions before setup. Do not replace the stack or upgrade major versions without explaining the impact and obtaining approval.

## Architecture boundaries

- Angular communicates with Spring Boot APIs only.
- Spring Boot owns business rules, identity, authorization, persistence and orchestration.
- Python ML endpoints are private backend-to-backend services; never expose them or secrets to the browser.
- Start with a modular monolith; avoid premature microservices.
- Use feature-based modules, DTOs, validation, service/repository separation, transactions, centralized errors, structured logs and pagination.
- Enforce resource ownership in addition to role checks.
- Keep external-provider integrations behind interfaces/adapters where practical.

## Required working method

For every task that changes more than a trivial single file:

1. **Understand:** inspect relevant code, docs, git status and existing tests.
2. **Plan:** state objective, assumptions, files to change, API/schema impact, risks and test plan. Use plan mode for multi-file or architectural work. Do not edit until the user approves the plan when the task is broad, irreversible, security-sensitive, or changes architecture.
3. **Issue:** define a small task with observable acceptance criteria. Use GitHub issues if configured; otherwise track it in `docs/PROJECT_STATUS.md`.
4. **Implement:** change only what the approved task requires. Do not rewrite unrelated code.
5. **Verify:** run formatting, compilation, unit/integration tests and relevant manual checks. Fix regressions before calling it complete.
6. **Document:** update relevant docs, API contracts, schema notes, setup instructions and `docs/PROJECT_STATUS.md`.
7. **Review:** inspect `git diff`, check for secrets, TODO placeholders, dead links, unsafe defaults and incomplete flows.
8. **Report:** list changed files, commands run, test outcomes, remaining risks and the next recommended issue.

If a command fails, diagnose it; do not hide or ignore the failure. Never claim a test passed unless it actually ran successfully.

## Automation boundaries and permissions

You may inspect files and run safe local development commands. Ask before destructive operations, deleting/replacing user data, changing global machine configuration, installing system-wide software, creating paid resources, publishing releases, deploying, modifying production data, or pushing externally when the user has not explicitly authorized that action. Never bypass Claude Code permission prompts or security controls. Do not place credentials in source code, shell history, logs or documentation.

Create `.env.example` with variable names and safe placeholders only. Ensure real `.env` files and credential files are gitignored.

## Data, AI and reliability

- Do not fabricate live weather, market prices, government scheme eligibility or scientific advice.
- Show source and timestamp/forecast period for external data.
- Clearly label demo/sample/synthetic data.
- Separate crop agronomic suitability from profitability.
- Provide uncertainty and expert escalation for disease classification.
- Ground chatbot answers in retrieved trusted sources and authorized app data. Do not let the LLM bypass authorization or invent live facts.
- Document dataset provenance, licensing, preprocessing, validation, metrics and model limitations.

## UI requirements

Use a farmer-friendly, responsive, accessible design. Keep language simple, provide clear loading/empty/error/success states, validate forms, support mobile layouts, and avoid decorative controls that do nothing. Use dark green, leaf green and white as the design direction. Regional-language support is part of the product scope.

## Testing expectations

Backend: JUnit 5, Mockito and Spring Boot integration tests.
Frontend: Angular component and service tests.
API/security: validation, role access, ownership, auth lifecycle and upload validation.
ML: preprocessing, inference contract, reproducibility and model metrics.
End-to-end: critical farmer journeys.

Add tests with each feature. Do not defer all testing to the end.

## Documentation that must stay current

Update the applicable documents as work proceeds:
- `docs/PROJECT_STATUS.md` – phase, completed items, in-progress items, blockers, next task.
- `docs/engineering/DEVELOPMENT.md` – verified setup, environment variables, commands, ports and troubleshooting.
- `docs/architecture/SYSTEM_ARCHITECTURE.md` – components and trust boundaries.
- `docs/database/ERD.md` and migrations – schema and relationships.
- `docs/api/API_CONTRACT.md` – endpoints, DTOs, auth and error conventions.
- `docs/ml/MODEL_CARDS.md` – datasets, model versions, metrics, limitations.
- ADRs under `docs/architecture/decisions/` – significant technical decisions.

Do not write that a feature is complete unless implementation and acceptance tests support that status.

## Completion definition

A task is complete only when:
- Acceptance criteria are met.
- Relevant tests/build pass.
- No known critical security issue is introduced.
- API/schema changes are documented and migrations are included where needed.
- UI has loading, empty, error and success states where applicable.
- Documentation and project status are updated.
- `git diff` has been reviewed.

## First action in a new repository

Do not generate the whole application in one shot. Inspect the current repository, tools and Git state. Then produce a concise environment report and a Phase 1 architecture/setup plan, including system diagram, ERD draft, API boundaries, folder structure, dependency/version choices and risks. Wait for approval before creating the application code.
