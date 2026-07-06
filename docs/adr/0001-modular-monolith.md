# ADR 0001: Modular Monolith

## Status

Accepted

## Context

The project is a portfolio backend intended to demonstrate banking-grade engineering judgment: secure APIs, transaction correctness, auditability, and production readiness. A microservice design would add deployment and distributed-systems overhead before the core domain is reliable.

## Decision

Use a modular monolith with pragmatic DDD boundaries.

Core modules will include identity, customer, catalog, checkout, order, payment, ledger, audit, shared, and config. Important business modules should use `api`, `application`, `domain`, and `infrastructure` layers.

## Consequences

- The application remains easy to run locally and review from a fresh clone.
- Database transactions can protect important workflows such as checkout, payment state changes, and ledger posting.
- Module boundaries still make ownership, dependencies, and business rules visible.
- Architecture tests should be added to prevent accidental dependency drift.

## Alternatives Considered

- Microservices: rejected for the portfolio version because it would create operational complexity without improving the first milestone.
- Flat CRUD package structure: rejected because it hides business rules and does not demonstrate bank-relevant design skill.
