# CreditCardFlow

CreditCardFlow is a synthetic educational backend that models a subset of issuer-side credit-card transaction processing. It implements the transaction lifecycle:

```text
Authorization -> Reversal
              -> Clearing
```

The system is non-production, uses synthetic data, and is not intended to store or process real cardholder information. Settlement and reconciliation are not implemented.

## Key features

### Core domain

- Merchant creation and lifecycle management
- Card account limits, balances, available credit, and status
- Card issuance, expiration, and status controls
- Authorization approval or decline
- Authorization reversal and reserved-credit release
- Clearing and balance posting

### Financial integrity

- Transactional credit reservation, release, and posting
- `BigDecimal` monetary calculations backed by PostgreSQL `NUMERIC`
- Optimistic locking on card accounts
- Idempotent reversal replay and duplicate-reference protection
- Database uniqueness and foreign-key constraints
- Kafka publication after successful clearing transaction commit

### Platform

- Versioned REST API with validation and centralized error handling
- PostgreSQL persistence through Spring Data JPA and Flyway
- Service-layer AOP logging and Micrometer business metrics
- Actuator health, information, and metrics endpoints
- Persistent application users, encoded passwords, JWT authentication, and role-based authorization
- Kafka producer and consumers using headerless JSON events
- Independent clearing event consumer service
- Spring Cloud API Gateway
- Multi-stage, non-root Docker images and a five-service Docker Compose environment
- Plain Kubernetes manifests
- GitHub Actions test and image-build verification

## Technology stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Application framework | Spring Boot 4.1.0 |
| Build | Maven and Maven Wrapper |
| Persistence | Spring Data JPA, Hibernate schema validation |
| Database | PostgreSQL 18.4 |
| Schema migration | Flyway, migrations V1-V10 |
| Security | Spring Security, OAuth2 Resource Server, RSA-signed JWT |
| Messaging | Spring Kafka 4.1.0; Apache Kafka 4.2.1 in Compose |
| Gateway | Spring Cloud Gateway 5.0.2, Spring Cloud 2025.1.2 |
| Observability | Spring Boot Actuator and Micrometer |
| Testing | JUnit, Mockito, MockMvc, Testcontainers, Embedded Kafka |
| Infrastructure | Docker, Docker Compose, plain Kubernetes YAML |
| Automation | GitHub Actions |

## Architecture summary

```text
Client
  |
  v
API Gateway :8082
  |
  v
CreditCardFlow :8080 ------> PostgreSQL :5432
  |
  v
Kafka :19092 (container network)
  |
  v
clearing-event-service :8081
```

The API Gateway is the external REST entry point in the containerized architecture and preserves `/api/v1/**` paths and bearer authorization headers. CreditCardFlow owns authentication, authorization, business rules, transactions, and database state. The clearing event service is an independent application that consumes clearing events asynchronously; it is not called over REST and is not routed through the Gateway.

The root service's Kafka consumer is disabled during normal runtime. It remains available for the focused embedded producer-to-consumer integration test.

## Core business flow

### Authorization

An authorization validates the referenced card, card account, and merchant. An eligible request with sufficient credit reserves the exact amount from `availableCredit` and becomes `APPROVED`. Ineligible requests or insufficient credit produce a persisted `DECLINED` authorization without reserving credit.

### Reversal

A reversal applies to an `APPROVED` authorization for the matching amount. It releases the reserved credit, changes the authorization to `REVERSED`, and persists a `COMPLETED` reversal. An idempotency key supports safe replay without releasing credit twice; conflicting replays and duplicate references are rejected.

### Clearing

Clearing requires an `APPROVED` authorization with matching amount and currency. It converts pending exposure into `currentBalance` and does not reduce `availableCredit` again. The authorization becomes `CLEARED`, and the clearing becomes `POSTED`.

CreditCardFlow publishes a `ClearingPostedEvent` through a transactional `AFTER_COMMIT` listener, so Kafka publication is attempted only after the database transaction commits.

## Security

