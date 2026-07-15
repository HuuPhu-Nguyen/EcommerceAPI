# Local Keycloak Setup

This project uses Keycloak as the local OAuth2/OIDC issuer for the Spring Security resource server.

## Start Services

```powershell
docker compose up -d postgres keycloak
```

For a clean demo import of the current realm, run `docker compose down -v` first. That command deletes the local PostgreSQL and Keycloak demo volumes, so run it intentionally.

Keycloak is available at `http://localhost:8081`.

Admin console:

- Username: `admin`
- Password: `admin`

The imported realm is `ecommerce`.

## Demo Users

| Username | Password | Roles |
| --- | --- | --- |
| `customer@example.com` | `customer-password` | `customer` |
| `admin@example.com` | `admin-password` | `admin` |
| `auditor@example.com` | `auditor-password` | `auditor` |

## Get A Local Access Token

The `ecommerce-web` client has direct access grants enabled only for local CLI demos. Browser clients should use authorization code with PKCE.

```powershell
$tokenResponse = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8081/realms/ecommerce/protocol/openid-connect/token" `
  -ContentType "application/x-www-form-urlencoded" `
  -Body @{
    grant_type = "password"
    client_id = "ecommerce-web"
    username = "customer@example.com"
    password = "customer-password"
  }

$accessToken = $tokenResponse.access_token
```

Use the token against protected API endpoints:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/customer/profile/me" `
  -Headers @{ Authorization = "Bearer $accessToken" }
```

Admin and auditor users can call review endpoints such as `/admin/customer-profiles`, `/ledger/transactions`, `/audit/events`, and `/reconciliation/report` when their token contains the matching role and scope. Starting `/reconciliation/runs` requires an admin token with `reconciliation:run`.

## Token Validation Settings

Local profile defaults:

- Issuer: `http://localhost:8081/realms/ecommerce`
- JWK set: `http://localhost:8081/realms/ecommerce/protocol/openid-connect/certs`
- Required audience: `ecommerce-api`
- Trusted resource client roles: `resource_access.ecommerce-api.roles`
- Allowed authorized party: `ecommerce-web`

Both are configured so the API validates the token issuer while avoiding a hard startup dependency on Keycloak availability.

The realm import also includes local API scopes used by method security:

- `profile:read`
- `profile:write`
- `product:read`
- `cart:read`
- `cart:write`
- `checkout:write`
- `payment:create`
- `payment:refund`
- `stock:stream`
- `audit:read`
- `ledger:read`
- `product:write`
- `reconciliation:run`
- `user:read`
