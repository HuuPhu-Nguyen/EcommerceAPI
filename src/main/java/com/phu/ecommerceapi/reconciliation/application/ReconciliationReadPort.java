package com.phu.ecommerceapi.reconciliation.application;

import java.util.List;

public interface ReconciliationReadPort {

    List<PaymentReconciliationItem> findPayments();

    List<RefundReconciliationItem> findRefunds();

    List<LedgerTransactionReconciliationItem> findLedgerTransactions();

    List<LedgerEntryReconciliationItem> findLedgerEntries();
}
