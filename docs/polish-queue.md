# Banking-Grade Polish Queue

Date: 2026-07-15

Purpose: convert the current strong portfolio backend into something that can be defended as closer to a banking-grade system. The current `docs/final-portfolio-review.md` says the repository is ready to share as banking-grade; this queue lists the remaining concrete work before that claim should be used without qualification.

## Priority Rules

- P0: Must complete before calling the project banking-grade.
- P1: Must complete before any production-style deployment claim.
- P2: Portfolio polish that improves reviewer confidence but is not a core security blocker.

## P0 Tasks

### P0-01: Make runtime profile selection fail closed

Status: Completed on 2026-07-15.

Problem: `application.properties` defaults to the `local` profile. Local mode enables demo data, SQL logging, local credentials, and a fake provider secret.

Files to change:

- `src/main/resources/application.properties`
- `src/main/resources/application-local.properties`
- `src/main/java/com/phu/ecommerceapi/config/DeploymentProfileGuard.java`
- `src/test/java/com/phu/ecommerceapi/config/DeploymentProfileGuardTest.java`
- `README.md`

Implementation requirements:

- Remove `spring.profiles.default=local` from `src/main/resources/application.properties`.
- Add a startup guard that fails when no explicit Spring profile is active.
- The guard must allow only these explicit active profiles:
  - `local`
  - `test`
  - `prod`
- The guard must reject `local` when either condition is true:
  - `app.deployment.containerized=true`
  - `app.environment` is anything other than `local` or `test`
- The failure message must include this exact text: `SPRING_PROFILES_ACTIVE must be explicitly set`.
- Update README local-start instructions to set `SPRING_PROFILES_ACTIVE=local`.
- Keep Dockerfile production defaults as `SPRING_PROFILES_ACTIVE=prod`, `APP_ENVIRONMENT=prod`, and `APP_CONTAINERIZED=true`.

Acceptance criteria:

- Running without `SPRING_PROFILES_ACTIVE` fails before the app can serve requests.
- Running with `SPRING_PROFILES_ACTIVE=local` still supports the documented local demo.
- Running the Docker image still uses the prod profile by default.
- `DeploymentProfileGuardTest` covers:
  - no active profile is rejected;
  - explicit `local` is accepted for local environment;
  - `local` plus containerized marker is rejected;
  - explicit `prod` is accepted.

Verification commands:

```powershell
.\mvnw.cmd -B -ntp -Dtest=DeploymentProfileGuardTest test
.\mvnw.cmd -B -ntp -DskipTests compile
```

### P0-02: Require authorized-party binding for production JWT validation

Status: Completed on 2026-07-15.

Problem: production config currently allows `app.security.oauth2.allowed-authorized-parties` to default to blank. When blank, `OAuth2AudienceAndAuthorizedPartyValidator` does not enforce the JWT `azp` claim.

Files to change:

- `src/main/resources/application-prod.properties`
- `src/main/java/com/phu/ecommerceapi/config/DeploymentProfileGuard.java`
- `src/test/java/com/phu/ecommerceapi/config/DeploymentProfileGuardTest.java`
- `src/test/java/com/phu/ecommerceapi/config/OAuth2AudienceAndAuthorizedPartyValidatorTest.java`
- `.env.example`
- `README.md`

Implementation requirements:

- Change production config from:
  - `app.security.oauth2.allowed-authorized-parties=${OAUTH2_ALLOWED_AUTHORIZED_PARTIES:}`
- To:
  - `app.security.oauth2.allowed-authorized-parties=${OAUTH2_ALLOWED_AUTHORIZED_PARTIES}`
- Add a prod startup guard that rejects blank `app.security.oauth2.allowed-authorized-parties`.
- The failure message must include this exact text: `OAUTH2_ALLOWED_AUTHORIZED_PARTIES is required in prod`.
- Keep local and test defaults as `ecommerce-web`.
- Document the required production variable in `.env.example` and README.

Acceptance criteria:

- A prod startup with missing `OAUTH2_ALLOWED_AUTHORIZED_PARTIES` fails.
- A prod startup with `OAUTH2_ALLOWED_AUTHORIZED_PARTIES=ecommerce-web` starts past configuration validation.
- JWTs without an allowed `azp` remain rejected.
- JWTs with required issuer, audience, and allowed `azp` remain accepted.

Verification commands:

```powershell
.\mvnw.cmd -B -ntp -Dtest=DeploymentProfileGuardTest,OAuth2AudienceAndAuthorizedPartyValidatorTest test
```

### P0-03: Harden audit events against database tampering

