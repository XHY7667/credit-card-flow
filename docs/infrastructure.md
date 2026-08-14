# CreditCardFlow Infrastructure

For component responsibilities, see the [system architecture](architecture.md). For local usage and project scope, see the [project README](../README.md).

## Docker

Three independent application images are defined:

| Image | Dockerfile | Runtime | User | Port |
|---|---|---|---|---:|
| `creditcardflow` | `/Dockerfile` | Eclipse Temurin Java 21 JRE | `app` | 8080 |
| `clearing-event-service` | `/services/clearing-event-service/Dockerfile` | Eclipse Temurin Java 21 JRE | `app` | 8081 |
| `api-gateway` | `/services/api-gateway/Dockerfile` | Eclipse Temurin Java 21 JRE | `app` | 8082 |

Each Dockerfile uses a Maven/Java 21 builder stage and a separate Java 21 JRE runtime stage. Tests are skipped during image packaging because CI runs the application test suites first. Only the executable JAR is copied into `/app/app.jar`; source, Maven, and test reports are not copied into the runtime stage. Each container uses the exec-form entry point:

```text
java -jar /app/app.jar
```

## Docker Compose

The root `compose.yaml` uses the Compose Specification and default project network.

| Service | Image/build | Host mapping | Purpose |
|---|---|---|---|
| `postgres` | `postgres:18.4-bookworm` | `5432:5432` | Transactional database and Flyway schema |
| `kafka` | `apache/kafka:4.2.1` | `9092:9092` | Single-node KRaft broker/controller |
| `creditcardflow` | Root Dockerfile build | `8080:8080` | Security, REST, business logic, JPA, Kafka producer |
| `clearing-event-service` | Service Dockerfile build | `8081:8081` | Independent Kafka consumer |
| `api-gateway` | Gateway Dockerfile build | `8082:8082` | External REST routing |

### PostgreSQL

- Database and username are supplied as local runtime configuration.
- The password is interpolated from the deployment environment; this document does not provide it.
- Named volume `postgres-data` is mounted at `/var/lib/postgresql`.
- `pg_isready` supplies the health check.

### Kafka

- Runs one combined KRaft broker/controller with no ZooKeeper.
- Internal client listener: `INTERNAL://kafka:19092`.
- External host listener: `EXTERNAL://localhost:9092`.
- Controller listener: port 9093.
- Single-node offsets and transaction-state replication settings are one.
- Kafka CLI topic listing against the internal listener supplies the health check.

### Application Wiring and Startup

- CreditCardFlow reaches PostgreSQL at `postgres:5432` and Kafka at `kafka:19092`.
- Kafka publishing is enabled and the root consumer is disabled.
- clearing-event-service reaches Kafka at `kafka:19092`.
- api-gateway routes to `http://creditcardflow:8080`.
- CreditCardFlow waits for healthy PostgreSQL and Kafka.
- clearing-event-service waits for healthy Kafka.
- api-gateway waits for healthy CreditCardFlow.
- No sleep-based startup commands are used.

### Verified Compose Evidence

Container verification established:

- All five services started.
- CreditCardFlow, clearing-event-service, and api-gateway Actuator health endpoints returned `UP`.
- An intentional unknown-user login traversed Gateway → CreditCardFlow → PostgreSQL and returned the expected 401.
- Flyway V1-V10 completed and schema history was present.
- `creditcardflow.transaction-events` existed with one partition and replication factor one.
- A representative headerless JSON event sent to Kafka was consumed and logged by clearing-event-service.

No application user or smoke credential is committed by the infrastructure.

## Kubernetes

Plain YAML manifests are stored under `k8s/` in filename application order.

| File | Resources |
|---|---|
| `00-namespace.yaml` | Namespace `creditcardflow` |
| `01-configmap.yaml` | Non-sensitive runtime ConfigMap |
| `02-secret.example.yaml` | Example Opaque Secret boundary |
| `10-postgres.yaml` | PostgreSQL PVC, Deployment, ClusterIP Service |
| `20-kafka.yaml` | Single-node KRaft Deployment and ClusterIP Service |
| `30-creditcardflow.yaml` | CreditCardFlow Deployment and ClusterIP Service |
| `40-clearing-event-service.yaml` | Consumer Deployment and ClusterIP Service |
| `50-api-gateway.yaml` | Gateway Deployment and NodePort Service |

The manifests define five Deployments and five Services. PostgreSQL uses PVC `postgres-data`; no cloud-specific storage class is selected. Sensitive PostgreSQL configuration is referenced from `creditcardflow-secrets`, while non-sensitive endpoints and feature toggles come from `creditcardflow-config`.

PostgreSQL, Kafka, CreditCardFlow, and clearing-event-service remain internal ClusterIP services. The API Gateway is exposed through NodePort `30082`, targeting container port 8082. The three Java workloads have startup, readiness, and liveness probes against `/actuator/health`; PostgreSQL and Kafka use infrastructure-appropriate probes.

The Kubernetes manifests were statically/offline validated. They were not applied to a live Kubernetes cluster, and no Ingress, LoadBalancer, Helm chart, operator, or cloud deployment is included.

## Continuous Integration

Workflow: `.github/workflows/ci.yml`

Triggers:

- Pull request targeting `main`
- Push to `main`
- Manual `workflow_dispatch`

Permissions are read-only for repository contents.

| Job | Responsibility |
|---|---|
| `root-tests` | Run the complete root Maven test suite with Java 21 |
| `clearing-event-service-tests` | Run the independent consumer-service Maven suite |
| `api-gateway-tests` | Run the independent Gateway Maven suite |
| `docker-builds` | Verify Docker, validate Compose, and build/inspect all three images |

The three Maven jobs are independent and can run in parallel. `docker-builds` declares all three as dependencies and runs only after they pass. The workflow has executed successfully on `main`.

The workflow does not log in to a registry, publish images, use deployment credentials, start the Compose application stack, or deploy Kubernetes resources. It implements continuous integration and build verification, not a production continuous-delivery pipeline.
