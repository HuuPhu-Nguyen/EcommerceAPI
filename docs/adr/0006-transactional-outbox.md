# ADR 0006: Transactional Outbox

## Status

Accepted

## Context

Some workflows need reliable asynchronous events after database changes commit. For example, stock changes should be pushed to product viewers, but the system must not lose the event if the API process crashes after reserving inventory.

## Decision

Use a transactional outbox table for important asynchronous events.

Business use cases write outbox records in the same database transaction as the business change. A separate processor publishes pending events and records processing state, retries, and failures.

## Consequences

- Events are not lost when the business transaction commits.
- Stock updates can be delivered through SSE initially.
- The production scaling path can replace the in-memory broadcaster with Redis Pub/Sub or Kafka.
- Outbox lag and failures should be observable through metrics and logs.

## Alternatives Considered

- Publish directly from the request thread after commit: rejected because crashes can lose events.
- Publish before commit: rejected because consumers could observe events for rolled-back data.
- Introduce Kafka immediately: deferred because the portfolio MVP should stay runnable and focused.
