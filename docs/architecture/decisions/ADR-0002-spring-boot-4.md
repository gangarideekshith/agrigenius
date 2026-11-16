# ADR-0002: Use Spring Boot 4.1 instead of 3.x

- Status: Accepted
- Date: 2026-11-17
- Deciders: project owner (approved in the Spring Boot foundation planning session)

## Context

`CLAUDE.md` and `docs/PROJECT_SPEC.md` specified "Java 21 and compatible Spring Boot 3.x". When the backend foundation was planned, Maven Central showed:

- Spring Boot 3.5.16, the latest 3.x release, published 25 June 2026, with no 3.x release since. Open-source support for Spring Boot 3.x has ended; further fixes require commercial support.
- Spring Boot 4.1.2 published 22 October 2026, on the actively supported 4.x line.

No backend code existed yet, so migration cost was at its lowest.

## Decision

Build the backend on **Spring Boot 4.1.2** with Java 21. Resulting stack: Spring Framework 7.0, Spring Security 7.1, Hibernate 7.4, Flyway 12.4, Jackson 3.1, springdoc-openapi 3.2 (built against Boot 4.1.2), JUnit 6, Testcontainers 2.0, Maven 3.9.16 via the Maven Wrapper. Spring AI 2.0.x is the matching line for the chatbot milestone.

## Alternatives considered

- **Spring Boot 3.5.16** (as originally specified): compatible with Java 21 and the rest of the plan, but no longer receives open-source security fixes. Rejected for a security-sensitive platform; a later forced migration to 4.x would touch Jackson, Security, Hibernate, springdoc and Spring AI with far more code in place.

## Consequences

- `CLAUDE.md` and `docs/PROJECT_SPEC.md` now say Spring Boot 4.x.
- Boot 4 uses modular starters (`spring-boot-starter-webmvc`, `-flyway`, `-restclient`, per-module `-test` starters) and moved packages (for example health contributors are in `org.springframework.boot.health.contributor`). Examples written for Boot 3 need adjusting.
- Jackson 3 (`tools.jackson.*`) is the application JSON library; Jackson 2 remains on the classpath only because springdoc's Swagger models use it.
- Spring Boot 4.1 open-source support is time-limited as well; plan to move to the next 4.x minor release when it ships and is supported by springdoc and Spring AI.
