package com.phu.ecommerceapi.reconciliation.application;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ReconciliationReadPort {

    List<PaymentReconciliationItem> findPaymentsAfterId(UUID afterIdExclusive, int limit);

    List<PaymentReconciliationItem> findPaymentsByIds(Collection<UUID> paymentIds);

    Set<UUID> findPaymentIdsByIds(Collection<UUID> paymentIds);

    Set<ProviderReconciliationReference> findPaymentProviderReferences(
            Collection<ProviderReconciliationReference> providerReferences
    );

    List<RefundReconciliationItem> findRefundsAfterId(UUID afterIdExclusive, int limit);

    List<RefundReconciliationItem> findRefundsByIds(Collection<UUID> refundIds);

    Set<UUID> findRefundIdsByIds(Collection<UUID> refundIds);

    Set<ProviderReconciliationReference> findRefundProviderReferences(
            Collection<ProviderReconciliationReference> providerReferences
    );

    List<ProviderWebhookReconciliationItem> findProviderWebhookEventsAfterId(UUID afterIdExclusive, int limit);

    List<LedgerTransactionReconciliationItem> findLedgerTransactionsAfterId(UUID afterIdExclusive, int limit);

    List<LedgerTransactionReconciliationItem> findLedgerTransactionsByReferences(
            Collection<LedgerTransactionLookupKey> references
    );

    List<LedgerEntryReconciliationItem> findLedgerEntriesByTransactionIds(Collection<UUID> transactionIds);
}
