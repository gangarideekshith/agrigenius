# AgriGenius Implementation Plan

## Milestone 0 – Plan and repository setup
- Confirm product scope and three phases.
- Inspect local OS/tool versions and existing Git repository.
- Create GitHub repository and clone/initialize locally.
- Add `README.md`, `CLAUDE.md`, `.gitignore`, docs skeleton and `.env.example`.
- Agree on ports, local database strategy, Java/Node/Python versions.
- Produce architecture diagram, ERD draft, API contract outline and ADRs.
- Do not write feature code until the user approves the plan.

## Milestone 1 – Phase 1 foundation
- Create Angular and Spring Boot applications.
- Establish PostgreSQL connection and Flyway baseline.
- Add health endpoints, global errors, logging, OpenAPI and test scaffolding.
- Add CI build/test workflow after local commands are stable.

## Milestone 2 – Identity, farmer and farms
- Define users, roles, farmer profile, farms and farm locations.
- Implement registration/login, secure password hashing, token lifecycle and RBAC.
- Implement profile and farm CRUD with ownership checks.
- Integrate Angular forms, guards, interceptors and farmer dashboard shell.
- Add backend/frontend/security tests.

## Milestone 3 – Crop recommendation
- Confirm dataset provenance, licensing, features and units.
- Build preprocessing/training/evaluation pipeline and baseline.
- Implement private FastAPI inference contract.
- Implement Spring Boot orchestration, persistence and recommendation history.
- Build Angular input/results UI with agronomic suitability distinct from economics.
- Add tests for validation, inference contract, model metrics and failure handling.

## Milestone 4 – Weather and alerts
- Select data providers and document rate limits/coverage.
- Implement farm-coordinate forecast retrieval, caching/storage and scheduled refresh.
- Implement threshold configuration, deduplication and notification history.
- Integrate push notifications only after secure credentials/configuration are available.
- Show source and forecast period in UI.

## Milestone 5 – Agriculture chatbot
- Select LLM provider and vector store after cost/privacy review.
- Build verified knowledge ingestion and retrieval evaluation.
- Add Spring AI chat, session memory and authorized tool calling.
- Ensure tool access checks user and farm ownership.
- Add source/uncertainty behavior and tests preventing invented live data.

## Phase 1 exit gate
- All four Phase 1 modules work end-to-end.
- Build and automated tests pass.
- API/security checks and documented manual smoke tests pass.
- Setup instructions are reproducible.
- User reviews and approves Phase 1 before Phase 2 begins.

## Phase 2
Implement the six Phase 2 modules in small issues with schema/API/UI/tests/docs per module. Reuse approved Phase 1 boundaries. Obtain user approval before beginning this phase.

## Phase 3
Implement government services, marketplace, admin and expert workflows; then perform integration, security, performance, accessibility, backup/restore, deployment and operational readiness. Obtain user approval before beginning this phase.

## Per-feature delivery loop
1. Create an issue with goal, scope, non-goals and acceptance criteria.
2. Inspect existing architecture and tests.
3. Plan the implementation and identify schema/API/security impact.
4. Obtain approval for broad/high-impact work.
5. Implement in a focused branch.
6. Run targeted tests, full relevant build and manual smoke test.
7. Update docs and project status.
8. Review diff and commit.
9. Close issue only when acceptance criteria are met.

## Workflows
- **Single-feature workflow:** default for one cohesive feature; one Claude Code session, one issue, one branch.
- **Issue-based workflow:** use GitHub issues for backlog, acceptance criteria, priorities and review history.
- **Multi-agent workflow:** only for parallel, independent tasks after interfaces are stable. Use separate Git worktrees/branches; never let agents edit the same files concurrently. Integrate and test centrally.
