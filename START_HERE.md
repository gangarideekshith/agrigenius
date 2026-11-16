# AgriGenius Claude Code Starter Pack

This pack applies the PSB workflow (Plan → Setup → Build) from the linked video to AgriGenius.

## What is included
- `CLAUDE.md`: concise persistent project rules and working loop
- `README.md`: GitHub-facing project overview
- `docs/PROJECT_SPEC.md`: product + engineering requirements
- `docs/IMPLEMENTATION_PLAN.md`: milestones and delivery workflows
- `docs/PROJECT_STATUS.md`: current source of truth for progress
- `docs/engineering/DEVELOPMENT.md`: verified setup/run commands
- Architecture, ERD, API and ML documentation starters
- `.env.example`, `.gitignore`
- GitHub issue templates
- Claude Code slash commands: `/plan-feature`, `/implement-issue`, `/review-ready`
- Specialist agent definitions for backend, frontend and ML tasks

## How to use
1. Extract/copy the contents into the root of your existing AgriGenius repository. Merge folders carefully; do not overwrite existing source code.
2. Keep your existing `CLAUDE.md` and `README.md` only if you merge their useful content into these updated versions.
3. Inspect `.gitignore` and `.env.example` before use. Put real secrets only in local ignored files.
4. Open the repository root in VS Code and start Claude Code there.
5. Start with the prompt below. Do not ask Claude to build the entire system in one shot.

## First Claude Code prompt
Read CLAUDE.md and all linked project docs. Inspect the existing repository, Git status and installed development tools. Do not modify application code yet. Compare the current repository against the starter documentation, identify existing files that must be preserved, report missing prerequisites, and produce a verified environment report plus a detailed Phase 1 implementation plan (architecture, ERD, APIs, folder structure, version compatibility, security and test strategy). Ask me to approve the plan before creating or changing application code.

## After approval
Use one small issue at a time. Ask Claude to run `/plan-feature` for planning, then `/implement-issue` after approval, and `/review-ready` before committing. Update docs/PROJECT_STATUS.md after each verified task.

## Important
This starter pack structures Claude Code's work; it does not guarantee unattended completion. Claude Code still needs access to the repository, installed tools, explicit permissions and review. Keep human approval for destructive, paid, external, deployment and production actions. Parallel agents should only work on separate files/issues with stable interfaces; use separate Git worktrees when running concurrent sessions.
