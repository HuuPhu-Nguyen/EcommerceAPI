package com.phu.ecommerceapi.reconciliation.infrastructure;

import com.phu.ecommerceapi.ledger.domain.LedgerEntryDirection;
import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;
import com.phu.ecommerceapi.reconciliation.application.LedgerEntryReconciliationItem;
import com.phu.ecommerceapi.reconciliation.application.LedgerTransactionLookupKey;
import com.phu.ecommerceapi.reconciliation.application.ProviderReconciliationReference;
import com.phu.ecommerceapi.reconciliation.application.ReconciliationIssue;
import com.phu.ecommerceapi.reconciliation.application.ReconciliationIssueCode;
import com.phu.ecommerceapi.reconciliation.application.ReconciliationReport;
import com.phu.ecommerceapi.reconciliation.application.ReconciliationRunCompletion;
import com.phu.ecommerceapi.reconciliation.application.ReconciliationRunStorePort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ReconciliationPersistenceIntegrationTest {

    private static final UUID PROVIDER_CLEARING_ACCOUNT_ID = uuid(101);
    private static final UUID ORDER_REVENUE_ACCOUNT_ID = uuid(102);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JpaReconciliationReadAdapter readAdapter;

    @Autowired
    private ReconciliationRunStorePort runStorePort;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE reconciliation_issue_record, reconciliation_run, provider_webhook_event, "
                        + "ledger_entry, ledger_transaction, refund_record, payment_record, "
                        + "customer_order_item, customer_order, cart_item_model, cart_model, user_model "
                        + "RESTART IDENTITY CASCADE"
        );
    }

    @AfterEach
    void cleanUpAfter() {
        cleanUp();
    }

    @Test
    void readAdapterPagesReconciliationRowsByIdAndUsesBoundedLookups() {
        UUID firstPaymentId = uuid(1);
        UUID secondPaymentId = uuid(2);
        UUID firstRefundId = uuid(3);
        UUID secondRefundId = uuid(4);
        UUID firstTransactionId = uuid(11);
        UUID secondTransactionId = uuid(12);
        UUID firstWebhookId = uuid(21);
        UUID secondWebhookId = uuid(22);

        TestOrder firstOrder = insertOrder();
        TestOrder secondOrder = insertOrder();
        insertPayment(firstPaymentId, firstOrder, "REFUNDED", "fake_payment_1");
        insertPayment(secondPaymentId, secondOrder, "REFUNDED", "fake_payment_2");
        insertRefund(firstRefundId, firstPaymentId, firstOrder, "fake_refund_1");
        insertRefund(secondRefundId, secondPaymentId, secondOrder, "fake_refund_2");
        insertLedgerTransaction(firstTransactionId, "PAYMENT_CAPTURE", "PAYMENT", firstPaymentId.toString());
        insertLedgerTransaction(secondTransactionId, "REFUND", "REFUND", firstRefundId.toString());
        insertLedgerEntry(firstTransactionId, PROVIDER_CLEARING_ACCOUNT_ID, "DEBIT");
        insertLedgerEntry(firstTransactionId, ORDER_REVENUE_ACCOUNT_ID, "CREDIT");
        insertLedgerEntry(secondTransactionId, ORDER_REVENUE_ACCOUNT_ID, "DEBIT");
        insertLedgerEntry(secondTransactionId, PROVIDER_CLEARING_ACCOUNT_ID, "CREDIT");
        insertWebhook(firstWebhookId, "PAYMENT_SUCCEEDED", "fake_payment_1");
        insertWebhook(secondWebhookId, "REFUND_SUCCEEDED", "fake_refund_2");

        assertThat(readAdapter.findPaymentsAfterId(null, 1))
                .extracting("id")
                .containsExactly(firstPaymentId);
        assertThat(readAdapter.findPaymentsAfterId(firstPaymentId, 1))
                .extracting("id")
                .containsExactly(secondPaymentId);
        assertThat(readAdapter.findRefundsAfterId(null, 1))
                .extracting("id")
                .containsExactly(firstRefundId);
        assertThat(readAdapter.findProviderWebhookEventsAfterId(firstWebhookId, 1))
                .extracting("id")
                .containsExactly(secondWebhookId);
        assertThat(readAdapter.findLedgerTransactionsAfterId(firstTransactionId, 1))
                .extracting("id")
                .containsExactly(secondTransactionId);

        assertThat(readAdapter.findPaymentIdsByIds(Set.of(firstPaymentId, uuid(99))))
                .containsExactly(firstPaymentId);
        assertThat(readAdapter.findPaymentProviderReferences(Set.of(
                new ProviderReconciliationReference("fake", "fake_payment_1"),
                new ProviderReconciliationReference("fake", "missing_payment")
        )))
                .containsExactly(new ProviderReconciliationReference("fake", "fake_payment_1"));
        assertThat(readAdapter.findRefundProviderReferences(Set.of(
                new ProviderReconciliationReference("fake", "fake_refund_2"),
                new ProviderReconciliationReference("fake", "missing_refund")
        )))
                .containsExactly(new ProviderReconciliationReference("fake", "fake_refund_2"));
        assertThat(readAdapter.findLedgerTransactionsByReferences(Set.of(
                new LedgerTransactionLookupKey(
                        LedgerTransactionType.PAYMENT_CAPTURE,
                        "PAYMENT",
                        firstPaymentId.toString()
                ),
                new LedgerTransactionLookupKey(LedgerTransactionType.REFUND, "REFUND", uuid(99).toString())
        )))
                .extracting("id")
                .containsExactly(firstTransactionId);

        List<LedgerEntryReconciliationItem> entries = readAdapter.findLedgerEntriesByTransactionIds(List.of(
                firstTransactionId,
                secondTransactionId
        ));
        assertThat(entries)
                .extracting(LedgerEntryReconciliationItem::transactionId)
                .containsExactlyInAnyOrder(
                        firstTransactionId,
                        firstTransactionId,
                        secondTransactionId,
                        secondTransactionId
                );
        assertThat(entries)
                .extracting(LedgerEntryReconciliationItem::direction)
                .contains(LedgerEntryDirection.DEBIT, LedgerEntryDirection.CREDIT);
    }

    @Test
    void runStoreReturnsLatestCompletedRunWithLimitedIssueRows() {
        UUID olderRunId = runStorePort.startRun(Instant.parse("2026-07-10T08:00:00Z"));
        runStorePort.completeRun(
                olderRunId,
                new ReconciliationRunCompletion(
                        Instant.parse("2026-07-10T08:01:00Z"),
                        true,
                        1,
                        0,
                        1,
                        0
                ),
                List.of()
        );
        UUID latestRunId = runStorePort.startRun(Instant.parse("2026-07-11T08:00:00Z"));
        runStorePort.completeRun(
                latestRunId,
                new ReconciliationRunCompletion(
                        Instant.parse("2026-07-11T08:01:00Z"),
                        false,
                        5,
                        2,
                        7,
                        3
                ),
                List.of(issue("first"), issue("second"))
        );
        UUID failedRunId = runStorePort.startRun(Instant.parse("2026-07-12T08:00:00Z"));
        runStorePort.failRun(failedRunId, Instant.parse("2026-07-12T08:01:00Z"), "boom");

        ReconciliationReport report = runStorePort.findLatestCompleted(1).orElseThrow();

        assertThat(report.healthy()).isFalse();
        assertThat(report.generatedAt()).isEqualTo(Instant.parse("2026-07-11T08:01:00Z"));
        assertThat(report.checkedPayments()).isEqualTo(5);
        assertThat(report.checkedRefunds()).isEqualTo(2);
        assertThat(report.checkedLedgerTransactions()).isEqualTo(7);
        assertThat(report.issueCount()).isEqualTo(3);
        assertThat(report.issuesTruncated()).isTrue();
        assertThat(report.issues())
                .extracting(ReconciliationIssue::message)
                .containsExactly("first");
        assertThat(runStorePort.findCompleted(failedRunId, 10)).isEmpty();
    }

    private TestOrder insertOrder() {
        Long customerId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO user_model (
                            username,
                            email,
                            first_name,
                            last_name,
                            identity_subject
                        )
                        VALUES (?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                "reconciliation-" + UUID.randomUUID() + "@example.com",
                "reconciliation@example.com",
                "Reconciliation",
                "Customer",
                "reconciliation-" + UUID.randomUUID()
        );
        Long cartId = jdbcTemplate.queryForObject("SELECT nextval('cart_model_seq')", Long.class);
        UUID orderId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                        INSERT INTO cart_model (id, total, owner_id, currency)
                        VALUES (?, ?, ?, ?)
                        """,
                cartId,
                new BigDecimal("25.00"),
                customerId,
                "USD"
        );
        jdbcTemplate.update(
                """
                        INSERT INTO customer_order (
                            id,
                            customer_id,
                            cart_id,
                            status,
                            total_amount,
                            currency,
                            created_at,
                            version
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                orderId,
                customerId,
                cartId,
                "REFUNDED",
                new BigDecimal("25.00"),
                "USD",
                OffsetDateTime.now()
        );

        return new TestOrder(orderId, customerId);
    }

    private void insertPayment(UUID paymentId, TestOrder order, String status, String providerPaymentId) {
        jdbcTemplate.update(
                """
                        INSERT INTO payment_record (
                            id,
                            order_id,
                            customer_id,
                            amount,
                            currency,
                            status,
                            provider_code,
                            provider_idempotency_key,
                            provider_payment_id,
                            provider_status,
                            idempotency_key,
                            created_at,
                            completed_at,
                            version
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                paymentId,
                order.orderId(),
                order.customerId(),
                new BigDecimal("25.00"),
                "USD",
                status,
                "fake",
                "payment:fake:" + paymentId,
                providerPaymentId,
                "SUCCEEDED",
                "app-payment-" + paymentId,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private void insertRefund(UUID refundId, UUID paymentId, TestOrder order, String providerRefundId) {
        jdbcTemplate.update(
                """
                        INSERT INTO refund_record (
                            id,
                            payment_id,
                            order_id,
                            customer_id,
                            amount,
                            currency,
                            status,
                            provider_code,
                            provider_idempotency_key,
                            provider_refund_id,
                            provider_status,
                            idempotency_key,
                            reason,
                            created_at,
                            completed_at,
                            version
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                refundId,
                paymentId,
                order.orderId(),
                order.customerId(),
                new BigDecimal("25.00"),
                "USD",
                "SUCCEEDED",
                "fake",
                "refund:fake:" + refundId,
                providerRefundId,
                "SUCCEEDED",
                "app-refund-" + refundId,
                "customer_request",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private void insertLedgerTransaction(
            UUID transactionId,
            String transactionType,
            String referenceType,
            String referenceId
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO ledger_transaction (
                            id,
                            transaction_type,
                            reference_type,
                            reference_id,
                            description,
                            posted_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                transactionId,
                transactionType,
                referenceType,
                referenceId,
                "reconciliation persistence test",
                OffsetDateTime.now()
        );
    }

    private void insertLedgerEntry(UUID transactionId, UUID accountId, String direction) {
        jdbcTemplate.update(
                """
                        INSERT INTO ledger_entry (
                            transaction_id,
                            account_id,
                            direction,
                            amount,
                            currency
                        )
                        VALUES (?, ?, ?, ?, ?)
                        """,
                transactionId,
                accountId,
                direction,
                new BigDecimal("25.00"),
                "USD"
        );
    }

    private void insertWebhook(UUID id, String eventType, String providerObjectId) {
        jdbcTemplate.update(
                """
                        INSERT INTO provider_webhook_event (
                            id,
                            provider_name,
                            provider_event_id,
                            event_type,
                            payload_hash,
                            payload,
                            processing_status,
                            provider_object_id,
                            received_at,
                            processed_at,
                            version
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                id,
                "fake",
                "evt_" + id,
                eventType,
                UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""),
                "{}",
                "PROCESSED",
                providerObjectId,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private ReconciliationIssue issue(String message) {
        return new ReconciliationIssue(
                ReconciliationIssueCode.MISSING_PAYMENT_LEDGER_TRANSACTION,
                "PAYMENT",
                UUID.randomUUID().toString(),
                message
        );
    }

    private static UUID uuid(int value) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(value));
    }

    private record TestOrder(UUID orderId, long customerId) {
    }
}
