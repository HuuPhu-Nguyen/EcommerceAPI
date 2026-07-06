# ADR 0005: Immutable Ledger

## Status

Accepted

## Context

Money movement must be auditable and correct. Updating balances directly would be simple, but it would not demonstrate the level of traceability expected in banking-style systems.

## Decision

Use an immutable double-entry-style ledger.

Payments and refunds create ledger transactions with balanced ledger entries. Ledger entries are append-only. Corrections must use reversing transactions rather than updates or deletes.

## Consequences

- Money movement has a durable audit trail.
- Reconciliation can detect missing, orphaned, or unbalanced records.
- Tests can prove every transaction balances per currency.
- Application code and database design must prevent mutation paths for posted ledger entries.

## Alternatives Considered

- Mutable account balances only: rejected because it loses historical traceability.
- Single-entry transaction records: rejected because balanced entries provide a stronger correctness model.
- External accounting service: rejected for the portfolio MVP because it would hide the core learning value.
