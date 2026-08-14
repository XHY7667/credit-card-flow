# CreditCardFlow Manual Demo

## Prerequisites

- Docker Desktop or Docker Engine
- Docker Compose
- Postman
- Persisted, enabled demo `AppUser` records for one `ADMIN` and one `USER`

No registration or user-administration API exists. No default application user or seed credentials are committed. The two `AppUser` records must be prepared separately in PostgreSQL with passwords encoded in the format accepted by the configured delegating password encoder. This repository does not provide a documented safe user-creation command, so user preparation and credential values are intentionally left to the evaluator. Do not commit plaintext passwords or encoded password hashes for demo convenience.

Use synthetic data only. Never enter a PAN, CVV, real credential, real JWT, or other real secret.

## Start Environment

From the repository root:

```console
docker compose up -d --build
docker compose ps
```

Wait until the services are ready. Useful public health checks are:

```console
curl http://localhost:8082/actuator/health
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
```

Each should return HTTP 200 with `"status":"UP"`. Port 8082 is the API Gateway, port 8080 is the root CreditCardFlow application, and port 8081 is clearing-event-service.

## Prepare Postman

1. Import `postman/CreditCardFlow.postman_collection.json`.
2. Import `postman/CreditCardFlow.local.postman_environment.json`.
3. Select the **CreditCardFlow Local** environment.
4. Fill `adminUsername`, `adminPassword`, `userUsername`, and `userPassword` locally.
5. Leave token variables empty; the login tests populate `adminToken` and `userToken`.

Never commit an exported environment containing passwords or JWTs. Business API requests use `baseUrl=http://localhost:8082`. Secured application actuator requests use `serviceBaseUrl=http://localhost:8080`, because the gateway forwards only `/api/v1/**` and does not proxy the root application's metrics.

The synthetic references supplied in the environment are unique database keys. Before repeating the demo, change all business-reference and idempotency-key values, or intentionally reset the local database. Normal shutdown is:

```console
docker compose down
```

An optional full local database reset is destructive and removes persisted demo users and business data:

```console
docker compose down -v
```

Use that reset only deliberately; it is not the normal shutdown path.

## Recommended Demo Sequence

Run requests in the order below. The controlled balances assume a newly created account with a USD 1000.00 credit limit and no prior activity.

1. **Health** — Run **00 Health / Gateway Health**. Expect HTTP 200 and `UP`.
2. **Admin login** — Run **01 Authentication / Admin Login**. Expect HTTP 200 and a nonempty `accessToken`, saved as `adminToken`.
3. **User login** — Run **01 Authentication / User Login**. Expect HTTP 200 and a nonempty `accessToken`, saved as `userToken`.
4. **Create Merchant** — Run **02 Merchant / Create Merchant**. Expect HTTP 201 and `ACTIVE`; the test saves `merchantId`. Run **Get Merchant** and expect HTTP 200.
5. **Create CardAccount** — Run **03 Card Account / Create CardAccount**. Expect HTTP 201 with `creditLimit=1000.00`, `currentBalance=0.00`, and `availableCredit=1000.00`. Run the initial GET and expect HTTP 200.
6. **Create Card** — Run **04 Card / Create Card**. Expect HTTP 201 and `ACTIVE`. The request contains only synthetic `lastFour`, never PAN or CVV. Run **Get Card** and expect HTTP 200.
7. **Authorization #1** — Run **05 Authorization / Create Authorization #1**. Expect HTTP 201 and `APPROVED` for the USD 200.00 PURCHASE.
8. **Verify account state** — Run **Get Authorization #1** and **Get CardAccount - After Authorization #1**. Expect authorization `APPROVED`, limit 1000.00, balance 0.00, and available credit 800.00.
9. **Reversal** — Run **06 Reversal / Create Full Reversal**. Expect HTTP 201 and `COMPLETED`. Run **Replay Full Reversal** with the identical body and `Idempotency-Key`; expect the existing `COMPLETED` result with HTTP 201 and no second credit release.
10. **Verify released credit** — Run the reversal and authorization GETs, then **Get CardAccount - After Reversal**. Expect the authorization `REVERSED`, limit 1000.00, balance 0.00, and available credit restored to 1000.00.
11. **Authorization #2** — Run **05 Authorization / Create Authorization #2** only after step 10. Expect HTTP 201 and `APPROVED`.
12. **Verify second reservation** — Run its GET and **Get CardAccount - After Authorization #2**. Expect limit 1000.00, balance 0.00, and available credit 800.00.
13. **Clearing** — Run **07 Clearing / Post Clearing** for Authorization #2. Expect HTTP 201 and clearing `POSTED`. Run the clearing, authorization, and account GETs. Expect authorization `CLEARED`, limit 1000.00, balance 200.00, and available credit 800.00. Clearing converts reserved exposure into posted balance without reducing available credit a second time.
14. **Verify Kafka consumer log** — After the successful clearing, run:

    ```console
    docker compose logs clearing-event-service
    ```

    Look for `Consumed clearing event` containing the environment's `clearingReference` and `status=POSTED`. This is asynchronous consumer evidence; Postman does not deliver or inspect Kafka events. The demo makes no exactly-once delivery claim.
15. **Security 401/403** — Run **08 Security Checks** in order. The anonymous protected account GET must return 401. Direct root metrics with a USER token must return 403. The identical safe metrics GET with an ADMIN token must return 200.
16. **Actuator/Metrics** — Run **09 Observability**. Health should be `UP`; info, metrics index, and metric detail requests require ADMIN. The approved authorization, completed reversal, and posted clearing counters should reflect demo activity, but their exact values may include previous requests and are intentionally not hard-coded.

No Settlement or Reconciliation flow is part of this demo.
