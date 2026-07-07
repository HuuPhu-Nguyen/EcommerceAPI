package com.phu.ecommerceapi.reconciliation.infrastructure;

import com.phu.ecommerceapi.ledger.infrastructure.LedgerEntryRepository;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerTransactionRepository;
import com.phu.ecommerceapi.payment.infrastructure.PaymentRecordRepository;
import com.phu.ecommerceapi.payment.infrastructure.RefundRecordRepository;
import com.phu.ecommerceapi.reconciliation.application.LedgerEntryReconciliationItem;
import com.phu.ecommerceapi.reconciliation.application.LedgerTransactionReconciliationItem;
import com.phu.ecommerceapi.reconciliation.application.PaymentReconciliationItem;
import com.phu.ecommerceapi.reconciliation.application.ReconciliationReadPort;
import com.phu.ecommerceapi.reconciliation.application.RefundReconciliationItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JpaReconciliationReadAdapter implements ReconciliationReadPort {

    private final PaymentRecordRepository paymentRecordRepository;
    private final RefundRecordRepository refundRecordRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public JpaReconciliationReadAdapter(
            PaymentRecordRepository paymentRecordRepository,
            RefundRecordRepository refundRecordRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            LedgerEntryRepository ledgerEntryRepository
    ) {
        this.paymentRecordRepository = paymentRecordRepository;
        this.refundRecordRepository = refundRecordRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Override
    public List<PaymentReconciliationItem> findPayments() {
        return paymentRecordRepository.findAllForReconciliation();
    }

    @Override
    public List<RefundReconciliationItem> findRefunds() {
        return refundRecordRepository.findAllForReconciliation();
    }

    @Override
    public List<LedgerTransactionReconciliationItem> findLedgerTransactions() {
        return ledgerTransactionRepository.findAllForReconciliation();
    }

    @Override
    public List<LedgerEntryReconciliationItem> findLedgerEntries() {
        return ledgerEntryRepository.findAllForReconciliation();
    }
}
