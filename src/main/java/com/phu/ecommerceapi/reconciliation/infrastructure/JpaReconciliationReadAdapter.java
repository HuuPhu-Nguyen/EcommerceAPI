package com.phu.ecommerceapi.reconciliation.infrastructure;

import com.phu.ecommerceapi.ledger.infrastructure.LedgerEntryRepository;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerTransactionRepository;
import com.phu.ecommerceapi.payment.infrastructure.PaymentRecordRepository;
import com.phu.ecommerceapi.payment.infrastructure.ProviderWebhookEventRepository;
import com.phu.ecommerceapi.payment.infrastructure.RefundRecordRepository;
import com.phu.ecommerceapi.reconciliation.application.LedgerEntryReconciliationItem;
import com.phu.ecommerceapi.reconciliation.application.LedgerTransactionReconciliationItem;
import com.phu.ecommerceapi.reconciliation.application.LedgerTransactionLookupKey;
import com.phu.ecommerceapi.reconciliation.application.PaymentReconciliationItem;
import com.phu.ecommerceapi.reconciliation.application.ProviderReconciliationReference;
import com.phu.ecommerceapi.reconciliation.application.ProviderWebhookReconciliationItem;
import com.phu.ecommerceapi.reconciliation.application.ReconciliationReadPort;
import com.phu.ecommerceapi.reconciliation.application.RefundReconciliationItem;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JpaReconciliationReadAdapter implements ReconciliationReadPort {

    private final PaymentRecordRepository paymentRecordRepository;
    private final RefundRecordRepository refundRecordRepository;
    private final ProviderWebhookEventRepository providerWebhookEventRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public JpaReconciliationReadAdapter(
            PaymentRecordRepository paymentRecordRepository,
            RefundRecordRepository refundRecordRepository,
            ProviderWebhookEventRepository providerWebhookEventRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            LedgerEntryRepository ledgerEntryRepository
    ) {
        this.paymentRecordRepository = paymentRecordRepository;
        this.refundRecordRepository = refundRecordRepository;
        this.providerWebhookEventRepository = providerWebhookEventRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Override
    public List<PaymentReconciliationItem> findPaymentsAfterId(UUID afterIdExclusive, int limit) {
        return paymentRecordRepository.findPageForReconciliation(afterIdExclusive, page(limit));
    }

    @Override
    public List<PaymentReconciliationItem> findPaymentsByIds(Collection<UUID> paymentIds) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            return List.of();
        }
        return paymentRecordRepository.findByIdsForReconciliation(paymentIds);
    }

    @Override
    public Set<UUID> findPaymentIdsByIds(Collection<UUID> paymentIds) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(paymentRecordRepository.findExistingIdsForReconciliation(paymentIds));
    }

    @Override
    public Set<ProviderReconciliationReference> findPaymentProviderReferences(
            Collection<ProviderReconciliationReference> providerReferences
    ) {
        if (providerReferences == null || providerReferences.isEmpty()) {
            return Set.of();
        }
        Set<ProviderReconciliationReference> requested = Set.copyOf(providerReferences);
        List<String> providerCodes = values(requested, ProviderReconciliationReference::providerCode);
        List<String> providerPaymentIds = values(requested, ProviderReconciliationReference::providerObjectId);
        return ledgerSafeSet(paymentRecordRepository.findProviderReferencesForReconciliation(
                providerCodes,
                providerPaymentIds
        ), requested);
    }

    @Override
    public List<RefundReconciliationItem> findRefundsAfterId(UUID afterIdExclusive, int limit) {
        return refundRecordRepository.findPageForReconciliation(afterIdExclusive, page(limit));
    }

    @Override
    public List<RefundReconciliationItem> findRefundsByIds(Collection<UUID> refundIds) {
        if (refundIds == null || refundIds.isEmpty()) {
            return List.of();
        }
        return refundRecordRepository.findByIdsForReconciliation(refundIds);
    }

    @Override
    public Set<UUID> findRefundIdsByIds(Collection<UUID> refundIds) {
        if (refundIds == null || refundIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(refundRecordRepository.findExistingIdsForReconciliation(refundIds));
    }

    @Override
    public Set<ProviderReconciliationReference> findRefundProviderReferences(
            Collection<ProviderReconciliationReference> providerReferences
    ) {
        if (providerReferences == null || providerReferences.isEmpty()) {
            return Set.of();
        }
        Set<ProviderReconciliationReference> requested = Set.copyOf(providerReferences);
        List<String> providerCodes = values(requested, ProviderReconciliationReference::providerCode);
        List<String> providerRefundIds = values(requested, ProviderReconciliationReference::providerObjectId);
        return ledgerSafeSet(refundRecordRepository.findProviderReferencesForReconciliation(
                providerCodes,
                providerRefundIds
        ), requested);
    }

    @Override
    public List<ProviderWebhookReconciliationItem> findProviderWebhookEventsAfterId(UUID afterIdExclusive, int limit) {
        return providerWebhookEventRepository.findPageForReconciliation(afterIdExclusive, page(limit));
    }

    @Override
    public List<LedgerTransactionReconciliationItem> findLedgerTransactionsAfterId(UUID afterIdExclusive, int limit) {
        return ledgerTransactionRepository.findPageForReconciliation(afterIdExclusive, page(limit));
    }

    @Override
    public List<LedgerTransactionReconciliationItem> findLedgerTransactionsByReferences(
            Collection<LedgerTransactionLookupKey> references
    ) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        Set<LedgerTransactionLookupKey> requested = Set.copyOf(references);
        List<LedgerTransactionReconciliationItem> candidates = ledgerTransactionRepository
                .findByReferenceCandidatesForReconciliation(
                        values(requested, LedgerTransactionLookupKey::transactionType),
                        values(requested, LedgerTransactionLookupKey::referenceType),
                        values(requested, LedgerTransactionLookupKey::referenceId)
                );
        return candidates.stream()
                .filter(item -> requested.contains(new LedgerTransactionLookupKey(
                        item.transactionType(),
                        item.referenceType(),
                        item.referenceId()
                )))
                .toList();
    }

    @Override
    public List<LedgerEntryReconciliationItem> findLedgerEntriesByTransactionIds(Collection<UUID> transactionIds) {
        if (transactionIds == null || transactionIds.isEmpty()) {
            return List.of();
        }
        return ledgerEntryRepository.findForReconciliationByTransactionIds(List.copyOf(transactionIds));
    }

    private PageRequest page(int limit) {
        return PageRequest.of(0, Math.max(1, limit));
    }

    private <T, R> List<R> values(Collection<T> source, Function<T, R> extractor) {
        return source.stream()
                .map(extractor)
                .distinct()
                .toList();
    }

    private Set<ProviderReconciliationReference> ledgerSafeSet(
            List<ProviderReconciliationReference> candidates,
            Set<ProviderReconciliationReference> requested
    ) {
        return candidates.stream()
                .filter(requested::contains)
                .collect(Collectors.toSet());
    }
}
