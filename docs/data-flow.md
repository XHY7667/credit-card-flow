# CreditCardFlow Data Flows

## Authentication Flow

```mermaid
sequenceDiagram
    actor Client
    participant Gateway as api-gateway :8082
    participant Controller as AuthenticationController
    participant AuthService as AuthenticationService
    participant Manager as AuthenticationManager
    participant UserDetails as AppUserDetailsService
    participant Database as PostgreSQL :5432
    participant Encoder as PasswordEncoder
    participant JWT as JwtEncoder

    Client->>Gateway: POST /api/v1/auth/login
    Gateway->>Controller: Forward unchanged path and credentials
    Controller->>AuthService: login(request)
    AuthService->>Manager: authenticate(username, password)
    Manager->>UserDetails: loadUserByUsername(username)
    UserDetails->>Database: SELECT app_users by username
    Database-->>UserDetails: Encoded password, role, enabled state
    UserDetails-->>Manager: UserDetails
    Manager->>Encoder: Verify supplied password
    Encoder-->>Manager: Password match or failure
    Manager-->>AuthService: Authenticated principal and authority
    AuthService->>JWT: Encode issuer, subject, role, issued/expiry claims
    JWT-->>AuthService: RSA-signed access token
    AuthService-->>Controller: Bearer login response
    Controller-->>Gateway: HTTP response
    Gateway-->>Client: Access token
```

The Gateway does not authenticate the credentials or validate the resulting JWT. CreditCardFlow owns authentication and security. Later API requests send `Authorization: Bearer <token>` through the Gateway unchanged; CreditCardFlow's OAuth2 Resource Server validates the signature, standard claims, and issuer, then maps the `role` claim to a USER or ADMIN authority.

The in-memory RSA key pair is generated at application startup, and issued access tokens have a 30-minute lifetime. Restarting CreditCardFlow invalidates previously issued tokens.

## Authorization Flow

```mermaid
sequenceDiagram
    actor Client
    participant Gateway as api-gateway :8082
    participant Controller as AuthorizationController
    participant Service as AuthorizationService
    participant AuthRepo as AuthorizationRepository
    participant CardRepo as CardRepository
    participant MerchantRepo as MerchantRepository
    participant Account as CardAccount
    participant Database as PostgreSQL :5432

    Client->>Gateway: POST /api/v1/authorizations + bearer token
    Gateway->>Controller: Forward /api/v1/authorizations
    Controller->>Service: createAuthorization(request)
    Service->>AuthRepo: Check authorization reference
    AuthRepo->>Database: Lookup by unique reference
    Service->>CardRepo: Lookup Card by card reference
    CardRepo->>Database: SELECT Card
    Service->>MerchantRepo: Lookup Merchant by merchant code
    MerchantRepo->>Database: SELECT Merchant
    Service->>Account: Inspect linked CardAccount
    Service->>Service: Check card/account/merchant status and card expiration
    alt Eligible and sufficient available credit
        Service->>Account: reserveCredit(full amount)
        Account-->>Service: availableCredit reduced
        Service->>AuthRepo: Save APPROVED Authorization
    else Ineligible or insufficient available credit
        Service->>AuthRepo: Save DECLINED Authorization
    end
    AuthRepo->>Database: Commit Authorization and any account mutation
    Service-->>Controller: Authorization response
    Controller-->>Gateway: 201 Created
    Gateway-->>Client: APPROVED or DECLINED
```

Bean Validation checks the request shape, positive two-decimal amount, and currency-code format. Authorization eligibility checks card, card-account, and merchant state plus expiration. The service does not implement partial approvals: it reserves the complete requested amount or declines the authorization.

Financial effect:

| State | `availableCredit` | `currentBalance` |
|---|---|---|
| Before request | Existing value | Existing value |
| Approved | Decreased by full authorization amount | Unchanged |
| Declined | Unchanged | Unchanged |

## Reversal Flow

```mermaid
sequenceDiagram
    actor Client
    participant Gateway as api-gateway :8082
    participant Controller as ReversalController
    participant Service as ReversalService
    participant ReversalRepo as AuthorizationReversalRepository
    participant AuthRepo as AuthorizationRepository
    participant Account as CardAccount
    participant Database as PostgreSQL :5432

    Client->>Gateway: POST /api/v1/reversals + idempotency key
    Gateway->>Controller: Forward request and headers
    Controller->>Service: createReversal(key, request)
    Service->>ReversalRepo: Lookup idempotency key
    ReversalRepo->>Database: SELECT by unique idempotency_key
    alt Matching replay exists
        Service-->>Controller: Existing COMPLETED reversal
    else Key conflicts with existing request
        Service-->>Controller: Idempotency conflict
    else No replay exists
        Service->>ReversalRepo: Check reversal reference
        Service->>AuthRepo: Lookup Authorization
        AuthRepo->>Database: SELECT Authorization
        Service->>Service: Require APPROVED and exact full amount
        Service->>Account: releaseCredit(amount)
        Service->>AuthRepo: Mark Authorization REVERSED
        Service->>ReversalRepo: Save Reversal COMPLETED
        ReversalRepo->>Database: Commit account, authorization, and reversal
        Service-->>Controller: COMPLETED reversal
    end
    Controller-->>Gateway: HTTP response
    Gateway-->>Client: Created result or error
```

