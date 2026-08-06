# CreditCardFlow Project Scope

## Project overview

CreditCardFlow is a simplified issuer-side credit card transaction processing platform. It models the lifecycle of merchant transactions from authorization through reconciliation while providing a capstone-scale foundation for learning and demonstrating reliable financial-system design.

The MVP will be delivered as a Spring Boot modular monolith. It uses the base package `com.hx.creditcardflow`, Java 21, Spring Boot 4.1.0, and Maven.

## Business problem

An issuer must consistently decide whether to authorize card transactions, handle duplicate requests and reversals safely, account for cleared transactions, settle merchant obligations, and identify discrepancies. These steps cross multiple operational stages, and errors can create incorrect balances, duplicate financial effects, or unresolved differences.

CreditCardFlow provides a controlled, auditable simulation of that lifecycle without attempting to implement a production card-network or core-banking platform.

## Project goals

- Model the issuer-side lifecycle of a credit card transaction.
- Provide reliable authorization, reversal, clearing, settlement, and reconciliation workflows.
- Prevent duplicate financial effects through idempotent request handling.
- Preserve an auditable history of all financial activity.
- Enforce role-based access for administrative, operational, and merchant use cases.
- Establish a maintainable modular architecture that can evolve toward asynchronous processing and independently deployable services.
- Provide observable, testable behavior suitable for a capstone demonstration.

## User roles

- **ADMIN**: Manages platform-level access, configuration, and administrative oversight.
- **OPERATIONS**: Monitors transaction processing, manages settlement and reconciliation workflows, and investigates exceptions.
- **MERCHANT**: Manages permitted merchant information and submits or reviews transactions belonging to that merchant.

## Core business flow

`Merchant -> Authorization -> Reversal -> Clearing -> Settlement -> Reconciliation`

Reversal is conditional: it is used when a prior authorization must be cancelled or corrected. Transactions that are not reversed proceed into clearing, settlement, and reconciliation.

## MVP phases

1. **Merchant CRUD**: Create, read, update, and deactivate merchant records with validation and role-based access.
2. **Card account and authorization**: Model card accounts, evaluate authorization requests, reserve available credit, and record approval or decline outcomes.
3. **Reversal and idempotency**: Reverse eligible authorizations safely and prevent duplicate requests from producing repeated financial effects.
4. **Clearing and settlement**: Accept clearing records, match them to prior activity, calculate settlement obligations, and group them into batches.
5. **Reconciliation**: Compare authorization, clearing, and settlement records and report matched items and exceptions.
6. **Security, messaging, deployment, and monitoring**: Harden role-based security, introduce asynchronous integration where appropriate, package the application for deployment, and add health, metrics, logging, and operational monitoring.

## Functional requirements

- Manage merchant records, including activation status and merchant identifiers.
- Manage simplified card accounts and their available-credit state using non-sensitive test identifiers.
- Accept authorization requests and return deterministic approval or decline results.
- Record authorization amounts, statuses, timestamps, merchant references, and idempotency keys.
- Reject or safely replay duplicate requests without applying a second financial effect.
- Create reversals that reference eligible authorization transactions.
- Prevent a reversal amount from exceeding the eligible authorized amount.
- Ingest clearing records and associate them with merchants and relevant authorization activity when available.
- Build settlement batches from eligible clearing records and track batch status and totals.
- Reconcile authorization, clearing, and settlement data.
- Produce reconciliation results that identify matches, mismatches, duplicates, and missing records.
- Provide role-based access appropriate to ADMIN, OPERATIONS, and MERCHANT users.
- Maintain an auditable history for security-sensitive actions and financial workflow changes.
- Expose clear validation and error responses for invalid, conflicting, or unauthorized requests.

## Non-functional requirements

- **Integrity**: Financial state changes must be transactional, consistent, and traceable.
- **Security**: Apply least-privilege authorization, protect credentials and secrets, validate input, and avoid sensitive data exposure in logs or responses.
- **Idempotency**: Retried commands must not create duplicate financial effects.
- **Auditability**: Important business decisions and transaction links must be reconstructable from retained records.
- **Reliability**: Failures must leave data in a consistent state and support safe retry where applicable.
- **Performance**: Typical synchronous API operations should have predictable response times under the intended capstone workload.
- **Scalability**: Module boundaries and event-ready interfaces should allow later horizontal and service-level scaling.
- **Maintainability**: Code should follow explicit domain boundaries, DTO/entity separation, automated tests, and documented interfaces.
- **Observability**: Provide structured logging, health information, metrics, correlation identifiers, and actionable error reporting.
- **Portability**: The application should build reproducibly with Maven and run in standard Java 21 deployment environments.

## Security rule

**Real PAN, CVV, PIN, and magnetic-stripe data must never be stored.** Development and demonstrations must use synthetic, tokenized, or otherwise non-sensitive test identifiers. Sensitive authentication data must not appear in application logs, error messages, test fixtures, or monitoring output.

## Out of scope

- Direct integration with Visa, Mastercard, American Express, or other payment networks.
- Production card issuance, personalization, activation, or PIN management.
- Storage or processing of real cardholder data or sensitive authentication data.
- PCI DSS certification or a claim of production PCI DSS compliance.
- Fraud scoring, machine-learning risk engines, and chargeback case management.
- Interest, fees, statements, minimum payments, collections, and full general-ledger accounting.
- Foreign-exchange conversion and multi-currency settlement.
- Token vaults, hardware security modules, and cryptographic key ceremonies.
- High-availability, multi-region, or card-network-scale throughput guarantees.
- A merchant point-of-sale application or cardholder-facing user interface.

