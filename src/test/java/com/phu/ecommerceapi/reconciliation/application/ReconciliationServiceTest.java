package com.phu.ecommerceapi.reconciliation.application;

import com.phu.ecommerceapi.ledger.domain.LedgerEntryDirection;
import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;
import com.phu.ecommerceapi.payment.application.PaymentIdempotencyRecoveryService;
import com.phu.ecommerceapi.payment.application.StripePaymentIntentSnapshot;
import com.phu.ecommerceapi.payment.application.StripeProviderReadPort;
import com.phu.ecommerceapi.payment.application.StripeRefundSnapshot;
import com.phu.ecommerceapi.payment.domain.PaymentStatus;
import com.phu.ecommerceapi.payment.domain.ProviderWebhookEventType;
import com.phu.ecommerceapi.payment.domain.ProviderWebhookProcessingStatus;
import com.phu.ecommerceapi.payment.domain.RefundStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    private static final String PROVIDER_CLEARING_ACCOUNT = "PAYMENT_PROVIDER_CLEARING";
    private static final String ORDER_REVENUE_ACCOUNT = "ORDER_REVENUE";

    @Mock
    private ReconciliationReadPort reconciliationReadPort;

    @Mock
    private ObjectProvider<StripeProviderReadPort> stripeProviderReadPortProvider;

    @Mock
    private StripeProviderReadPort stripeProviderReadPort;

    @Mock
    private PaymentIdempotencyRecoveryService paymentIdempotencyRecoveryService;

    @InjectMocks
    private ReconciliationService reconciliationService;

    @Test
    void healthyReportWhenPaymentsRefundsAndLedgerEntriesMatch() {
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        UUID paymentTransactionId = UUID.randomUUID();
        UUID refundTransactionId = UUID.randomUUID();

        givenRows(
                List.of(payment(paymentId, "25.00", PaymentStatus.REFUNDED)),
                List.of(refund(refundId, paymentId, "25.00", RefundStatus.SUCCEEDED)),
                List.of(
                        transaction(paymentTransactionId, LedgerTransactionType.PAYMENT_CAPTURE, "PAYMENT", paymentId),
                        transaction(refundTransactionId, LedgerTransactionType.REFUND, "REFUND", refundId)
                ),
                List.of(
                        entry(paymentTransactionId, LedgerEntryDirection.DEBIT, "25.00", PROVIDER_CLEARING_ACCOUNT),
                        entry(paymentTransactionId, LedgerEntryDirection.CREDIT, "25.00", ORDER_REVENUE_ACCOUNT),
                        entry(refundTransactionId, LedgerEntryDirection.DEBIT, "25.00", ORDER_REVENUE_ACCOUNT),
                        entry(refundTransactionId, LedgerEntryDirection.CREDIT, "25.00", PROVIDER_CLEARING_ACCOUNT)
                )
        );

        ReconciliationReport report = reconciliationService.runReport();

        assertThat(report.healthy()).isTrue();
        assertThat(report.checkedPayments()).isEqualTo(1);
        assertThat(report.checkedRefunds()).isEqualTo(1);
        assertThat(report.checkedLedgerTransactions()).isEqualTo(2);
        assertThat(report.issues()).isEmpty();
    }

    @Test
    void brokenMoneyMovementIsReported() {
        UUID missingLedgerPaymentId = UUID.randomUUID();
        UUID mismatchedPaymentId = UUID.randomUUID();
        UUID orphanRefundPaymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        UUID mismatchedPaymentTransactionId = UUID.randomUUID();
        UUID orphanLedgerTransactionId = UUID.randomUUID();
        UUID unbalancedTransactionId = UUID.randomUUID();

        givenRows(
                List.of(
                        payment(missingLedgerPaymentId, "10.00", PaymentStatus.SUCCEEDED),
                        payment(mismatchedPaymentId, "5.00", PaymentStatus.SUCCEEDED)
                ),
                List.of(refund(refundId, orphanRefundPaymentId, "7.00", RefundStatus.SUCCEEDED)),
                List.of(
                        transaction(mismatchedPaymentTransactionId, LedgerTransactionType.PAYMENT_CAPTURE, "PAYMENT", mismatchedPaymentId),
                        transaction(orphanLedgerTransactionId, LedgerTransactionType.PAYMENT_CAPTURE, "PAYMENT", UUID.randomUUID()),
                        transaction(unbalancedTransactionId, LedgerTransactionType.REFUND, "REFUND", UUID.randomUUID())
                ),
                List.of(
                        entry(mismatchedPaymentTransactionId, LedgerEntryDirection.DEBIT, "4.00", PROVIDER_CLEARING_ACCOUNT),
                        entry(mismatchedPaymentTransactionId, LedgerEntryDirection.CREDIT, "4.00", ORDER_REVENUE_ACCOUNT),
                        entry(orphanLedgerTransactionId, LedgerEntryDirection.DEBIT, "3.00", PROVIDER_CLEARING_ACCOUNT),
                        entry(orphanLedgerTransactionId, LedgerEntryDirection.CREDIT, "3.00", ORDER_REVENUE_ACCOUNT),
                        entry(unbalancedTransactionId, LedgerEntryDirection.DEBIT, "9.00", ORDER_REVENUE_ACCOUNT),
                        entry(unbalancedTransactionId, LedgerEntryDirection.CREDIT, "8.00", PROVIDER_CLEARING_ACCOUNT)
                )
        );

        ReconciliationReport report = reconciliationService.runReport();

        assertThat(report.healthy()).isFalse();
        assertThat(report.issues())
                .extracting(ReconciliationIssue::code)
                .contains(
                        ReconciliationIssueCode.UNBALANCED_LEDGER_TRANSACTION,
                        ReconciliationIssueCode.MISSING_PAYMENT_LEDGER_TRANSACTION,
                        ReconciliationIssueCode.PAYMENT_LEDGER_MISMATCH,
                        ReconciliationIssueCode.ORPHANED_REFUND,
                        ReconciliationIssueCode.MISSING_REFUND_LEDGER_TRANSACTION,
                        ReconciliationIssueCode.ORPHANED_LEDGER_TRANSACTION
                );
    }

    @Test
    void providerIssueIncludesProviderCodeWhenSuccessfulPaymentMissingProviderReference() {
        UUID paymentId = UUID.randomUUID();
        UUID paymentTransactionId = UUID.randomUUID();

        givenRows(
                List.of(payment(paymentId, "25.00", PaymentStatus.SUCCEEDED, "stripe", null)),
                List.of(),
                List.of(transaction(paymentTransactionId, LedgerTransactionType.PAYMENT_CAPTURE, "PAYMENT", paymentId)),
                List.of(
                        entry(paymentTransactionId, LedgerEntryDirection.DEBIT, "25.00", PROVIDER_CLEARING_ACCOUNT),
                        entry(paymentTransactionId, LedgerEntryDirection.CREDIT, "25.00", ORDER_REVENUE_ACCOUNT)
                )
        );

        ReconciliationReport report = reconciliationService.runReport();

        assertThat(report.healthy()).isFalse();
        assertThat(report.issues())
                .filteredOn(issue -> issue.code() == ReconciliationIssueCode.MISSING_PROVIDER_REFERENCE)
                .singleElement()
                .extracting(ReconciliationIssue::message)
                .asString()
                .contains("provider=stripe");
        verifyNoInteractions(stripeProviderReadPortProvider);
    }

    @Test
    void stripeProviderSucceededPaymentIsFlaggedWhenLocalPaymentIsNotSucceeded() {
        UUID paymentId = UUID.randomUUID();
        when(stripeProviderReadPortProvider.getIfAvailable()).thenReturn(stripeProviderReadPort);
        when(stripeProviderReadPort.fetchPaymentIntent("pi_reconciled_success"))
                .thenReturn(Optional.of(stripePaymentIntent("pi_reconciled_success", "succeeded")));

        givenRows(
                List.of(payment(paymentId, "25.00", PaymentStatus.PENDING, "stripe", "pi_reconciled_success")),
                List.of(),
                List.of(),
                List.of()
        );

        ReconciliationReport report = reconciliationService.runReport();

        assertThat(report.healthy()).isFalse();
        assertThat(report.issues())
                .filteredOn(issue -> issue.code() == ReconciliationIssueCode.PROVIDER_PAYMENT_STATE_MISMATCH)
                .singleElement()
                .extracting(ReconciliationIssue::message)
                .asString()
                .contains("provider=stripe", "providerPaymentId=pi_reconciled_success");
    }

    @Test
    void stripeRefundSucceededLocallyIsFlaggedWhenProviderRefundIsNotSucceeded() {
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        UUID paymentTransactionId = UUID.randomUUID();
        UUID refundTransactionId = UUID.randomUUID();
        when(stripeProviderReadPortProvider.getIfAvailable()).thenReturn(stripeProviderReadPort);
        when(stripeProviderReadPort.fetchPaymentIntent("pi_refunded"))
                .thenReturn(Optional.of(stripePaymentIntent("pi_refunded", "succeeded")));
        when(stripeProviderReadPort.fetchRefund("re_failed"))
                .thenReturn(Optional.of(stripeRefund("re_failed", "failed")));

        givenRows(
                List.of(payment(paymentId, "25.00", PaymentStatus.REFUNDED, "stripe", "pi_refunded")),
                List.of(refund(refundId, paymentId, "25.00", RefundStatus.SUCCEEDED, "stripe", "re_failed")),
                List.of(
                        transaction(paymentTransactionId, LedgerTransactionType.PAYMENT_CAPTURE, "PAYMENT", paymentId),
                        transaction(refundTransactionId, LedgerTransactionType.REFUND, "REFUND", refundId)
                ),
                List.of(
                        entry(paymentTransactionId, LedgerEntryDirection.DEBIT, "25.00", PROVIDER_CLEARING_ACCOUNT),
                        entry(paymentTransactionId, LedgerEntryDirection.CREDIT, "25.00", ORDER_REVENUE_ACCOUNT),
                        entry(refundTransactionId, LedgerEntryDirection.DEBIT, "25.00", ORDER_REVENUE_ACCOUNT),
                        entry(refundTransactionId, LedgerEntryDirection.CREDIT, "25.00", PROVIDER_CLEARING_ACCOUNT)
                )
        );

        ReconciliationReport report = reconciliationService.runReport();

        assertThat(report.healthy()).isFalse();
        assertThat(report.issues())
                .filteredOn(issue -> issue.code() == ReconciliationIssueCode.PROVIDER_REFUND_STATE_MISMATCH)
                .singleElement()
                .extracting(ReconciliationIssue::message)
                .asString()
                .contains("provider=stripe", "providerRefundId=re_failed", "providerStatus=failed");
    }

    @Test
    void processedProviderWebhookEventWithoutMatchingInternalPaymentIsReported() {
        UUID eventId = UUID.randomUUID();

        givenRows(
                List.of(),
                List.of(),
                List.of(webhookEvent(
                        eventId,
                        "stripe",
                        ProviderWebhookEventType.PAYMENT_SUCCEEDED,
                        ProviderWebhookProcessingStatus.PROCESSED,
                        "pi_missing"
                )),
                List.of(),
                List.of()
        );

        ReconciliationReport report = reconciliationService.runReport();

        assertThat(report.healthy()).isFalse();
        assertThat(report.issues())
                .filteredOn(issue -> issue.code() == ReconciliationIssueCode.ORPHANED_PROVIDER_WEBHOOK_EVENT)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.resourceId()).isEqualTo(eventId.toString());
                    assertThat(issue.message()).contains("provider=stripe", "providerObjectId=pi_missing");
                });
    }

    private void givenRows(
            List<PaymentReconciliationItem> payments,
            List<RefundReconciliationItem> refunds,
            List<LedgerTransactionReconciliationItem> transactions,
            List<LedgerEntryReconciliationItem> entries
    ) {
        givenRows(payments, refunds, List.of(), transactions, entries);
    }

    private void givenRows(
            List<PaymentReconciliationItem> payments,
            List<RefundReconciliationItem> refunds,
            List<ProviderWebhookReconciliationItem> webhookEvents,
            List<LedgerTransactionReconciliationItem> transactions,
            List<LedgerEntryReconciliationItem> entries
    ) {
        when(reconciliationReadPort.findPayments()).thenReturn(payments);
        when(reconciliationReadPort.findRefunds()).thenReturn(refunds);
        when(reconciliationReadPort.findProviderWebhookEvents()).thenReturn(webhookEvents);
        when(reconciliationReadPort.findLedgerTransactions()).thenReturn(transactions);
        when(reconciliationReadPort.findLedgerEntries()).thenReturn(entries);
    }

    private PaymentReconciliationItem payment(UUID id, String amount, PaymentStatus status) {
        return payment(id, amount, status, "fake", "fake_payment_" + id);
    }

    private PaymentReconciliationItem payment(
            UUID id,
            String amount,
            PaymentStatus status,
            String providerCode,
            String providerPaymentId
    ) {
        return new PaymentReconciliationItem(
                id,
                new BigDecimal(amount),
                "USD",
                status,
                providerCode,
                providerPaymentId
        );
    }

    private RefundReconciliationItem refund(UUID id, UUID paymentId, String amount, RefundStatus status) {
        return refund(id, paymentId, amount, status, "fake", "fake_refund_" + id);
    }

    private RefundReconciliationItem refund(
            UUID id,
            UUID paymentId,
            String amount,
            RefundStatus status,
            String providerCode,
            String providerRefundId
    ) {
        return new RefundReconciliationItem(
                id,
                paymentId,
                new BigDecimal(amount),
                "USD",
                status,
                providerCode,
                providerRefundId
        );
    }

    private ProviderWebhookReconciliationItem webhookEvent(
            UUID id,
            String providerCode,
            ProviderWebhookEventType eventType,
            ProviderWebhookProcessingStatus processingStatus,
            String providerObjectId
    ) {
        return new ProviderWebhookReconciliationItem(
                id,
                providerCode,
                eventType,
                processingStatus,
                providerObjectId,
                null
        );
    }

    private LedgerTransactionReconciliationItem transaction(
            UUID id,
            LedgerTransactionType type,
            String referenceType,
            UUID referenceId
    ) {
        return new LedgerTransactionReconciliationItem(id, type, referenceType, referenceId.toString());
    }

    private LedgerEntryReconciliationItem entry(
            UUID transactionId,
            LedgerEntryDirection direction,
            String amount,
            String accountCode
    ) {
        return new LedgerEntryReconciliationItem(transactionId, direction, new BigDecimal(amount), "USD", accountCode);
    }

    private StripePaymentIntentSnapshot stripePaymentIntent(String providerPaymentId, String status) {
        return new StripePaymentIntentSnapshot(providerPaymentId, status, null, "provider state");
    }

    private StripeRefundSnapshot stripeRefund(String providerRefundId, String status) {
        return new StripeRefundSnapshot(providerRefundId, status, null, "provider state");
    }
}