Status: Completed on 2026-07-15.

Problem: audit events have a hash chain, but audit rows can still be updated or deleted at the database level. Ledger rows have append-only triggers; audit rows do not.

Files to change:

- `src/main/resources/db/migration/V24__make_audit_event_append_only.sql`
- `src/test/java/com/phu/ecommerceapi/audit/infrastructure/JpaAuditEventRecorderTest.java`
- `src/test/java/com/phu/ecommerceapi/audit/api/AuditHashVerificationTest.java`
- `docs/threat-model.md`
- `docs/adr/0005-immutable-ledger.md` or a new ADR if preferred

Implementation requirements:

- Add a Flyway migration named exactly `V24__make_audit_event_append_only.sql`.
- The migration must fail if any existing `audit_event.event_hash` is null.
- Add a PostgreSQL trigger function named `reject_audit_event_mutation`.
- Add a trigger named `trg_audit_event_no_update` that rejects `UPDATE` and `DELETE` on `audit_event`.
- Use the same trigger style as `reject_ledger_mutation` in `V9__create_ledger_tables.sql`.
- Add an integration test proving `UPDATE audit_event SET details = 'tampered'` fails.
- Add an integration test proving `DELETE FROM audit_event` fails.
- Update the threat model to distinguish:
  - current hash-chain tamper detection;
  - new database-level append-only protection;
  - remaining need for external/WORM audit anchoring in real regulated production.

Acceptance criteria:

- Existing audit writes still work.
- Audit verification still passes after normal writes.
- Direct SQL update/delete against `audit_event` fails.
- The threat model no longer implies that hash chaining alone is sufficient against privileged DB tampering.

Verification commands:

```powershell
.\mvnw.cmd -B -ntp -Dtest=JpaAuditEventRecorderTest,AuditHashVerificationTest test
```

### P0-04: Add an external-key audit seal

Status: Completed on 2026-07-15.

Problem: plain SHA-256 hashes can be recomputed by anyone with database write access. Banking-grade audit evidence needs a secret or external trust anchor not stored in the same database.

Files to change:

- `src/main/resources/db/migration/V25__add_audit_event_signature.sql`
- `src/main/resources/application.properties`
- `src/main/resources/application-prod.properties`
- `src/main/resources/application-local.properties`
- `src/main/resources/application-test.properties`
- `src/main/java/com/phu/ecommerceapi/audit/application/AuditHashService.java`
- `src/main/java/com/phu/ecommerceapi/audit/infrastructure/AuditEventRecord.java`
- `src/main/java/com/phu/ecommerceapi/audit/infrastructure/JpaAuditEventRecorder.java`
- `src/main/java/com/phu/ecommerceapi/config/DeploymentProfileGuard.java`
- audit hash tests under `src/test/java/com/phu/ecommerceapi/audit`
- `docs/threat-model.md`

Implementation requirements:

- Add nullable column `event_signature VARCHAR(64)` to `audit_event`.
- Add config property:
  - common: `app.audit.signature-secret=${AUDIT_SIGNATURE_SECRET:}`
  - prod: `app.audit.signature-secret=${AUDIT_SIGNATURE_SECRET}`
  - local/test: safe non-production placeholder value.
- Add a prod startup guard that rejects blank `app.audit.signature-secret`.
- Compute `event_signature` as HMAC-SHA256 over `event_hash`.
- Do not store the HMAC secret in the database.
- Include `event_signature` in audit read DTOs only for admin/auditor APIs.
- Update audit verification to validate both:
  - hash-chain continuity;
  - HMAC signature validity when the signature column is present.

Acceptance criteria:

- A prod startup without `AUDIT_SIGNATURE_SECRET` fails.
- New audit events store both `event_hash` and `event_signature`.
- Audit verification fails when `event_hash` is changed.
- Audit verification fails when `event_signature` is changed.
- Tests prove old rows with null signature are handled only if created before `V25`.

Verification commands:

```powershell
.\mvnw.cmd -B -ntp -Dtest=*Audit* test
```

### P0-05: Fix least-privilege authorization gaps

Status: Completed on 2026-07-15.

Problem: some write or operational endpoints use read scopes, and product/stock read endpoints rely on global authentication instead of explicit endpoint authorization.

Files to change:

