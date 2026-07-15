# Threat Model

## Scope

This threat model covers the banking-grade MVP slice:

- Customer profile, cart, checkout, order, payment, refund, audit, ledger, reconciliation, and stock event APIs.
- Local Keycloak-backed OAuth2 Resource Server authentication.
- PostgreSQL persistence through Flyway-managed schema.
- Fake and Stripe payment provider adapters, including provider webhook endpoints.
- Transactional outbox and Server-Sent Events stock updates.

Out of scope for this portfolio version:

- Real card processing and PCI storage. The API stores no raw card data.
- Production network segmentation, WAF rules, SIEM integration, and cloud IAM.
- Live regulated payment operations. Stripe support is sandbox-oriented for portfolio review.

## Assets

- Customer identity subject and profile data.
- Cart, order, payment, refund, and provider reference records.
- Immutable ledger transactions and entries.
- Audit events and audit hash-chain state.
- Idempotency records and request body hashes.
- Inventory and outbox records.
- OAuth2 access tokens and role/scope claims.
- Provider webhook secrets, Stripe API keys, provider event ids, and provider idempotency keys.

## Trust Boundaries

- External clients cross into the API through HTTPS in production.
- The API trusts Keycloak only after Spring Security validates issuer, signature, roles, and scopes.
- The API trusts PostgreSQL as the transaction and constraint boundary.
- Payment provider behavior is isolated behind a provider port.
- Stripe is trusted only through configured sandbox credentials, webhook signature verification, provider idempotency keys, and reconciliation reads.
- Stock SSE clients receive advisory events only; checkout never trusts client-visible stock.

## Threat Matrix