Only an `APPROVED` authorization can be reversed, and the reversal amount must be numerically equal to the full authorization amount. A successful reversal increases `availableCredit` by that amount and leaves `currentBalance` unchanged. The authorization, account mutation, and reversal are committed in one transaction.

Application replay checks are backed by unique database constraints on `idempotency_key` and `reversal_reference`. Replaying the same logical request does not release credit or create another reversal.

## Clearing Flow

```mermaid
sequenceDiagram
    actor Client
    participant Gateway as api-gateway :8082
    participant Controller as ClearingController
    participant Service as ClearingService
    participant ClearingRepo as ClearingRepository
    participant AuthRepo as AuthorizationRepository
    participant Account as CardAccount
    participant Database as PostgreSQL :5432
    participant Events as Spring ApplicationEventPublisher
    participant Publisher as AFTER_COMMIT ClearingPostedKafkaPublisher
    participant KafkaTemplate
    participant Kafka as creditcardflow.transaction-events
    participant Consumer as clearing-event-service

    Client->>Gateway: POST /api/v1/clearings + bearer token
    Gateway->>Controller: Forward /api/v1/clearings
    Controller->>Service: createClearing(request)

    rect rgb(235, 245, 255)
        Note over Service,Database: Database transaction
        Service->>ClearingRepo: Check clearing reference
        ClearingRepo->>Database: Lookup unique reference
        Service->>AuthRepo: Lookup Authorization
        AuthRepo->>Database: SELECT Authorization and linked CardAccount
        Service->>Service: Require APPROVED status
        Service->>Service: Require matching amount and currency
        Service->>Account: postClearing(amount)
        Note right of Account: currentBalance increases<br/>availableCredit stays unchanged
        Service->>AuthRepo: Mark Authorization CLEARED
        Service->>ClearingRepo: Save Clearing POSTED
        Service->>Events: Publish ClearingPostedEvent
        Service->>Database: COMMIT account, authorization, and clearing
    end

    Note over Database,Publisher: Commit completes before Kafka send
    Events-->>Publisher: Deliver event AFTER_COMMIT
    Publisher->>KafkaTemplate: send(clearingReference, event)
    KafkaTemplate->>Kafka: Headerless JSON ClearingPostedEvent
    Kafka-->>Consumer: Consume asynchronously
    Consumer->>Consumer: Log eventId, clearingReference, status

    Service-->>Controller: POSTED clearing response
    Controller-->>Gateway: 201 Created
    Gateway-->>Client: Clearing response
```

Clearing converts pending exposure into posted balance:

- `currentBalance` increases by the clearing amount.
- `availableCredit` remains unchanged because the authorization already reserved it.
- Authorization changes from `APPROVED` to `CLEARED`.
- Clearing is persisted as `POSTED`.

The account, authorization, and clearing changes share one database transaction. The Spring event is raised inside that transaction, but the Kafka publisher listens with `@TransactionalEventListener(AFTER_COMMIT)`. A database rollback therefore prevents the Kafka send.

After commit, immediate or asynchronous Kafka send failures are logged and contained. They do not roll back the committed clearing. The implementation does not provide a transactional outbox or exactly-once delivery guarantee.

## Containerized Runtime Flow

```mermaid
flowchart LR
    External[External client]
    Gateway[api-gateway<br/>localhost:8082]
    Root[CreditCardFlow<br/>creditcardflow:8080]
    Database[(PostgreSQL<br/>postgres:5432)]
    Kafka[[Kafka<br/>kafka:19092]]
    Consumer[clearing-event-service<br/>8081]

    External -->|HTTP /api/v1/**| Gateway
    Gateway -->|Preserved path and Authorization header| Root
    Root -->|JPA / Flyway| Database
    Root -->|ClearingPostedEvent| Kafka
    Kafka -->|Consumer group clearing-event-service| Consumer
```

Container verification established:

- All five Compose services started successfully.
- CreditCardFlow, clearing-event-service, and api-gateway health endpoints returned `UP`.
- An intentional failed login traversed Gateway → CreditCardFlow → persisted-user lookup in PostgreSQL and returned the expected 401.
- Flyway migrations V1-V10 executed in PostgreSQL.
- The Kafka topic existed with one partition and replication factor one.
- A representative headerless JSON event sent through Kafka was consumed and logged by the real clearing-event-service container.

The intentional 401 verified connectivity and security behavior; no default application user was seeded.