- `src/main/java/com/phu/ecommerceapi/identity/application/SecurityExpressions.java`
- `src/main/java/com/phu/ecommerceapi/customer/api/CustomerProfileController.java`
- `src/main/java/com/phu/ecommerceapi/reconciliation/api/ReconciliationController.java`
- `src/main/java/com/phu/ecommerceapi/catalog/api/ProductCatalogController.java`
- `src/main/java/com/phu/ecommerceapi/inventory/api/StockEventStreamController.java`
- `docker/keycloak/import/ecommerce-realm.json`
- `src/test/java/com/phu/ecommerceapi/config/SecurityAuthorizationIntegrationTest.java`
- `src/test/java/com/phu/ecommerceapi/config/AuthorizationPolicyTest.java`
- relevant controller tests

Implementation requirements:

- Add these constants to `SecurityExpressions`:
  - `CUSTOMER_PROFILE_WRITE = "hasRole('CUSTOMER') and hasAuthority('SCOPE_profile:write')"`
  - `PRODUCT_READ = "hasAuthority('SCOPE_product:read')"`
  - `STOCK_STREAM = "hasAuthority('SCOPE_stock:stream')"`
  - `ADMIN_RECONCILIATION_RUN = "hasRole('ADMIN') and hasAuthority('SCOPE_reconciliation:run')"`
- Change `POST /customer/profile/me` to use `CUSTOMER_PROFILE_WRITE`.
- Keep `GET /customer/profile/me` on `CUSTOMER_PROFILE_READ`.
- Change `POST /reconciliation/runs` to use `ADMIN_RECONCILIATION_RUN`.
- Keep `GET /reconciliation/report` read-only for admin/auditor using audit-read scope.
- Add `@PreAuthorize(SecurityExpressions.PRODUCT_READ)` to:
  - `GET /products`
  - `GET /products/{id}`
- Add `@PreAuthorize(SecurityExpressions.STOCK_STREAM)` to:
  - `GET /products/{productId}/stock/stream`
- Update the Keycloak realm import so demo users receive the exact new scopes needed for the README demo.

Acceptance criteria:

- A token with `profile:read` but not `profile:write` cannot call `POST /customer/profile/me`.
- An auditor can read reconciliation reports but cannot start reconciliation runs.
- An admin with `reconciliation:run` can start a reconciliation run.
- A token without `product:read` cannot call product catalog endpoints.
- A token without `stock:stream` cannot open the stock SSE endpoint.

Verification commands:

```powershell
.\mvnw.cmd -B -ntp -Dtest=SecurityAuthorizationIntegrationTest,AuthorizationPolicyTest test
```

### P0-06: Remove unused password storage from the customer model

Status: Completed on 2026-07-15.

Problem: authentication is delegated to OAuth2/Keycloak, but `UserModel` and the initial schema still contain a `password` field/column.

Files to change:

- `src/main/java/com/phu/ecommerceapi/User/UserModel.java`
- `src/main/java/com/phu/ecommerceapi/User/UserRepo.java`
- `src/main/resources/db/migration/V26__drop_unused_user_password.sql`
- `src/main/resources/demo-data.sql`
- tests that build `UserModel` with `.password(...)`
- `docs/threat-model.md`

Implementation requirements:

- Remove `private String password` from `UserModel`.
- Remove `getPassword()`.
- Remove `password` from `UserRepo.insertProvisionedProfileIfAbsent`.
- Remove password values from `demo-data.sql`.
- Add Flyway migration `V26__drop_unused_user_password.sql`:
  - `ALTER TABLE user_model DROP COLUMN password;`
- Update tests to stop setting or asserting a password field.
- Update the threat model to state that the API stores no local password hashes.

Acceptance criteria:

- `rg -n "password" src/main/java/com/phu/ecommerceapi/User src/main/resources/demo-data.sql` returns no password storage references.
- Customer profile responses still do not expose password fields.
- Customer provisioning still works.

Verification commands:

```powershell
rg -n "password" src/main/java/com/phu/ecommerceapi/User src/main/resources/demo-data.sql
.\mvnw.cmd -B -ntp -Dtest=CustomerProfileBoundaryTest,CustomerProfileControllerTest test
```

## P1 Tasks

### P1-01: Make rate limiting production-safe

Status: Completed on 2026-07-15.

Problem: `AbuseRateLimitFilter` uses in-memory counters. The threat model already states this is single-instance only.

Files to change:

- `src/main/java/com/phu/ecommerceapi/shared/api/AbuseRateLimitFilter.java`
- new package or classes under `src/main/java/com/phu/ecommerceapi/shared/ratelimit`
- `src/main/resources/application.properties`
- `src/main/resources/application-prod.properties`
- `src/main/resources/application-local.properties`
- `src/main/resources/application-test.properties`
- `src/main/java/com/phu/ecommerceapi/config/DeploymentProfileGuard.java`
- `src/test/java/com/phu/ecommerceapi/shared/api/AbuseRateLimitFilterTest.java`
- `docs/threat-model.md`
- `README.md`

