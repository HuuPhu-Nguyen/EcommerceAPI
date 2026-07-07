package com.phu.ecommerceapi.shared.api;

public final class OpenApiExamples {

    public static final String CHECKOUT_REQUEST = """
            {
              "cartId": 42
            }
            """;

    public static final String ORDER_RESPONSE = """
            {
              "orderId": "7b3c4aa9-5658-48df-a0a9-fc6f8e9e6f8b",
              "cartId": 42,
              "customerId": 1001,
              "status": "PENDING_PAYMENT",
              "total": 149.98,
              "currency": "USD",
              "createdAt": "2026-07-07T15:30:00Z",
              "items": [
                {
                  "productId": 501,
                  "productName": "Hardware Security Key",
                  "quantity": 2,
                  "unitPrice": 74.99,
                  "currency": "USD",
                  "lineTotal": 149.98
                }
              ]
            }
            """;

    public static final String CREATE_PAYMENT_REQUEST = """
            {
              "orderId": "7b3c4aa9-5658-48df-a0a9-fc6f8e9e6f8b",
              "paymentMethodToken": "pm_approved"
            }
            """;

    public static final String PAYMENT_RESPONSE = """
            {
              "paymentId": "638dc8e7-e7c5-47bf-a62f-a5728f9c19be",
              "orderId": "7b3c4aa9-5658-48df-a0a9-fc6f8e9e6f8b",
              "status": "SUCCEEDED",
              "providerStatus": "SUCCEEDED",
              "providerPaymentId": "fake-pay-638dc8e7",
              "failureCode": null,
              "message": "Payment approved by fake provider",
              "amount": 149.98,
              "currency": "USD"
            }
            """;

    public static final String CREATE_REFUND_REQUEST = """
            {
              "reason": "customer_request"
            }
            """;

    public static final String REFUND_RESPONSE = """
            {
              "refundId": "d1dfd665-f26c-4e87-8808-e2367f65076a",
              "paymentId": "638dc8e7-e7c5-47bf-a62f-a5728f9c19be",
              "orderId": "7b3c4aa9-5658-48df-a0a9-fc6f8e9e6f8b",
              "status": "SUCCEEDED",
              "providerStatus": "SUCCEEDED",
              "providerRefundId": "fake-ref-d1dfd665",
              "failureCode": null,
              "message": "Refund approved by fake provider",
              "amount": 149.98,
              "currency": "USD"
            }
            """;

    public static final String LEDGER_TRANSACTIONS_RESPONSE = """
            [
              {
                "transactionId": "2d2ce6ea-71ad-4525-92be-654952e9b62c",
                "transactionType": "PAYMENT_CAPTURE",
                "referenceType": "PAYMENT",
                "referenceId": "638dc8e7-e7c5-47bf-a62f-a5728f9c19be",
                "description": "Payment captured for order 7b3c4aa9-5658-48df-a0a9-fc6f8e9e6f8b",
                "postedAt": "2026-07-07T15:30:02Z",
                "entries": [
                  {
                    "id": 9001,
                    "accountCode": "PROVIDER_CLEARING",
                    "accountName": "Provider clearing",
                    "accountType": "ASSET",
                    "direction": "DEBIT",
                    "amount": 149.98,
                    "currency": "USD"
                  },
                  {
                    "id": 9002,
                    "accountCode": "CUSTOMER_PAYMENTS",
                    "accountName": "Customer payments",
                    "accountType": "REVENUE",
                    "direction": "CREDIT",
                    "amount": 149.98,
                    "currency": "USD"
                  }
                ]
              }
            ]
            """;

    public static final String AUDIT_EVENTS_RESPONSE = """
            [
              {
                "id": 7001,
                "actorSubject": "customer-123",
                "action": "PAYMENT_SUCCEEDED",
                "resourceType": "PAYMENT",
                "resourceId": "638dc8e7-e7c5-47bf-a62f-a5728f9c19be",
                "details": "Payment approved by fake provider",
                "requestId": "req-7e2c1f",
                "ipAddress": "203.0.113.10",
                "userAgent": "curl/8.0",
                "createdAt": "2026-07-07T15:30:02Z",
                "previousHash": "5d41402abc4b2a76b9719d911017c592",
                "eventHash": "7d793037a0760186574b0282f2f435e7"
              }
            ]
            """;

    public static final String AUDIT_VERIFICATION_RESPONSE = """
            {
              "verified": true,
              "checkedEvents": 128,
              "brokenEventId": null,
              "latestHash": "7d793037a0760186574b0282f2f435e7",
              "message": "Audit hash chain verified"
            }
            """;

    public static final String RECONCILIATION_REPORT_RESPONSE = """
            {
              "healthy": true,
              "generatedAt": "2026-07-07T15:35:00Z",
              "checkedPayments": 12,
              "checkedRefunds": 2,
              "checkedLedgerTransactions": 14,
              "issues": []
            }
            """;

    public static final String STOCK_EVENT_STREAM = """
            event: stock-changed
            data: {"productId":501,"availableQuantity":7,"reservedQuantity":2,"reason":"CHECKOUT_RESERVED","occurredAt":"2026-07-07T15:30:00Z","advisory":true}

            """;

    public static final String VALIDATION_PROBLEM = """
            {
              "type": "urn:problem:validation-failed",
              "title": "Validation failed",
              "status": 400,
              "detail": "Request validation failed",
              "code": "VALIDATION_FAILED",
              "path": "/checkout",
              "requestId": "req-7e2c1f"
            }
            """;

    public static final String UNAUTHORIZED_PROBLEM = """
            {
              "type": "urn:problem:unauthorized",
              "title": "Unauthorized",
              "status": 401,
              "detail": "Authentication is required",
              "code": "UNAUTHORIZED",
              "path": "/payments",
              "requestId": "req-7e2c1f"
            }
            """;

    public static final String FORBIDDEN_PROBLEM = """
            {
              "type": "urn:problem:forbidden",
              "title": "Forbidden",
              "status": 403,
              "detail": "Access is denied",
              "code": "FORBIDDEN",
              "path": "/audit/events",
              "requestId": "req-7e2c1f"
            }
            """;

    public static final String CONFLICT_PROBLEM = """
            {
              "type": "urn:problem:conflict",
              "title": "Conflict",
              "status": 409,
              "detail": "Idempotency key was reused with a different request body",
              "code": "CONFLICT",
              "path": "/payments",
              "requestId": "req-7e2c1f"
            }
            """;

    private OpenApiExamples() {
    }
}
