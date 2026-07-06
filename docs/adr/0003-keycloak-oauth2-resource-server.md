# ADR 0003: Keycloak And OAuth2 Resource Server

## Status

Accepted

## Context

The prototype uses custom JWT handling. For a bank-facing portfolio, authentication and authorization should use standard, well-supported security mechanisms instead of custom token parsing.

## Decision

Use Spring Security OAuth2 Resource Server for API authentication and Keycloak in Docker Compose as the local identity provider.

Roles and scopes should be mapped into Spring Security authorities. Application code should use a current-user abstraction rather than reading raw token details throughout the codebase.

## Consequences

- Token validation is handled by Spring Security instead of custom code.
- Local demos can use realistic OAuth2/OIDC flows.
- Authorization can combine role/scope checks with object ownership checks.
- Tests should cover unauthenticated access, role restrictions, scope restrictions, and cross-customer access attempts.

## Alternatives Considered

- Custom JWT utilities: rejected because they increase security risk and are weaker in a bank technical review.
- Spring Authorization Server: deferred because running Keycloak is simpler for local identity-provider realism.
- Mock-only authentication: rejected for the main demo because it hides an important security integration.
