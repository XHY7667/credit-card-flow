# Merchant API

## Overview

The Merchant API manages merchants that participate in CreditCardFlow transactions. It supports the initial CRUD lifecycle for registering, retrieving, updating, listing, and deleting fictional merchant records.

## Base URL

```text
http://localhost:8080/api/v1/merchants
```

Requests and responses use JSON unless an endpoint has no body.

## Merchant data model

| Field | Type | Description |
| --- | --- | --- |
| `id` | number | Server-generated merchant identifier. |
| `merchantCode` | string | Unique, immutable business identifier for the merchant. |
| `legalName` | string | Merchant's registered legal name. |
| `displayName` | string | Merchant name displayed to users and operators. |
| `merchantCategoryCode` | string | Four-digit merchant category code (MCC). |
| `countryCode` | string | Two-letter uppercase country code. |
| `status` | string | Current merchant status. |
| `createdAt` | string | Creation timestamp in ISO-8601 format. |
| `updatedAt` | string | Most recent update timestamp in ISO-8601 format. |

### Merchant statuses

- `ACTIVE`
- `SUSPENDED`
- `CLOSED`

## Endpoints

### Create a merchant

- **Purpose:** Register a new merchant.
- **Request:** `POST /api/v1/merchants`
- **Success status:** `201 Created`
- **Relevant errors:** `400 Bad Request` for validation failures; `409 Conflict` when the merchant code already exists.

Request body and example request:

```json
{
  "merchantCode": "M300001",
  "legalName": "Blue Harbor Retail LLC",
  "displayName": "Blue Harbor Market",
  "merchantCategoryCode": "5411",
  "countryCode": "US",
  "status": "ACTIVE"
}
```

Example success response:

```json
{
  "id": 1,
  "merchantCode": "M300001",
  "legalName": "Blue Harbor Retail LLC",
  "displayName": "Blue Harbor Market",
  "merchantCategoryCode": "5411",
  "countryCode": "US",
  "status": "ACTIVE",
  "createdAt": "2026-08-06T18:30:00Z",
  "updatedAt": "2026-08-06T18:30:00Z"
}
```

### Get a merchant by ID

- **Purpose:** Retrieve one merchant by its server-generated identifier.
- **Request:** `GET /api/v1/merchants/{id}`
- **Request body:** None.
- **Example request:** `GET /api/v1/merchants/1`
- **Success status:** `200 OK`
- **Relevant errors:** `404 Not Found` when no merchant has the requested ID.

Example success response:

```json
{
  "id": 1,
  "merchantCode": "M300001",
  "legalName": "Blue Harbor Retail LLC",
  "displayName": "Blue Harbor Market",
  "merchantCategoryCode": "5411",
  "countryCode": "US",
  "status": "ACTIVE",
  "createdAt": "2026-08-06T18:30:00Z",
  "updatedAt": "2026-08-06T18:30:00Z"
}
```

### Get all merchants

- **Purpose:** Retrieve all merchants.
- **Request:** `GET /api/v1/merchants`
- **Request body:** None.
- **Example request:** `GET /api/v1/merchants`
- **Success status:** `200 OK`
- **Relevant errors:** No merchant-specific error status; an empty data set returns an empty array.

Example success response:

```json
[
  {
    "id": 1,
    "merchantCode": "M300001",
    "legalName": "Blue Harbor Retail LLC",
    "displayName": "Blue Harbor Market",
    "merchantCategoryCode": "5411",
    "countryCode": "US",
    "status": "ACTIVE",
    "createdAt": "2026-08-06T18:30:00Z",
    "updatedAt": "2026-08-06T18:30:00Z"
  }
]
```

### Update a merchant

- **Purpose:** Replace the mutable business details of an existing merchant.
- **Request:** `PUT /api/v1/merchants/{id}`
- **Example request path:** `PUT /api/v1/merchants/1`
- **Success status:** `200 OK`
- **Relevant errors:** `400 Bad Request` for validation failures; `404 Not Found` when no merchant has the requested ID.

The request does not contain `merchantCode`. A merchant code cannot be changed through the update API.

Request body and example request:

```json
{
  "legalName": "Blue Harbor Mercantile LLC",
  "displayName": "Blue Harbor Mercantile",
  "merchantCategoryCode": "5399",
  "countryCode": "CA",
  "status": "SUSPENDED"
}
```

Example success response:

```json
{
  "id": 1,
  "merchantCode": "M300001",
  "legalName": "Blue Harbor Mercantile LLC",
  "displayName": "Blue Harbor Mercantile",
  "merchantCategoryCode": "5399",
  "countryCode": "CA",
  "status": "SUSPENDED",
  "createdAt": "2026-08-06T18:30:00Z",
  "updatedAt": "2026-08-06T18:45:00Z"
}
```

### Delete a merchant

- **Purpose:** Delete an existing merchant.
- **Request:** `DELETE /api/v1/merchants/{id}`
- **Request body:** None.
- **Example request:** `DELETE /api/v1/merchants/1`
- **Success status:** `204 No Content`
- **Example success response:** Empty response body.
- **Relevant errors:** `404 Not Found` when no merchant has the requested ID.

Merchant deletion is currently a physical delete for the initial CRUD phase. It may later evolve to lifecycle-based closure when transaction relationships are added.

## Validation rules

| Field | Rules |
| --- | --- |
| `merchantCode` | Required; maximum 20 characters; uppercase letters, digits, underscores, or hyphens only. |
| `legalName` | Required; maximum 150 characters. |
| `displayName` | Required; maximum 100 characters. |
| `merchantCategoryCode` | Required and exactly four digits. |
| `countryCode` | Required and exactly two uppercase letters. |
| `status` | Required and must be a supported merchant status. |

The same corresponding rules apply to update fields. `merchantCode` is omitted from updates because it is immutable after creation.

## Error responses

Errors use the standard `ApiErrorResponse` structure:

| Field | Type | Description |
| --- | --- | --- |
| `timestamp` | string | Error timestamp in ISO-8601 format. |
| `status` | number | Numeric HTTP status. |
| `error` | string | HTTP error label. |
| `message` | string | Safe summary of the error. |
| `path` | string | Request URI that produced the error. |
| `validationErrors` | object or null | Field-to-message map for validation errors; otherwise `null`. |

Stack traces, rejected values, and exception class names are not included.

### 400 validation error

```json
{
  "timestamp": "2026-08-06T18:50:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/api/v1/merchants",
  "validationErrors": {
    "merchantCode": "Merchant code must not be blank",
    "merchantCategoryCode": "Merchant category code must contain exactly four digits",
    "countryCode": "Country code must contain exactly two uppercase letters"
  }
}
```

### 404 merchant not found

```json
{
  "timestamp": "2026-08-06T18:51:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Merchant not found with id: 999999",
  "path": "/api/v1/merchants/999999",
  "validationErrors": null
}
```

### 409 duplicate merchant code

```json
{
  "timestamp": "2026-08-06T18:52:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Merchant code already exists: M300001",
  "path": "/api/v1/merchants",
  "validationErrors": null
}
```

