# EcommerceAPI

Banking-grade e-commerce API portfolio project built with Spring Boot, PostgreSQL, Flyway, OAuth2 resource-server security, immutable ledger records, tamper-evident audit events, and reliable asynchronous stock updates.

## Stock Update Stream

Product viewers can subscribe to advisory stock updates with Server-Sent Events:

```http
GET /products/{productId}/stock/stream
Accept: text/event-stream
Authorization: Bearer <access-token>
```

Events use the `stock-changed` event name and include:

- `productId`
- `availableQuantity`
- `reservedQuantity`
- `reason`
- `occurredAt`
- `advisory`

The stream is intentionally advisory. Checkout still revalidates and reserves stock atomically in PostgreSQL, so UI stock numbers should never be treated as an authorization to buy.

## Outbox Delivery

Inventory changes write `StockChanged` records to the transactional outbox in the same database transaction as the stock mutation. A scheduled outbox processor publishes those records to the in-memory SSE broadcaster and marks each event processed or failed with retry metadata.

This first version is designed for a single API instance. For multiple instances, keep the transactional outbox table and replace the in-memory broadcaster fan-out with Redis Pub/Sub, Kafka, or another shared event backbone so clients connected to any node receive the same stock updates.
