# Production Readiness Checklist

This checklist is the go-live evidence list for a real production deployment. It intentionally separates controls implemented in this application from controls that must be supplied by the deployment platform, operations process, or managed services.

Current status: the application is portfolio-ready, but a real production launch is not approved until every unchecked platform control below has recorded evidence for the target environment.

## Application Controls Already Implemented

- Fail-closed production profile selection and required production configuration validation.
- OAuth2 Resource Server issuer, audience, authorized-party, role, scope, and ownership checks.
- Idempotent payment and refund workflows backed by durable database records.
- Append-only ledger rows and append-only, HMAC-sealed audit events.
- Reconciliation reports for local payment, refund, provider, and ledger consistency.
- Local/test rate limiting plus production guardrails requiring gateway/WAF or Redis-backed enforcement.
- Authenticated operational Actuator endpoints for metrics and Prometheus.
- CI gates for tests, architecture rules, Checkstyle, 80% line coverage, CodeQL, SBOM generation, secret scanning, container scanning, dependency review, and scheduled OWASP dependency scans.

## Required Platform Controls

For each checkbox, record the environment, owner, evidence link or artifact path, verification date, reviewer, and follow-up ticket if the control is not fully satisfied.

- [ ] TLS termination and transport security
  - Evidence fields: ingress/load balancer name; TLS policy version; certificate id and expiry; HTTP-to-HTTPS redirect proof; HSTS setting if browser-facing; latest external or internal TLS scan result.
- [ ] WAF and gateway rate limiting
  - Evidence fields: gateway/WAF policy id; route-level limits for checkout, payment, refund, webhook, profile, and stock-stream routes; body-size limits matching or below application limits; trusted proxy CIDRs; test evidence for expected `429` responses.
- [ ] Centralized logging and SIEM
  - Evidence fields: log sink or dataset name; retention period; parser fields for request id, subject, action, resource, result, and client identity; alert rule ids; sample correlated API/audit/security event links.
- [ ] Secrets manager
  - Evidence fields: secret store name; IAM role or workload identity allowed to read each secret; mapping from secret names to required environment variables; access-review date; proof secrets are not stored in images, Compose files, or committed `.env` files.
- [ ] Database role separation
  - Evidence fields: separate migration/admin and runtime application roles; granted privileges for each role; proof the application role cannot run DDL or mutate append-only ledger/audit rows directly; latest privilege-review artifact.
- [ ] Backup and restore testing
  - Evidence fields: backup policy id; encryption setting; backup frequency; latest restore-test timestamp; restored environment name; measured restore duration; data integrity checks after restore; resulting RPO measurement.
- [ ] Incident response
  - Evidence fields: incident runbook link; severity matrix; escalation contacts; on-call schedule; payment-provider escalation path; tabletop or game-day date; evidence that audit, reconciliation, and SIEM procedures were exercised.
- [ ] Vulnerability management
  - Evidence fields: SBOM artifact location; SCA/container/SAST scan results; CVE triage owner; patch SLA by severity; accepted-risk exception list; latest dependency and image refresh evidence.
- [ ] Audit log retention and external anchoring
  - Evidence fields: retention duration; immutable/WORM storage or external signing/anchoring mechanism; legal hold process; retrieval test result; audit hash/signature verification result for retained records.
- [ ] Key and secret rotation
  - Evidence fields: inventory of OAuth2 signing keys, API secrets, webhook secrets, audit HMAC secret, database passwords, and TLS certificates; rotation cadence; last rotation date; dual-key or rollout plan; rollback proof.
- [ ] Zero-downtime migrations
  - Evidence fields: migration release checklist; backward-compatible expand/contract plan; application version compatibility notes; canary or blue/green deployment evidence; rollback plan; Flyway validation output.
- [ ] Monitoring and alerting
  - Evidence fields: SLOs; dashboard links; alert ids for error rate, latency, saturation, payment/provider failures, webhook failures, reconciliation mismatches, audit verification failures, rate-limit spikes, and database health.
- [ ] Disaster recovery objectives
  - Evidence fields: approved RTO and RPO; regional or zone-failure design; dependency recovery order; failover exercise date; measured failover and failback time; known gaps and remediation tickets.

## Go/No-Go Rule

Do not describe a deployment as production-ready until the target environment has evidence for every required platform control. If a control is intentionally deferred, record the risk owner, expiry date, compensating controls, and decision authority before launch.
