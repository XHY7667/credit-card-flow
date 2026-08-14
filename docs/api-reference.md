# CreditCardFlow API Reference

This document summarizes the implemented HTTP surface. For system context, see the [project README](../README.md), [architecture](architecture.md), and [data flows](data-flow.md).

In the containerized architecture, clients normally call `api-gateway` on port 8082. The Gateway preserves `/api/v1/**` paths and forwards them to CreditCardFlow on port 8080.

Access labels used below:

- **PUBLIC**: no bearer token required.
- **USER or ADMIN**: a valid JWT for either application role is required.
- **ADMIN only**: a valid JWT with role `ADMIN` is required.

## Authentication

| Method | Path | Access | Purpose | Success |
|---|---|---|---|---:|
| POST | `/api/v1/auth/login` | PUBLIC | Authenticate a persistent application user and issue a JWT | 200 |

Request:

```json
{
  "username": "synthetic-admin",
  "password": "<synthetic-password>"
}
```

Response:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 1800
}
```

Invalid credentials, an unknown user, or a disabled user produce 401. No registration endpoint or default application user is implemented.

## Merchant

| Method | Path | Access | Purpose | Success |
|---|---|---|---|---:|
| POST | `/api/v1/merchants` | ADMIN only | Create a merchant | 201 |
| GET | `/api/v1/merchants` | USER or ADMIN | List merchants | 200 |
| GET | `/api/v1/merchants/{id}` | USER or ADMIN | Get a merchant by database ID | 200 |
| PUT | `/api/v1/merchants/{id}` | ADMIN only | Update mutable merchant details | 200 |
| DELETE | `/api/v1/merchants/{id}` | ADMIN only | Delete a merchant | 204 |

Create request:

```json
{
  "merchantCode": "MERCHANT-1001",
  "legalName": "Synthetic Market LLC",
  "displayName": "Synthetic Market",
  "merchantCategoryCode": "5411",
  "countryCode": "US",
  "status": "ACTIVE"
}
```

Create response:

```json
{
  "id": 1,
  "merchantCode": "MERCHANT-1001",
  "legalName": "Synthetic Market LLC",
  "displayName": "Synthetic Market",
  "merchantCategoryCode": "5411",
  "countryCode": "US",
  "status": "ACTIVE",
  "createdAt": "2026-08-13T12:00:00Z",
  "updatedAt": "2026-08-13T12:00:00Z"
}
```

## CardAccount

| Method | Path | Access | Purpose | Success |
|---|---|---|---|---:|
| POST | `/api/v1/card-accounts` | ADMIN only | Create a card account and credit line | 201 |
| GET | `/api/v1/card-accounts/{accountNumber}` | USER or ADMIN | Get an account by account number | 200 |
| PUT | `/api/v1/card-accounts/{accountNumber}` | ADMIN only | Update credit limit and status | 200 |

Create request:

```json
{
  "accountNumber": "ACC-1001",
  "creditLimit": 5000.00,
  "currencyCode": "USD"
}
```

Create response:

```json
{
  "id": 1,
  "accountNumber": "ACC-1001",
  "creditLimit": 5000.00,
  "currentBalance": 0.00,
  "availableCredit": 5000.00,
  "currencyCode": "USD",
  "status": "ACTIVE",
  "version": 0,
  "createdAt": "2026-08-13T12:00:00Z",
  "updatedAt": "2026-08-13T12:00:00Z"
}
```

Credit-limit updates cannot reduce the limit below already committed exposure.

## Card

| Method | Path | Access | Purpose | Success |
|---|---|---|---|---:|
| POST | `/api/v1/cards` | ADMIN only | Issue a card for an active card account | 201 |
| GET | `/api/v1/cards/{cardReference}` | USER or ADMIN | Get a card by reference | 200 |
| PUT | `/api/v1/cards/{cardReference}` | ADMIN only | Update card status | 200 |

Create request:

```json
{
  "cardReference": "CARD-1001",
  "lastFour": "4242",
  "expirationMonth": 12,
  "expirationYear": 2030,
  "cardAccountNumber": "ACC-1001"
}
```

Create response:

```json
{
  "id": 1,
  "cardReference": "CARD-1001",
  "lastFour": "4242",
  "expirationMonth": 12,
  "expirationYear": 2030,
  "status": "ACTIVE",
  "cardAccountNumber": "ACC-1001",
  "createdAt": "2026-08-13T12:00:00Z",
  "updatedAt": "2026-08-13T12:00:00Z"
}
```

Only synthetic last-four values are modeled; this API is not intended for real cardholder data.

## Authorization

| Method | Path | Access | Purpose | Success |
|---|---|---|---|---:|
| POST | `/api/v1/authorizations` | USER or ADMIN | Approve or decline a full authorization request | 201 |
| GET | `/api/v1/authorizations/{authorizationReference}` | USER or ADMIN | Get an authorization by reference | 200 |

Create request:

```json
{
  "authorizationReference": "AUTH-1001",
  "cardReference": "CARD-1001",
  "merchantCode": "MERCHANT-1001",
  "amount": 125.75,
  "currencyCode": "USD",
  "authorizationType": "PURCHASE",
  "channel": "ECOMMERCE"
}
```

Create response:

```json
{
  "id": 1,
  "authorizationReference": "AUTH-1001",
  "cardReference": "CARD-1001",
  "merchantCode": "MERCHANT-1001",
  "amount": 125.75,
  "currencyCode": "USD",
  "authorizationType": "PURCHASE",
  "channel": "ECOMMERCE",
  "status": "APPROVED",
  "createdAt": "2026-08-13T12:05:00Z",
  "updatedAt": "2026-08-13T12:05:00Z"
}
```

A structurally valid authorization may return `DECLINED` with 201 when eligibility or available credit is insufficient. Partial approval is not implemented.

## Reversal

| Method | Path | Access | Purpose | Success |
|---|---|---|---|---:|
| POST | `/api/v1/reversals` | USER or ADMIN | Fully reverse an approved authorization | 201 |
| GET | `/api/v1/reversals/{reversalReference}` | USER or ADMIN | Get a reversal by reference | 200 |

The POST requires an `Idempotency-Key` request header.

```http
Idempotency-Key: synthetic-reversal-key-1001
```

Create request:

```json
{
  "reversalReference": "REV-1001",
  "authorizationReference": "AUTH-1001",
  "amount": 125.75
}
```

Create response:

```json
{
  "id": 1,
  "reversalReference": "REV-1001",
  "authorizationReference": "AUTH-1001",
  "amount": 125.75,
  "status": "COMPLETED",
  "createdAt": "2026-08-13T12:10:00Z",
  "updatedAt": "2026-08-13T12:10:00Z"
}
```

The amount must equal the full approved authorization amount. Replaying the same logical request and idempotency key returns the existing result with 201 and does not release credit again.

## Clearing

| Method | Path | Access | Purpose | Success |
|---|---|---|---|---:|
| POST | `/api/v1/clearings` | USER or ADMIN | Post a matching approved authorization | 201 |
| GET | `/api/v1/clearings/{clearingReference}` | USER or ADMIN | Get a clearing by reference | 200 |

Create request:

```json
{
  "clearingReference": "CLR-1001",
  "authorizationReference": "AUTH-1001",
  "amount": 125.75,
  "currencyCode": "USD"
}
```

Create response:

```json
{
  "id": 1,
  "clearingReference": "CLR-1001",
  "authorizationReference": "AUTH-1001",
  "amount": 125.75,
  "currencyCode": "USD",
  "status": "POSTED",
  "createdAt": "2026-08-13T12:15:00Z",
  "updatedAt": "2026-08-13T12:15:00Z"
}
```

Amount and currency must match the approved authorization. Posting increases `currentBalance`; it does not reduce `availableCredit` a second time.

## Actuator

Only the root application endpoints are shown here.

| Method | Path | Access | Purpose | Success |
|---|---|---|---|---:|
| GET | `/actuator/health` | PUBLIC | Application health | 200 |
| GET | `/actuator/info` | ADMIN only | Application information | 200 |
| GET | `/actuator/metrics` | ADMIN only | Available metric names | 200 |
| GET | `/actuator/metrics/{metricName}` | ADMIN only | Individual metric details | 200 |

## HTTP Outcomes and Errors

| Status | Meaning in this API |
|---:|---|
| 200 | Successful login, read, or update |
| 201 | Successful creation, including approved/declined authorization and reversal replay |
| 204 | Successful merchant deletion |
| 400 | Bean Validation failure, missing reversal header, invalid expiration/limit, or invalid business input |
| 401 | Missing/invalid bearer token or authentication failure |
| 403 | Authenticated user lacks the required ADMIN role |
| 404 | Referenced resource not found |
| 409 | Duplicate reference, idempotency conflict, or disallowed transaction state/amount/currency |

Application exceptions and validation failures use `ApiErrorResponse`:

```json
{
  "timestamp": "2026-08-13T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/api/v1/authorizations",
  "validationErrors": {
    "amount": "Amount must be at least 0.01"
  }
}
```

For non-validation application errors, `validationErrors` is `null`. Authentication and authorization failures are produced by the Spring Security filter chain and are not documented as guaranteed to use this application error body.
