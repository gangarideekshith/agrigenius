# ADR-0001: Start with a modular monolith

- Status: Proposed
- Context: AgriGenius has multiple domains, but the initial team and product are still establishing interfaces.
- Decision: Start with a feature-based Spring Boot modular monolith. Keep specialized Python ML inference behind a private service boundary.
- Alternatives: Separate microservices for every domain from day one.
- Consequences: Simpler local development, transactions and deployment initially; module boundaries must remain disciplined so extraction remains possible.
