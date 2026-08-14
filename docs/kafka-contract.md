# ClearingPostedEvent Kafka Contract

For the surrounding flow, see the [system architecture](architecture.md) and [clearing sequence](data-flow.md#clearing-flow).

## Channel

| Property | Value |
|---|---|
| Topic | `creditcardflow.transaction-events` |
| Producer | CreditCardFlow |
| Production consumer | clearing-event-service |
| Consumer group | `clearing-event-service` |
| Kafka record key | `clearingReference` |
| Value encoding | JSON |
| Topic partitions | 1 |
| Topic replicas | 1 |

The root application also retains a disabled-by-default consumer for its embedded round-trip integration test. Normal runtime consumption is owned by clearing-event-service.

## Event Schema

Current event name: `ClearingPostedEvent`

| Field | JSON representation | Meaning |
|---|---|---|
| `eventId` | UUID string | Unique event instance identifier |
| `clearingReference` | String | Posted clearing business reference and Kafka key |
| `authorizationReference` | String | Authorization converted into clearing |
| `amount` | JSON number | Fixed-precision clearing amount |
| `currency` | String | Three-letter currency code carried by the clearing |
| `status` | String | Current value is `POSTED` |
| `occurredAt` | ISO-8601 instant string | Event creation time |

Synthetic example:

```json
{
  "eventId": "73df179a-4aa4-4e2b-bec2-c2083ba479f0",
  "clearingReference": "CLR-1001",
  "authorizationReference": "AUTH-1001",
  "amount": 125.75,
  "currency": "USD",
  "status": "POSTED",
  "occurredAt": "2026-08-13T12:15:00Z"
}
```

## Producer Behavior

CreditCardFlow constructs the event after it has changed the Authorization to `CLEARED`, created a `POSTED` Clearing, and applied the CardAccount balance mutation within the database transaction.

The publisher is a `@TransactionalEventListener` with phase `AFTER_COMMIT`. The database commit therefore completes before `KafkaTemplate.send(...)` is invoked. The Kafka send uses `clearingReference` as the record key and returns a future rather than blocking for broker completion.

Both immediate send exceptions and asynchronous future failures are logged and contained. Since publication occurs after commit, a Kafka failure does not roll back the committed Clearing.

## Serialization and Consumer Compatibility

- CreditCardFlow serializes the value as JSON.
- Kafka Java type headers are disabled.
- Consumers ignore type headers and use an explicitly configured target type.
- The producer event uses the root application's `ClearingStatus` enum.
- clearing-event-service defines a separate local `ClearingPostedEvent`; its compatible `status` field is a String.
- The secondary service does not import or depend on the root event class.

This establishes a JSON wire contract across two independently packaged Java applications. Embedded Kafka verifies that representative headerless JSON is deserialized into the secondary service's local type.

## Delivery Boundaries

The current implementation intentionally does not provide:

- A transactional outbox
- An exactly-once delivery guarantee
- Application retry or dead-letter-topic handling
- A schema registry
- Avro or Protobuf encoding
- Kafka transactions

If the broker is unavailable after the database commit, the clearing remains committed and the failed send is logged. The repository does not claim automatic recovery of that event.
