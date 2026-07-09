# ADR 0007: Configurable Payment Providers And Stripe

## Status

Accepted

## Context

The current payment implementation uses one fake payment provider selected by `PAYMENT_PROVIDER=fake`. Payment and refund use cases inject one `PaymentProvider` directly, payment requests do not include a provider code, checkout does not return allowed payment providers, and payment/refund records do not persist provider identity.

Phase 8 adds configurable provider selection and a Stripe sandbox adapter while preserving the fake provider as the default local and test path. The design must avoid duplicate provider side effects, keep payment idempotency durable, and prevent Stripe SDK types from leaking into domain or application code.

## Decision

Provider codes are stable lowercase identifiers. The initial provider codes are `fake` and `stripe`.

`PAYMENT_PROVIDER_ACTIVE` is the default provider. It is used only when the payment request omits `provider` and omission is allowed. `PAYMENT_PROVIDER_ENABLED` is a comma-separated allow-list of providers the application can use.

Local and test profiles default to `active=fake` and `enabled=fake`. Production must explicitly set both active and enabled providers.

If only one provider is enabled, payment creation may omit `provider`. If more than one provider is enabled, payment creation must include `provider`. Checkout returns allowed providers for the order, but payment creation still validates the selected provider server-side.

Failed payment attempts may be retried while the order remains payable. Pending or unknown-outcome attempts block provider switching and new attempts until they are completed, failed safely, or reconciled. Only one successful captured payment is allowed per order.

Refunds use the provider code from the successful payment record. Refund requests do not choose a provider.

Stripe integration uses PaymentIntents, Stripe idempotency keys, webhook signature verification, and provider-scoped webhook lookup. Stripe support is sandbox-only for this portfolio project; live regulated payment operations are not claimed.

The first Stripe adapter supports USD card PaymentIntents only. It uses a portfolio sandbox cap of `999999.99 USD`, represented as `99999999` minor units, until a later task adds a currency minor-unit table and provider-specific amount configuration. The stored provider payment id is the Stripe PaymentIntent id; later Stripe refund work uses that id unless a separate charge-id persistence decision is made and documented.

PostgreSQL remains the durable source of truth for payment and refund idempotency through request hashes, stored responses, transactions, and unique constraints. Redis may be used later for rate limiting or short-lived coordination, but not as the durable idempotency source of truth.

Stripe SDK classes are isolated to infrastructure/configuration adapters. Domain and application types must not depend on Stripe SDK classes.

## Consequences

- Local demos and CI continue to run without Stripe secrets.
- Multi-provider behavior is deterministic and reviewable.
- Provider switching is safe only after the previous outcome is known.
- Payment, refund, webhook, audit, ledger, and reconciliation records can include provider code.
- Stripe webhook and timeout handling require reconciliation paths before production-like use.
- Database constraints remain the final protection against duplicate successful payments and duplicate active provider attempts.

## Alternatives Considered

- Keep a single `PAYMENT_PROVIDER` setting: rejected because it cannot represent enabled providers, defaults, or provider switching rules clearly.
- Let clients choose refund providers: rejected because refunds must go back through the provider that captured the payment.
- Use Redis as the payment idempotency store: rejected because eviction, TTL expiry, failover, or cache loss can reopen duplicate-charge windows.
- Couple application services directly to Stripe SDK classes: rejected because it weakens testability and violates the provider port boundary.