| Threat | Risk | Mitigations | Evidence |
| --- | --- | --- | --- |
| Broken object-level authorization | A customer could read or mutate another customer's cart, order, payment, refund, or profile. | Ownership-sensitive use cases resolve the customer by durable OAuth2 subject, not username/email. Controllers require customer role plus operation-specific scope. Cross-customer access returns forbidden. | `CurrentUser`, `JpaCustomerProfileLookup`, `CartService`, payment/refund use cases, `SecurityAuthorizationIntegrationTest`, `CartServiceAuthorizationTest`, `CustomerProfileBoundaryTest`. |
| Duplicate payment attempts | Retried requests, browser double-clicks, or network retries could charge twice. | Payment and refund endpoints require idempotency keys scoped by customer, endpoint, and operation. Request bodies are hashed. Same key/body replays the original response. Same key/different body returns `409 Conflict`. Unique database constraint enforces the scope. | `PaymentIdempotencyService`, `PaymentIdempotencyRecord`, Flyway `V7__create_payment_idempotency_table.sql`, `PaymentIdempotencyServiceTest`, `CreatePaymentUseCaseTest`, `RefundFlowTest`. |
| API replay attacks | A captured request could be replayed to repeat sensitive state changes. | OAuth2 access tokens are validated by Spring Security. Payment/refund state changes use idempotency semantics. Provider metadata includes provider idempotency keys. Sensitive reads/writes require roles and scopes. | `SecurityConfig`, `PaymentIdempotencyService`, `CreatePaymentUseCase`, `CreateRefundUseCase`, security integration tests. |
| Public API documentation | Production OpenAPI or Swagger UI could disclose endpoint structure and authorization details by accident. | SpringDoc is disabled by default in production. If enabled while `OPENAPI_PUBLIC_DOCS_ENABLED=false`, docs require admin or auditor access. Public production docs require an explicit `OPENAPI_PUBLIC_DOCS_ENABLED=true` setting. | `SecurityConfig`, `OpenApiExposureProperties`, `application-prod.properties`, `OpenApiDocumentationTest`, OpenAPI security tests. |
| Webhook replay | Duplicate provider webhook deliveries could corrupt payment/order/ledger state. | Provider event ids are persisted and processed idempotently. Duplicate events are rejected or replayed safely. State transitions are validated before payment/order/ledger changes. Suspicious webhook outcomes are audited. | `FakeProviderWebhookUseCase`, `ProviderWebhookEventRecord`, Flyway `V11__create_provider_webhook_event_table.sql`, `ProviderWebhookHandlingTest`. |
| Webhook forgery or payload abuse | An attacker could post fake provider callbacks or oversized bodies to unauthenticated webhook endpoints. | Fake webhooks require the configured provider secret. Stripe webhooks verify provider signatures before parsing. Webhook endpoints have local rate limits and a stricter request body size ceiling enforced from actual request bytes, not only `Content-Length`. Production ingress or reverse proxy body-size limits must still be set to the same or a lower value. | `StripeWebhookEventParserAdapter`, `FakeProviderWebhookUseCase`, `AbuseRateLimitFilter`, `StripeWebhookEventParserAdapterTest`, `AbuseRateLimitFilterTest`. |
| Provider switching or double charge | A client could try another provider while a payment outcome is pending or unknown. | Provider selection is server-validated. Active attempts and successful payments are constrained by database state. Unknown or pending provider outcomes block new attempts until webhook/reconciliation/recovery resolves them. | `PaymentProviderRegistry`, `CreatePaymentUseCase`, Flyway provider metadata constraints, `CreatePaymentUseCaseTest`, `PaymentIdempotencyRecoveryServiceTest`. |
| Provider timeout or outage | Stripe calls could time out after the provider accepted a side effect, leaving local state uncertain. | Stripe timeouts are recorded as unresolved provider outcomes instead of retried blindly. Recovery and reconciliation rebuild stable local responses or flag manual review without creating a second provider side effect. Metrics include provider timeout/error labels. | `StripePaymentProviderAdapter`, `PaymentIdempotencyRecoveryService`, `ReconciliationService`, `BusinessMetricsTest`, `ReconciliationServiceTest`. |
| Reconciliation mismatch handling | Local payment/refund state could diverge from Stripe or ledger state after an outage, delayed webhook, manual provider action, or partial failure. | Reconciliation compares local provider references, Stripe current state when available, webhook event records, refund/payment status, and ledger entries. Mismatches are reported instead of silently corrected when the safe outcome is ambiguous. | `ReconciliationService`, `StripeProviderReadPort`, provider webhook persistence, `ReconciliationServiceTest`, `ProviderWebhookHandlingTest`. |
| Provider secret leakage | Stripe keys or webhook secrets could leak through config, image layers, logs, error responses, or audits. | Secrets are read from environment variables, not tracked files or the Docker image. Startup validation requires provider secrets only when that provider is enabled. Problem Details and audit details avoid raw headers, request bodies, and secret values. CI runs Gitleaks. | `AppProperties`, `.env.example`, `Dockerfile`, `.github/workflows/ci.yml`, `AppPropertiesTest`, `BusinessMetricsTest`. |
| Sensitive endpoint abuse | Attackers could brute-force profile provisioning/profile reads or flood payment, refund, and webhook endpoints. | A bounded in-memory limiter throttles sensitive endpoints by trusted-proxy-aware client identity, evicts inactive keys, rejects new keys once capacity is reached, and returns sanitized `429` Problem Details. Production multi-instance deployments must use Redis, gateway, platform, or WAF rate limiting as the authoritative layer. | `AbuseRateLimitFilter`, `RequestIdFilter`, `AbuseRateLimitFilterTest`, `README.md`. |
| PII leakage | API responses, logs, audit views, or errors could expose passwords, tokens, or unnecessary PII. | Controllers return DTOs rather than entities. The API stores no local password hashes, and profile responses exclude password fields. Problem Details avoid stack traces and secrets. Logs include correlation ids but not authorization headers or raw tokens. Audit views are explicit DTOs. | `CustomerProfile`, `CustomerProfileController`, `GlobalExceptionHandler`, architecture tests preventing controller persistence returns, `CustomerProfileBoundaryTest`. |
| Audit tampering | An attacker or operator could modify historical audit records without detection. | Audit events store canonical payload hashes, previous hashes, and HMAC-SHA256 signatures over each event hash using `AUDIT_SIGNATURE_SECRET`, which is not stored in the database. Verification recalculates the chain and validates signatures when present. Database triggers reject direct updates/deletes on `audit_event`, reject new unsigned inserts after the signature migration, and migration fails if unhashed legacy audit rows remain. Real regulated production should still anchor audit evidence outside the primary database, such as WORM storage or a managed external signing service. | `AuditHashService`, `AuditHashVerificationService`, Flyway `V13__add_audit_hash_chain.sql`, Flyway `V24__make_audit_event_append_only.sql`, Flyway `V25__add_audit_event_signature.sql`, `AuditHashVerificationTest`, `AuditEventController`. |
| Privilege escalation | A user with a valid token but insufficient role/scope could access admin, auditor, ledger, or payment operations. | Method security requires both role and scope for sensitive operations. Keycloak roles are mapped from realm/resource roles. Security tests prove missing role or missing scope is forbidden. | `SecurityExpressions`, `SecurityConfig`, `SecurityAuthorizationIntegrationTest`, `AuthorizationPolicyTest`. |
| Inventory race conditions | Concurrent checkout could oversell limited stock. | Checkout reserves inventory through an atomic database update requiring sufficient available quantity. Failed reservation aborts checkout. UI/SSE stock is advisory only. | `InventoryReservationService`, inventory migration constraints, `CheckoutUseCaseTest`, `InventoryReservationServiceTest`. |
| Ledger mutation | Posted money movement could be edited or deleted instead of corrected. | Ledger records are modeled as immutable application records. Database triggers prevent updates/deletes on ledger transactions and entries. Corrections use reversing transactions. | `LedgerPostingService`, Flyway `V9__create_ledger_tables.sql`, `LedgerPostingServiceTest`, ADR 0005. |
| Outbox event loss | Stock events could be lost after checkout commits but before notification. | Stock events are written to the transactional outbox in the same transaction as inventory changes. A scheduled processor retries and records failures. | `StockChangedOutboxPublisher`, `OutboxEventProcessor`, Flyway `V14__create_outbox_event_table.sql`, `OutboxEventProcessorTest`. |

