# CreditCardFlow

CreditCardFlow is a Java 21 and Spring Boot application for modeling an issuer-side credit card transaction lifecycle. The current implementation includes the Merchant API and a Flyway-managed PostgreSQL development database.

## Prerequisites

- Java 21
- Docker with Docker Compose support
- The included Maven wrapper (`mvnw.cmd`); a separate Maven installation is not required

## Local PostgreSQL setup

Create a local environment file from the committed example:

```powershell
Copy-Item .env.example .env
```

Set an appropriate local password in `.env`. The real `.env` file is ignored by Git and must never be committed.

Start PostgreSQL and check its status:

```powershell
docker compose up -d
docker compose ps
```

The Compose environment runs PostgreSQL 18.4 on local port `5432` and stores its data in the named `postgres-data` volume.

## Run the application locally

Start Spring Boot with the `local` profile:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

The local profile imports `.env`, connects to PostgreSQL through the PostgreSQL JDBC driver and HikariCP, enables Flyway, and configures Hibernate with `ddl-auto=validate`.

## Database architecture and schema ownership

The local database stack consists of:

- PostgreSQL 18.4
- PostgreSQL JDBC driver
- HikariCP connection pooling
- Flyway schema migrations
- Hibernate schema validation

Flyway owns database schema creation and evolution. Hibernate validates that the Flyway-managed schema matches the JPA mappings at application startup; it does not create or update the local PostgreSQL schema. Do not configure Hibernate `create`, `create-drop`, or `update` for local PostgreSQL.

The current versioned migrations are:

- `V1__create_merchants_table.sql`: creates the `merchants` table, its identity primary key, required columns, and unique merchant-code constraint.
- `V2__add_merchant_status_check_constraint.sql`: adds the `ck_merchants_status` CHECK constraint for `ACTIVE`, `SUSPENDED`, and `CLOSED`.

Previously applied Flyway versioned migrations must not be edited. Make future schema changes with new forward-only migrations such as V3, V4, and later versions.

## Testing

Run the complete test suite with:

```powershell
.\mvnw.cmd clean test
```

The fast application, API, and repository tests use H2. Their test-specific configuration disables Flyway and lets Hibernate create and drop the temporary H2 schema.

PostgreSQL integration coverage uses Testcontainers with PostgreSQL 18.4. It starts a fresh disposable database, applies Flyway migrations, validates the schema through Hibernate, persists and reads a Merchant through `MerchantRepository`, verifies Flyway history, and confirms the Merchant status CHECK constraint rejects an invalid value. Docker must be available when running the full test suite.

## Useful local database commands

Open a PostgreSQL shell in the running Compose service:

```powershell
docker compose exec postgres psql -U creditcardflow -d creditcardflow
```

Inside `psql`, list tables and inspect the Merchant table:

```text
\dt
\d merchants
```