Implementation requirements:

- Introduce property `app.security.rate-limit.backend`.
- Allowed values:
  - `in-memory`
  - `gateway`
  - `redis`
- Set local/test default to `in-memory`.
- Set prod default to `${RATE_LIMIT_BACKEND}` with no fallback.
- Add prod startup guard:
  - reject blank backend;
  - reject `in-memory` in prod.
- If backend is `gateway`, keep app body-size protection but skip app-level request counting.
- If backend is `redis`, implement shared counters with TTL using a Redis client.
- Keep in-memory implementation only for local/test.

Acceptance criteria:

- Prod cannot start with in-memory rate limiting.
- Local tests continue to exercise in-memory rate limiting.
- README explains the exact required prod setting.
- Threat model no longer presents in-memory limiting as a production control.

Verification commands:

```powershell
.\mvnw.cmd -B -ntp -Dtest=AbuseRateLimitFilterTest,DeploymentProfileGuardTest test
```

### P1-02: Harden stock SSE endpoint against connection abuse

Status: Completed on 2026-07-15.

Problem: `StockEventBroadcaster` keeps SSE connections in memory for 30 minutes and has no visible per-user/per-IP connection cap.

Files to change:

- `src/main/java/com/phu/ecommerceapi/inventory/api/StockEventStreamController.java`
- `src/main/java/com/phu/ecommerceapi/inventory/application/StockEventBroadcaster.java`
- `src/main/resources/application.properties`
- `src/main/resources/application-prod.properties`
- `src/test/java/com/phu/ecommerceapi/inventory/api/StockEventStreamControllerTest.java`
- `src/test/java/com/phu/ecommerceapi/inventory/application/StockEventBroadcasterTest.java`
- `docs/threat-model.md`

Implementation requirements:

- Require `STOCK_STREAM` authorization from P0-05.
- Add config:
  - `app.stock-stream.max-connections-per-client=${STOCK_STREAM_MAX_CONNECTIONS_PER_CLIENT:3}`
  - `app.stock-stream.timeout-seconds=${STOCK_STREAM_TIMEOUT_SECONDS:300}`
- Reduce default timeout from 30 minutes to 5 minutes.
- Track subscribers by:
  - product id;
  - authenticated subject when available;
  - otherwise trusted client IP from `RequestMetadataHolder`.
- Reject subscriptions above the configured per-client limit with HTTP 429.
- Expose a test-visible subscriber count by client key and product id.

Acceptance criteria:

- Opening the fourth stream for the same client returns 429 when the limit is 3.
- Closing a stream decrements the subscriber count.
- Unauthorized tokens cannot open the stream.
- Existing stock event publication still delivers to active subscribers.

Verification commands:

```powershell
.\mvnw.cmd -B -ntp -Dtest=StockEventStreamControllerTest,StockEventBroadcasterTest test
```

### P1-03: Make CI security gates reproducible

Problem: local `mvn verify` failed in this environment because Docker/Testcontainers was unavailable. OWASP dependency-check also did not finish within 3 minutes. CI must make prerequisites and failure modes explicit.

Files to change:

- `.github/workflows/ci.yml`
- `pom.xml`
- `README.md`
- optional: new `docs/verification.md`

Implementation requirements:

- Add a CI preflight step before `./mvnw verify`:
  - `docker version`
  - `docker info`
- Add a README prerequisite section stating Docker must be running for Testcontainers-backed tests.
- Add OWASP dependency-check to CI using the existing Maven profile.
- Configure dependency-check to use `NVD_API_KEY` from GitHub Actions secrets when present.
- Upload dependency-check HTML and JSON reports as CI artifacts.
- Keep build failure on CVSS 7.0 or higher.
- Add a scheduled weekly workflow trigger for dependency scanning.

Acceptance criteria:

- CI fails early with a clear Docker message if Docker is unavailable.
- Dependency-check reports are uploaded even when the scan fails.
- README has a copy-paste verification command sequence for Windows PowerShell.

Verification commands:

```powershell
docker version
.\mvnw.cmd -B -ntp verify
.\mvnw.cmd -B -ntp -Pdependency-scan -DskipTests verify
```

### P1-04: Add SAST, SBOM, and stronger coverage gates

Problem: CI currently has dependency review, Gitleaks, and Trivy, but no visible CodeQL/SAST job, no SBOM artifact, and only 20% line coverage enforcement.