## Residual Risks And Follow-Up

- Local demo credentials are intentionally simple and must not be reused outside local development.
- The in-memory rate limiter is bounded and trusted-proxy-aware, but remains single-instance only; horizontally scaled production must use Redis, gateway-level throttling, platform controls, or WAF controls as the authoritative limiter.
- Webhook body limits in the app depend on the request content length; production ingress should also enforce body size limits before traffic reaches the JVM.
- Stripe sandbox support includes signature verification and provider idempotency, but live regulated payment operations would require PCI, operational, and incident-response controls beyond this portfolio scope.
- The in-memory SSE broadcaster is single-instance only. Multi-instance production deployment should use Redis Pub/Sub, Kafka, or another shared fan-out mechanism behind the existing outbox.
- Customer profiles are provisioned from authenticated OAuth2 subjects; anonymous local password registration is not exposed, and the API stores no local password hashes.
- Database append-only guarantees for ledger and audit records and HMAC-sealed audit hashes are demonstrated by migrations/tests, but production hardening should also restrict application database permissions and anchor audit evidence outside the primary database.
- Token lifetime, refresh-token policy, TLS termination, centralized rate limiting, and abuse monitoring belong in deployment/platform configuration.

## Reviewer Checklist

- Cross-customer access is denied.
- Payment idempotency returns stable replay and conflict on mismatched body.
- Refund idempotency prevents duplicate refunds.
- Webhook duplicate delivery is safe.
- Ledger entries balance and cannot be modified.
- Audit hash verification detects tampering.
- Reconciliation detects missing or orphaned money records.
- Stock cannot be oversold under concurrent checkout.
