# ADR 0005: Immutable Ledger

## Status

Accepted

## Context

Money movement must be auditable and correct. Updating balances directly would be simple, but it would not demonstrate the level of traceability expected in banking-style systems.

## Decision

Use an immutable double-entry-style ledger.

Payments and refunds create ledger transactions with balanced ledger entries. Ledger entries are append-only. Corrections must use reversing transactions rather than updates or deletes.

Audit events use a single tamper-evident linear hash chain. Writes intentionally serialize on the singleton `audit_hash_chain_state` row lock and use that row's `latest_hash` as the next event's `previous_hash`. This avoids a per-write audit table count while keeping one ordered chain. The database rejects direct updates and deletes on sealed audit events, and the append-only migration refuses to proceed while unhashed audit rows exist. Sharded audit chains are deferred as a separate architecture decision because they trade write throughput for more complex verification semantics.

## Consequences

- Money movement has a durable audit trail.
- Reconciliation can detect missing, orphaned, or unbalanced records.
- Tests can prove every transaction balances per currency.
- Application code and database design must prevent mutation paths for posted ledger entries.
- Application code and database design must prevent mutation paths for sealed audit events.
- Audit writes have intentionally serialized throughput for one chain-state row.
- Regulated production deployments still need external audit anchoring, externally managed signing keys, WORM retention, or equivalent controls outside the primary database trust boundary.

## Alternatives Considered

- Mutable account balances only: rejected because it loses historical traceability.
- Single-entry transaction records: rejected because balanced entries provide a stronger correctness model.
- External accounting service: rejected for the portfolio MVP because it would hide the core learning value.
