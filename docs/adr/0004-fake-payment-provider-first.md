# ADR 0004: Fake Payment Provider First

## Status

Accepted

## Context

The payment workflow is central to the portfolio, but relying on Stripe during tests and demos would make the project harder to run and review. The important engineering skill is the internal payment design: idempotency, state transitions, audit, ledger posting, and safe provider boundaries.

## Decision

Implement a payment provider port and build a fake provider adapter first. Add a Stripe adapter later behind the same interface.

The fake provider should support deterministic success, failure, timeout, duplicate, and webhook scenarios.

## Consequences

- Integration tests can run without external accounts or network calls.
- The domain and application layers remain independent from Stripe SDK classes.
- Provider behavior can be tested under failure and replay scenarios.
- The Stripe adapter can be added later without changing core payment use cases.

## Alternatives Considered

- Stripe-only implementation: rejected because it makes tests and demos dependent on external setup.
- No provider abstraction: rejected because it couples business workflows to infrastructure.
- Mocking Stripe directly in services: rejected because it preserves the wrong dependency direction.