- Application users are persisted as `AppUser` records in PostgreSQL.
- Passwords are stored using Spring Security's encoded password format.
- Supported roles are `USER` and `ADMIN`.
- `POST /api/v1/auth/login` is public and issues a stateless bearer token for valid credentials.
- JWTs are RSA-signed and include issuer validation by the OAuth2 Resource Server.
- Business endpoints require authentication; master-data writes require `ADMIN`.
- `GET /actuator/health` is public.
- `/actuator/info`, `/actuator/metrics`, and individual metric endpoints require `ADMIN`.

The RSA key pair is generated when CreditCardFlow starts. Tokens therefore become invalid after an application restart. External key storage, rotation, and production key management are intentionally not implemented.

No default application user or application-user password is committed, and there is no public registration API. Authentication requires an `AppUser` already persisted in PostgreSQL.

## Kafka and the clearing event service

Kafka topic:

```text
creditcardflow.transaction-events
```

After a clearing transaction commits, CreditCardFlow sends a `ClearingPostedEvent` keyed by clearing reference. A Kafka send failure is contained and does not reverse the already committed clearing transaction.

Java type headers are disabled. Consumers deserialize JSON into an explicitly configured target, and `clearing-event-service` owns a separate local event type with the same wire contract. This demonstrates asynchronous communication between independently packaged Java applications without sharing the producer's Java class.

The design does not claim transactional-outbox delivery, distributed exactly-once processing, or guaranteed delivery after every post-commit broker failure.

## Observability

CreditCardFlow exposes Actuator health, information, and metrics endpoints. Micrometer records these business counters:

- `creditcardflow.authorization.approved`
- `creditcardflow.authorization.declined`
- `creditcardflow.reversal.completed`
- `creditcardflow.clearing.posted`

A service-layer logging aspect records invocation outcome and duration while preserving return values and exceptions. It does not intentionally log DTO or method-argument values.

The secondary service and Gateway expose their own health and information endpoints.

## Database and Flyway

PostgreSQL stores merchants, card accounts, cards, authorizations, authorization reversals, clearings, and application users. Flyway owns schema creation and forward evolution through V1-V10. Hibernate uses `ddl-auto=validate` with the local PostgreSQL profile rather than creating or updating the schema.

The schema includes business-reference uniqueness, non-null constraints, status checks, and foreign keys between the transaction entities. Monetary values use Java `BigDecimal` and PostgreSQL `NUMERIC(19,2)`. Card accounts use a version column for optimistic locking.

Detailed schema documentation is intentionally outside this overview.

## Testing

Verified test baselines:

| Application | Tests | Result |
|---|---:|---|
| CreditCardFlow | 341 | PASS |
| clearing-event-service | 2 | PASS |
| api-gateway | 2 | PASS |

The suites include:

- Unit and entity-invariant tests
- Controller/WebMvc and validation/error tests
- JPA and repository tests
- PostgreSQL 18.4 Testcontainers integration tests with Flyway validation
- Transaction, idempotency, and optimistic-locking tests
- Real Spring Security filter-chain, JWT, and RBAC tests
- Kafka configuration, publisher, consumer, and failure-containment tests
- Embedded Kafka producer-to-consumer round trips
- Independent headerless-JSON consumer integration
- Gateway routing, path preservation, and Authorization-header forwarding

