package com.phu.ecommerceapi.reconciliation.application;

import com.phu.ecommerceapi.ledger.domain.LedgerEntryDirection;
import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerEntryRepository;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerTransactionRepository;
import com.phu.ecommerceapi.payment.domain.PaymentStatus;
import com.phu.ecommerceapi.payment.domain.RefundStatus;
import com.phu.ecommerceapi.payment.infrastructure.PaymentRecordRepository;
import com.phu.ecommerceapi.payment.infrastructure.RefundRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    private static final String PROVIDER_CLEARING_ACCOUNT = "PAYMENT_PROVIDER_CLEARING";
    private static final String ORDER_REVENUE_ACCOUNT = "ORDER_REVENUE";

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private RefundRecordRepository refundRecordRepository;

    @Mock
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

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

    private void givenRows(
            List<PaymentReconciliationItem> payments,
            List<RefundReconciliationItem> refunds,
            List<LedgerTransactionReconciliationItem> transactions,
            List<LedgerEntryReconciliationItem> entries
    ) {
        when(paymentRecordRepository.findAllForReconciliation()).thenReturn(payments);
        when(refundRecordRepository.findAllForReconciliation()).thenReturn(refunds);
        when(ledgerTransactionRepository.findAllForReconciliation()).thenReturn(transactions);
        when(ledgerEntryRepository.findAllForReconciliation()).thenReturn(entries);
    }

    private PaymentReconciliationItem payment(UUID id, String amount, PaymentStatus status) {
        return new PaymentReconciliationItem(id, new BigDecimal(amount), "USD", status);
    }

    private RefundReconciliationItem refund(UUID id, UUID paymentId, String amount, RefundStatus status) {
        return new RefundReconciliationItem(id, paymentId, new BigDecimal(amount), "USD", status);
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
}
