# Security Architecture

For endpoint details, see the [API reference](api-reference.md). The full login sequence is shown in [data-flow.md](data-flow.md#authentication-flow).

## Security Boundary

CreditCardFlow owns authentication and authorization. The API Gateway preserves the incoming `Authorization` header and forwards the request, but it does not parse credentials, validate JWTs, or enforce roles.

The root application uses a stateless Spring Security filter chain:

- Session creation policy is `STATELESS`.
- CSRF is disabled.
- Form login is disabled.
- HTTP Basic is disabled.
- OAuth2 Resource Server JWT validation is enabled.
- Unmatched requests use a deny-all fallback.

## Authentication Flow

```text
POST /api/v1/auth/login
  -> AuthenticationController
  -> AuthenticationService
  -> AuthenticationManager
  -> AppUserDetailsService
  -> AppUserRepository / PostgreSQL
  -> PasswordEncoder verification
  -> JwtEncoder
  -> bearer-token response
```

`AppUserDetailsService` loads a persistent `AppUser` by unique username and maps its role to `ROLE_USER` or `ROLE_ADMIN`. Spring Security verifies the supplied password against the stored encoded value and rejects unknown, disabled, or invalid users.

No registration, refresh-token, logout-state, SSO, MFA, or external identity-provider flow is implemented.

## JWT Design

JWTs are RSA-signed and contain these claims:

| Claim | Meaning |
|---|---|
| `iss` | Fixed issuer `creditcardflow` |
| `sub` | Authenticated username |
| `iat` | Issue time |
| `exp` | Expiration time |
| `role` | `USER` or `ADMIN` |

The access-token lifetime is 1,800 seconds (30 minutes). Resource-server validation verifies the RSA signature, standard JWT validity rules, and issuer. The `role` claim is mapped with prefix `ROLE_` for authorization decisions.

An RSA-2048 key pair and key ID are generated when CreditCardFlow starts. The keys are not persisted, so restarting the application invalidates existing tokens. Production key rotation, KMS/HSM integration, and externalized signing keys are not implemented.

## Authorization Matrix

### Public

| Method | Path |
|---|---|
| POST | `/api/v1/auth/login` |
| GET | `/actuator/health` |

### USER or ADMIN

| Method | Path |
|---|---|
| GET | `/api/v1/merchants` |
| GET | `/api/v1/merchants/{id}` |
| GET | `/api/v1/card-accounts/{accountNumber}` |
| GET | `/api/v1/cards/{cardReference}` |
| POST | `/api/v1/authorizations` |
| GET | `/api/v1/authorizations/{authorizationReference}` |
| POST | `/api/v1/reversals` |
| GET | `/api/v1/reversals/{reversalReference}` |
| POST | `/api/v1/clearings` |
| GET | `/api/v1/clearings/{clearingReference}` |

These routes match the authenticated `/api/v1/**` fallback after the ADMIN-specific rules are evaluated.

### ADMIN only

| Method | Path |
|---|---|
| POST | `/api/v1/merchants` |
| PUT | `/api/v1/merchants/{id}` |
| DELETE | `/api/v1/merchants/{id}` |
| POST | `/api/v1/card-accounts` |
| PUT | `/api/v1/card-accounts/{accountNumber}` |
| POST | `/api/v1/cards` |
| PUT | `/api/v1/cards/{cardReference}` |
| GET | `/actuator/info` |
| GET | `/actuator/metrics` |
| GET | `/actuator/metrics/{metricName}` |

`/error` is permitted for framework error dispatch. Any request not matched by the explicit rules is denied.

## Password Handling

- Passwords are stored only in the `app_users.password_hash` column.
- `PasswordEncoderFactories.createDelegatingPasswordEncoder()` supplies the configured encoder.
- Login compares a supplied password with the encoded stored value.
- No plaintext application credential is committed.
- No default AppUser is seeded.

Database credentials and application-user credentials are separate concerns; an infrastructure database password does not provide API access.

## 401 and 403

- **401 Unauthorized** means authentication is absent or unsuccessful, such as missing/invalid bearer tokens, wrong passwords, unknown users, or disabled users.
- **403 Forbidden** means the JWT is valid but its role is not permitted for the requested operation, such as a USER attempting an ADMIN-only mutation or metrics request.

The dedicated integration tests exercise the real filter chain for login, token claims, invalid tokens, anonymous access, USER/ADMIN authorization, and actuator protection.

## Explicit Non-Features

The security design does not claim mTLS, an OAuth authorization server, refresh tokens, SSO, MFA, KMS, HSM-backed keys, or an external identity provider.
