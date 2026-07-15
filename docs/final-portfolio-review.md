# Final Portfolio Review

Date: 2026-07-15

Purpose: final readiness pass before sharing the project with a bank recruiter or using it in an interview.

## Result

The repository is ready to share as a banking-grade backend portfolio project.

The final review found no blocking issues. The build gate passes, the security/race-condition pass found no high-risk defects, public docs point to the current API shape, and the main security/architecture risks are either tested, hardened, or documented as deliberate tradeoffs.

## Checklist

| Check | Result | Evidence |
| --- | --- | --- |
| Fresh clone setup works | Pass | Cloned the repository into a temporary directory and ran `.\mvnw.cmd -B -ntp -DskipTests compile`. Result: build success. The temporary clone was removed after verifying it was under the OS temp directory. |
| README demo is coherent | Pass with local-volume caveat | Demo script was reviewed against `compose.yaml`, the Keycloak realm import, `application-local.properties`, and `demo-data.sql`. The documented clean reset uses `docker compose down -v`; it was not executed during this review because an existing local Docker volume was present. The same checkout, payment, refund, ledger, audit, and reconciliation flow is covered by the green integration suite. |
| Tests pass | Pass | `.\mvnw.cmd -B -ntp verify` completed successfully with 389 tests, 0 failures, 0 errors, 0 Checkstyle violations, passing architecture tests, and passing JaCoCo coverage checks. |
| No secrets are committed | Pass | Searched tracked project files, excluding `.git`, `target`, and `agent`, for common private key, AWS, Stripe, webhook, GitHub, and Slack token patterns. No matches found. Demo credentials are local-only and documented as safe defaults. |
| No agent files are tracked | Pass | `git ls-files agent` produced no output. The `agent/` directory is ignored by `.gitignore`. |
| No controller returns JPA entities | Pass | `ArchitectureTest` runs in the full Maven gate and enforces controller/entity boundary rules. |
| No money path uses `double` or `float` | Pass | `rg "\b(double|float)\b" src\main\java src\test\java` found only architecture/test helpers and `OutboxMetrics` lag calculation. Money-sensitive packages are protected by architecture tests and use `Money`/`BigDecimal` with explicit currency. |
| No obvious security shortcuts remain | Pass | OAuth2 Resource Server validation, role/scope checks, subject-based ownership, idempotency, audit hashing, constant-time fake-provider webhook secret checks, and threat modeling are in place. Residual risks are documented in `docs/threat-model.md`. |
| Race-condition pass | Pass | Cart mutation/checkout and payment/refund state transitions use transactional persistence methods with explicit root-row `for update` lock acquisition before graph fetching. Concurrency coverage includes inventory reservation, payment multi-attempt, checkout, refund, ledger, outbox, and admin stock-update flows. |
| Repository looks intentional | Pass | README, ADRs, OpenAPI documentation, CI quality gates, threat model, local Keycloak docs, and final review evidence are present. |

## Known Tradeoffs

- The fake payment provider is deliberate. It keeps demos and tests deterministic while the payment provider port keeps a future Stripe adapter isolated from core workflows.
- Old compatibility aliases have been removed in favor of canonical REST routes.
- SSE stock updates use in-memory fan-out. The README documents Redis Pub/Sub or Kafka as the multi-instance upgrade path.
- The README clean demo command resets Docker volumes so Keycloak imports the current realm. That is appropriate for a clean demo but should be run intentionally.
- Rate limiting is enforced in-process for the local/demo profile. A production multi-node deployment should put the same policy at the gateway/WAF layer or back it with a shared store such as Redis.

## Verification Commands

```powershell
.\mvnw.cmd -B -ntp verify
git ls-files agent
rg -n --hidden --glob '!.git/**' --glob '!target/**' --glob '!agent/**' "BEGIN (RSA |DSA |EC |OPENSSH |PGP )?PRIVATE KEY|AKIA[0-9A-Z]{16}|sk_live_[A-Za-z0-9]+|pk_live_[A-Za-z0-9]+|whsec_[A-Za-z0-9]+|ghp_[A-Za-z0-9_]+|xox[baprs]-[A-Za-z0-9-]+" .
rg -n "\b(double|float)\b" src\main\java src\test\java --glob "!target/**"
```
