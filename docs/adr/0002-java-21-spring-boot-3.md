# ADR 0002: Java 21 And Spring Boot 3

## Status

Accepted

## Context

The current project targets a newer Java version than many enterprise teams would standardize on. The portfolio should look realistic for a bank engineering environment and should be easy for reviewers to build locally.

## Decision

Use Java 21 LTS and Spring Boot 3.x.

Java 21 provides a long-term-support baseline with modern language/runtime features. Spring Boot 3.x provides the current Jakarta-based Spring ecosystem, including Spring Security, Spring Data JPA, validation, observability, and testing support.

## Consequences

- The project aligns with common enterprise upgrade paths.
- CI and local development can use a stable LTS JDK.
- Dependency choices should remain compatible with Spring Boot 3.x.
- The build should fail fast if run on an unsupported Java version.

## Alternatives Considered

- Java 24/25: rejected for the portfolio baseline because it is less conservative for bank-style enterprise environments.
- Older Java LTS versions: rejected because Java 21 is a stronger modern baseline while still being enterprise-realistic.
