# Threat Model

## Scope

This threat model covers the banking-grade MVP slice:

- Customer profile, cart, checkout, order, payment, refund, audit, ledger, reconciliation, and stock event APIs.
- Local Keycloak-backed OAuth2 Resource Server authentication.
- PostgreSQL persistence through Flyway-managed schema.
- Fake payment provider adapter and fake provider webhook endpoint.
- Transactional outbox and Server-Sent Events stock updates.

Out of scope for this portfolio version:

- Real card processing and PCI storage. The API stores no raw card data.
- Production network segmentation, WAF rules, SIEM integration, and cloud IAM.
- Real Stripe webhook signature verification. The fake provider endpoint models replay/idempotency behavior first.

## Assets

- Customer identity subject and profile data.
- Cart, order, payment, refund, and provider reference records.
- Immutable ledger transactions and entries.
- Audit events and audit hash-chain state.
- Idempotency records and request body hashes.
- Inventory and outbox records.
- OAuth2 access tokens and role/scope claims.

## Trust Boundaries

- External clients cross into the API through HTTPS in production.
- The API trusts Keycloak only after Spring Security validates issuer, signature, roles, and scopes.
- The API trusts PostgreSQL as the transaction and constraint boundary.
- Payment provider behavior is isolated behind a provider port.
- Stock SSE clients receive advisory events only; checkout never trusts client-visible stock.

## Threat Matrix

| Threat | Risk | Mitigations | Evidence |
| --- | --- | --- | --- |
| Broken object-level authorization | A customer could read or mutate another customer's cart, order, payment, refund, or profile. | Ownership-sensitive use cases resolve the customer by durable OAuth2 subject, not username/email. Controllers require customer role plus operation-specific scope. Cross-customer access returns forbidden. | `CurrentUser`, `JpaCustomerProfileLookup`, `CartService`, payment/refund use cases, `SecurityAuthorizationIntegrationTest`, `CartServiceAuthorizationTest`, `CustomerProfileBoundaryTest`. |
| Duplicate payment attempts | Retried requests, browser double-clicks, or network retries could charge twice. | Payment and refund endpoints require idempotency keys scoped by customer, endpoint, and operation. Request bodies are hashed. Same key/body replays the original response. Same key/different body returns `409 Conflict`. Unique database constraint enforces the scope. | `PaymentIdempotencyService`, `PaymentIdempotencyRecord`, Flyway `V7__create_payment_idempotency_table.sql`, `PaymentIdempotencyServiceTest`, `CreatePaymentUseCaseTest`, `RefundFlowTest`. |
| API replay attacks | A captured request could be replayed to repeat sensitive state changes. | OAuth2 access tokens are validated by Spring Security. Payment/refund state changes use idempotency semantics. Provider metadata includes provider idempotency keys. Sensitive reads/writes require roles and scopes. | `SecurityConfig`, `PaymentIdempotencyService`, `CreatePaymentUseCase`, `CreateRefundUseCase`, security integration tests. |
| Webhook replay | Duplicate provider webhook deliveries could corrupt payment/order/ledger state. | Provider event ids are persisted and processed idempotently. Duplicate events are rejected or replayed safely. State transitions are validated before payment/order/ledger changes. Suspicious webhook outcomes are audited. | `FakeProviderWebhookUseCase`, `ProviderWebhookEventRecord`, Flyway `V11__create_provider_webhook_event_table.sql`, `ProviderWebhookHandlingTest`. |
| PII leakage | API responses, logs, audit views, or errors could expose passwords, tokens, or unnecessary PII. | Controllers return DTOs rather than entities. Profile responses exclude password fields. Problem Details avoid stack traces and secrets. Logs include correlation ids but not authorization headers or raw tokens. Audit views are explicit DTOs. | `CustomerProfile`, `CustomerProfileController`, `GlobalExceptionHandler`, architecture tests preventing controller persistence returns, `CustomerProfileBoundaryTest`. |
| Audit tampering | An attacker or operator could modify historical audit records without detection. | Audit events store canonical payload hashes and previous hashes. Verification recalculates the chain and reports the first broken event. Audit records are append-style application records. | `AuditHashService`, `AuditHashVerificationService`, Flyway `V13__add_audit_hash_chain.sql`, `AuditHashVerificationTest`, `AuditEventController`. |
| Privilege escalation | A user with a valid token but insufficient role/scope could access admin, auditor, ledger, or payment operations. | Method security requires both role and scope for sensitive operations. Keycloak roles are mapped from realm/resource roles. Security tests prove missing role or missing scope is forbidden. | `SecurityExpressions`, `SecurityConfig`, `SecurityAuthorizationIntegrationTest`, `AuthorizationPolicyTest`. |
| Inventory race conditions | Concurrent checkout could oversell limited stock. | Checkout reserves inventory through an atomic database update requiring sufficient available quantity. Failed reservation aborts checkout. UI/SSE stock is advisory only. | `InventoryReservationService`, inventory migration constraints, `CheckoutUseCaseTest`, `InventoryReservationServiceTest`. |
| Ledger mutation | Posted money movement could be edited or deleted instead of corrected. | Ledger records are modeled as immutable application records. Database triggers prevent updates/deletes on ledger transactions and entries. Corrections use reversing transactions. | `LedgerPostingService`, Flyway `V9__create_ledger_tables.sql`, `LedgerPostingServiceTest`, ADR 0005. |
| Outbox event loss | Stock events could be lost after checkout commits but before notification. | Stock events are written to the transactional outbox in the same transaction as inventory changes. A scheduled processor retries and records failures. | `StockChangedOutboxPublisher`, `OutboxEventProcessor`, Flyway `V14__create_outbox_event_table.sql`, `OutboxEventProcessorTest`. |

## Residual Risks And Follow-Up

- Local demo credentials are intentionally simple and must not be reused outside local development.
- Fake provider webhooks model replay safety, but real provider integration should add signature verification, timestamp tolerance, and provider-specific idempotency keys.
- The in-memory SSE broadcaster is single-instance only. Multi-instance production deployment should use Redis Pub/Sub, Kafka, or another shared fan-out mechanism behind the existing outbox.
- The public registration endpoint is legacy-compatible and should be replaced by a subject-bound provisioning flow before production use.
- Database append-only guarantees are demonstrated by migrations/tests, but production hardening should also restrict application database permissions.
- Token lifetime, refresh-token policy, TLS termination, rate limiting, and abuse monitoring belong in deployment/platform configuration.

## Reviewer Checklist

- Cross-customer access is denied.
- Payment idempotency returns stable replay and conflict on mismatched body.
- Refund idempotency prevents duplicate refunds.
- Webhook duplicate delivery is safe.
- Ledger entries balance and cannot be modified.
- Audit hash verification detects tampering.
- Reconciliation detects missing or orphaned money records.
- Stock cannot be oversold under concurrent checkout.
