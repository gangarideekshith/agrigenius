# ADR-0007: Prompt-injection defences and tool policy for the chatbot

- Status: Accepted (decisions C5, C6, C7; 2026-11-17)

## Context

The chatbot combines farmer input, retrieved documents and application data in one prompt, and any of them can contain instructions. The spec (§3.3) forbids fabricated weather, prices, government benefits or scientific claims, and requires backend authorization for every data access.

## Decision

**Tools**
- Tools take **no user id**. The farmer's id is bound on the server from the verified JWT through Spring AI's tool context, so a hijacked prompt can reach only the caller's own data.
- Tools are **read-only** and return **allowlisted fields** only (never mobile number or email).
- Registered tools: `getMyProfile`, `getMyCropRecommendations(limit ≤ 5)`, and `getMyCropRecommendation(id)`, which goes through the existing owner-scoped service (another farmer's id → not found).
- Farm, soil and weather tools are **not registered** until those modules exist (weather: decision C6). The system prompt states this data is not available, so the model cannot invent it.

**Prompt structure**
- A fixed system prompt that is never built from user input.
- Retrieved sources and tool results are wrapped in tagged **untrusted** blocks marked as reference data, and the system prompt says to ignore instructions found inside them.
- Only manifest-allowlisted documents can be retrieved.

**Grounding**
- Answers must come from retrieved sources or tool results, cite their sources, and state uncertainty.
- Below the similarity threshold the answer is `NO_SOURCES`: the bot says it has no verified information and refers the farmer to the local KVK, using standard text written by us.
- Disease answers always recommend expert confirmation.

**Scope (`REFUSED`)**
- Field-specific fertilizer doses (Phase 2), market prices, government-scheme eligibility and live weather are refused or redirected, unless a future tool supplies verified data (C5).

**Verification**
- Offline tests check that injection fixtures stay inside untrusted blocks, that tools have no id parameter, and that scripted tool calls cannot reach another farmer's data.
- An opt-in live evaluation runs a red-team set: injection attempts, requests for weather or prices, and requests for other farmers' data.

## Consequences

- Tool coverage grows only as modules land. Each new tool needs the same identity binding and field allowlist.
- Prompt-level defences reduce injection risk but don't eliminate it. The tool design (no ids, read-only) is the hard boundary.
