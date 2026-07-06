# Local Keycloak Setup

This project uses Keycloak as the local OAuth2/OIDC issuer for the Spring Security resource server.

## Start Services

```powershell
docker compose up -d postgres keycloak
```

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
  -Uri "http://localhost:8080/allUserInfo" `
  -Headers @{ Authorization = "Bearer $accessToken" }
```

## Token Validation Settings

Local profile defaults:

- Issuer: `http://localhost:8081/realms/ecommerce`
- JWK set: `http://localhost:8081/realms/ecommerce/protocol/openid-connect/certs`

Both are configured so the API validates the token issuer while avoiding a hard startup dependency on Keycloak availability.