Run the suites independently:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd -f services/clearing-event-service/pom.xml clean test
.\mvnw.cmd -f services/api-gateway/pom.xml clean test
```

Java 21 and Docker are required for the complete root suite because its PostgreSQL integration tests use Testcontainers.

## Run the complete system locally

### Prerequisites

- Git
- Docker Desktop or Docker Engine
- Docker Compose

Docker Compose is the simplest way to run the complete architecture:

```powershell
docker compose up -d --build
docker compose ps
```

Compose starts:

- `postgres`
- `kafka`
- `creditcardflow`
- `clearing-event-service`
- `api-gateway`

Health endpoints:

- CreditCardFlow: <http://localhost:8080/actuator/health>
- Clearing event service: <http://localhost:8081/actuator/health>
- API Gateway: <http://localhost:8082/actuator/health>

Stop the environment without deleting the PostgreSQL volume:

```powershell
docker compose down
```

The three application images are `creditcardflow`, `clearing-event-service`, and `api-gateway`. Runtime database passwords are supplied through environment interpolation; no application-user credentials are seeded. A full secured business demonstration can create an application user through controlled database setup and then use the login endpoint, but those demonstration steps are documented separately.

The Compose environment has been verified for five-container startup, all three health endpoints reporting `UP`, Gateway-to-CreditCardFlow-to-PostgreSQL request flow, Flyway V1-V10 execution, Kafka topic creation, and Kafka-to-clearing-event-service event consumption.

## Useful endpoint summary

| Area | Endpoint |
|---|---|
| Authentication | `POST /api/v1/auth/login` |
| Merchants | `/api/v1/merchants` and `/api/v1/merchants/{id}` |
| Card accounts | `/api/v1/card-accounts` and `/api/v1/card-accounts/{accountNumber}` |
| Cards | `/api/v1/cards` and `/api/v1/cards/{cardReference}` |
| Authorizations | `/api/v1/authorizations` and `/api/v1/authorizations/{authorizationReference}` |
| Reversals | `/api/v1/reversals` and `/api/v1/reversals/{reversalReference}` |
| Clearings | `/api/v1/clearings` and `/api/v1/clearings/{clearingReference}` |
| Health | `GET /actuator/health` |

This is an overview rather than complete request/response API documentation.

## Kubernetes manifests

Plain Kubernetes YAML is stored under `k8s/` and defines:

- A `creditcardflow` namespace
- Shared non-sensitive configuration in a ConfigMap
- An example Secret boundary for the PostgreSQL password
- A PostgreSQL PersistentVolumeClaim
- Deployments and Services for PostgreSQL, Kafka, CreditCardFlow, the clearing event service, and the API Gateway
- Startup, readiness, and liveness probes
- API Gateway external access through NodePort `30082`

The manifests were statically/offline validated. They were not applied to a live Kubernetes cluster as part of this implementation.

## Continuous integration

`.github/workflows/ci.yml` runs on pull requests to `main`, pushes to `main`, and manual `workflow_dispatch` requests. Its jobs are:

- `root-tests`
- `clearing-event-service-tests`
- `api-gateway-tests`
- `docker-builds`

The three Maven suites run independently. The Docker job validates Compose syntax and builds all three application images only after all test jobs pass. The workflow has executed successfully on `main`.

This is CI and build verification. It does not log in to a registry, publish images, or deploy Kubernetes resources; automated production CD is not implemented.

## Project structure

```text
CreditCardFlow/
|-- src/                              # Root application and tests
|-- services/
|   |-- clearing-event-service/       # Independent Kafka consumer service
|   `-- api-gateway/                  # Independent Spring Cloud Gateway
|-- k8s/                              # Plain Kubernetes manifests
|-- .github/workflows/                # GitHub Actions CI
|-- compose.yaml                      # Five-service local environment
|-- Dockerfile                        # Root application image
`-- README.md
```

The repository contains three independent Maven applications and is not a Maven multi-module build.

## Known limitations

- This is a synthetic, non-production system and must not handle real cardholder data.
- Settlement and reconciliation are not implemented.
- JWT signing keys are generated in memory at startup, invalidating tokens after restart.
- No external secret-management or key-rotation platform is integrated.
- Compose uses single-node Kafka and a local PostgreSQL topology.
- There is no transactional outbox or exactly-once delivery guarantee.
- Kubernetes manifests have not been deployed to a live cluster for this submission.
- Images are not published to a registry, and automatic production CD is not implemented.
- No default application-user credentials are committed.

## Future improvements

- Settlement and reconciliation workflows
- Transactional outbox and Kafka retry/dead-letter handling
- Externalized signing-key and secret management with rotation
- Production registry publishing and controlled deployment automation
- Managed/cloud Kubernetes deployment and operational hardening
- Redis or other caching only where measured workloads justify it
- Production-scale monitoring, alerting, and delivery diagnostics