Files to change:

- `.github/workflows/ci.yml`
- `pom.xml`
- `README.md`

Implementation requirements:

- Add GitHub CodeQL analysis for Java.
- Add CycloneDX Maven plugin.
- Generate an SBOM during CI.
- Upload the SBOM as a build artifact.
- Raise `jacoco.minimum.line.coverage` from `0.20` to at least `0.80`.
- If 0.80 cannot pass immediately, split the work into:
  - first commit: record current coverage in README;
  - second commit: add focused tests until 0.80 passes;
  - final commit: raise the threshold.
- Do not exclude production code from coverage unless the exclusion is documented in `pom.xml` with a comment.

Acceptance criteria:

- CodeQL job runs on pull requests and pushes to `main`.
- CI uploads an SBOM.
- JaCoCo fails the build below the selected minimum.
- README documents all security gates.

Verification commands:

```powershell
.\mvnw.cmd -B -ntp verify
```

### P1-05: Pin container images by digest

Problem: Dockerfile and Compose use mutable image tags.

Files to change:

- `Dockerfile`
- `compose.yaml`
- `README.md`

Implementation requirements:

- Pin `eclipse-temurin:21-jdk-jammy` by digest.
- Pin `eclipse-temurin:21-jre-jammy` by digest.
- Pin `postgres:16-alpine` by digest.
- Pin `quay.io/keycloak/keycloak:26.6.4` by digest.
- Add README instructions for refreshing pinned digests intentionally.

Acceptance criteria:

- `Dockerfile` uses `FROM image@sha256:<digest>` for both stages.
- `compose.yaml` uses `image: name@sha256:<digest>`.
- README explains that digest updates must go through CI and image scanning.

Verification commands:

```powershell
docker build -t ecommerce-api:local .
docker compose config
```

## P2 Tasks

### P2-01: Update final portfolio review after the queue is resolved

Problem: `docs/final-portfolio-review.md` currently says the repository is ready as a banking-grade backend portfolio project. That is too strong while P0 items remain open.

Files to change:

- `docs/final-portfolio-review.md`

Implementation requirements:

- Replace the current result with a qualified statement:
  - ready as a strong backend portfolio;
  - not yet ready to claim banking-grade without completing P0 items in `docs/polish-queue.md`.
- Add a link to `docs/polish-queue.md`.
- Re-run the verification commands listed in that document before marking it final again.

Acceptance criteria:

- The final review no longer says there are no blocking issues while P0 tasks are open.
- The final review and this queue do not contradict each other.

### P2-02: Clarify local-only Compose and demo credentials

Problem: `compose.yaml` uses Keycloak `start-dev`, local admin credentials, and local database credentials. This is acceptable for demo, but should be labeled clearly.

Files to change:

- `compose.yaml`
- `.env.example`
- `README.md`
- `docs/local-keycloak.md`

Implementation requirements:

- Add comments or documentation stating `compose.yaml` is local/demo only.
- Ensure README does not imply `compose.yaml` is production deployment infrastructure.
- Keep `start-dev` only in local documentation.
- Keep local credentials documented as non-production placeholders.

Acceptance criteria:

- A reviewer can immediately tell that Compose is for local demo only.
- Production instructions point to the Docker image plus required environment variables, not the local Compose file.

### P2-03: Add a production-readiness checklist

Problem: the threat model lists deployment/platform controls as out of scope, but there is no single checklist for those controls.

Files to change:

- new `docs/production-readiness-checklist.md`
- `README.md`
- `docs/threat-model.md`

Implementation requirements:

- Create a checklist covering:
  - TLS termination;
  - WAF/gateway rate limiting;
  - centralized logging/SIEM;
  - secrets manager;
  - database role separation;
  - backup/restore testing;
  - incident response;
  - vulnerability management;
  - audit log retention;
  - key rotation;
  - zero-downtime migrations;
  - monitoring/alerting;
  - disaster recovery objectives.
- Link the checklist from README and threat model.

Acceptance criteria:

- The project clearly separates implemented application controls from required platform controls.
- The checklist uses checkboxes and concrete verification evidence fields.

## Completion Definition

The project can be described as "banking-grade portfolio-ready" only after:

- every P0 item is complete;
- `.\mvnw.cmd -B -ntp verify` passes in an environment with Docker available;
- dependency scanning completes or fails with an actionable vulnerability report;
- `docs/final-portfolio-review.md` is updated to match the actual state;
- this queue is either completed or each remaining P1/P2 item is explicitly documented as a non-production portfolio tradeoff.
