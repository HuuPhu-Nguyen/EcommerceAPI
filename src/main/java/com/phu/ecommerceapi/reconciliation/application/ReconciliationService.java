package com.phu.ecommerceapi.reconciliation.application;

import com.phu.ecommerceapi.ledger.domain.LedgerEntryDirection;
import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;
import com.phu.ecommerceapi.payment.application.PaymentIdempotencyRecoveryService;
import com.phu.ecommerceapi.payment.application.StripePaymentIntentSnapshot;
import com.phu.ecommerceapi.payment.application.StripeProviderReadPort;
import com.phu.ecommerceapi.payment.application.StripeRefundSnapshot;
import com.phu.ecommerceapi.payment.domain.ProviderWebhookEventType;
import com.phu.ecommerceapi.payment.domain.ProviderWebhookProcessingStatus;
import com.phu.ecommerceapi.payment.domain.PaymentStatus;
import com.phu.ecommerceapi.payment.domain.RefundStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {

    private static final String PAYMENT_REFERENCE_TYPE = "PAYMENT";
    private static final String REFUND_REFERENCE_TYPE = "REFUND";
    private static final String LEDGER_TRANSACTION_RESOURCE = "LEDGER_TRANSACTION";
    private static final String PAYMENT_RESOURCE = "PAYMENT";
    private static final String REFUND_RESOURCE = "REFUND";
    private static final String PROVIDER_WEBHOOK_EVENT_RESOURCE = "PROVIDER_WEBHOOK_EVENT";
    private static final String PROVIDER_CLEARING_ACCOUNT = "PAYMENT_PROVIDER_CLEARING";
    private static final String ORDER_REVENUE_ACCOUNT = "ORDER_REVENUE";
    private static final String FAKE_PROVIDER = "fake";
    private static final String STRIPE_PROVIDER = "stripe";

    private final ReconciliationReadPort reconciliationReadPort;
    private final ObjectProvider<StripeProviderReadPort> stripeProviderReadPort;
    private final PaymentIdempotencyRecoveryService paymentIdempotencyRecoveryService;

    public ReconciliationService(
            ReconciliationReadPort reconciliationReadPort,
            ObjectProvider<StripeProviderReadPort> stripeProviderReadPort,
            PaymentIdempotencyRecoveryService paymentIdempotencyRecoveryService
    ) {
        this.reconciliationReadPort = reconciliationReadPort;
        this.stripeProviderReadPort = stripeProviderReadPort;
        this.paymentIdempotencyRecoveryService = paymentIdempotencyRecoveryService;
    }

    public ReconciliationReport runReport() {
        paymentIdempotencyRecoveryService.recoverExpired();

        List<PaymentReconciliationItem> payments = reconciliationReadPort.findPayments();
        List<RefundReconciliationItem> refunds = reconciliationReadPort.findRefunds();
        List<ProviderWebhookReconciliationItem> webhookEvents = reconciliationReadPort.findProviderWebhookEvents();
        List<LedgerTransactionReconciliationItem> transactions = reconciliationReadPort.findLedgerTransactions();
        List<LedgerEntryReconciliationItem> entries = reconciliationReadPort.findLedgerEntries();

        Map<UUID, PaymentReconciliationItem> paymentsById = byId(payments, PaymentReconciliationItem::id);
        Map<UUID, RefundReconciliationItem> refundsById = byId(refunds, RefundReconciliationItem::id);
        Map<UUID, List<LedgerEntryReconciliationItem>> entriesByTransaction = entries.stream()
                .collect(Collectors.groupingBy(LedgerEntryReconciliationItem::transactionId));
        Map<LedgerReference, LedgerTransactionReconciliationItem> transactionsByReference = transactions.stream()
                .collect(Collectors.toMap(
                        LedgerReference::from,
                        Function.identity(),
                        (first, ignored) -> first
                ));

        List<ReconciliationIssue> issues = new ArrayList<>();
        checkPaymentProviderState(payments, issues);
        checkRefundProviderState(refunds, issues);
        checkProviderWebhookEvents(webhookEvents, payments, refunds, issues);
        checkLedgerBalances(transactions, entriesByTransaction, issues);
        checkPayments(payments, transactionsByReference, entriesByTransaction, issues);
        checkRefunds(refunds, paymentsById.keySet(), transactionsByReference, entriesByTransaction, issues);
        checkOrphanedLedgerTransactions(transactions, paymentsById, refundsById, issues);

        return new ReconciliationReport(
                issues.isEmpty(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                payments.size(),
                refunds.size(),
                transactions.size(),
                issues
        );
    }

    private void checkPaymentProviderState(
            List<PaymentReconciliationItem> payments,
            List<ReconciliationIssue> issues
    ) {
        for (PaymentReconciliationItem payment : payments) {
            String providerCode = providerCode(payment.providerCode());
            if (!isSupportedProvider(providerCode)) {
                issues.add(issue(
                        ReconciliationIssueCode.UNSUPPORTED_PAYMENT_PROVIDER,
                        PAYMENT_RESOURCE,
                        payment.id().toString(),
                        "Payment provider code is missing or unsupported: provider=" + providerLabel(providerCode)
                ));
                continue;
            }

            if (requiresCaptureLedger(payment.status()) && isBlank(payment.providerPaymentId())) {
                issues.add(issue(
                        ReconciliationIssueCode.MISSING_PROVIDER_REFERENCE,
                        PAYMENT_RESOURCE,
                        payment.id().toString(),
                        "Successful payment is missing provider payment reference: provider=" + providerCode
                ));
                continue;
            }

            if (STRIPE_PROVIDER.equals(providerCode) && !isBlank(payment.providerPaymentId())) {
                compareStripePaymentState(payment, providerCode, issues);
            }
        }
    }

    private void compareStripePaymentState(
            PaymentReconciliationItem payment,
            String providerCode,
            List<ReconciliationIssue> issues
    ) {
        Optional<StripePaymentIntentSnapshot> current = fetchStripePaymentIntent(payment.providerPaymentId());
        if (current.isEmpty()) {
            if (requiresCaptureLedger(payment.status())) {
                issues.add(issue(
                        ReconciliationIssueCode.PROVIDER_PAYMENT_STATE_MISMATCH,
                        PAYMENT_RESOURCE,
                        payment.id().toString(),
                        "Successful payment is missing at provider: provider=" + providerCode
                                + " providerPaymentId=" + payment.providerPaymentId()
                ));
            }
            return;
        }

        StripePaymentIntentSnapshot currentState = current.get();
        if (currentState.isSucceeded() && !requiresCaptureLedger(payment.status())) {
            issues.add(issue(
                    ReconciliationIssueCode.PROVIDER_PAYMENT_STATE_MISMATCH,
                    PAYMENT_RESOURCE,
                    payment.id().toString(),
                    "Provider payment succeeded but local payment is not succeeded: provider=" + providerCode
                            + " providerPaymentId=" + payment.providerPaymentId()
            ));
        }
        if (requiresCaptureLedger(payment.status()) && !currentState.isSucceeded()) {
            issues.add(issue(
                    ReconciliationIssueCode.PROVIDER_PAYMENT_STATE_MISMATCH,
                    PAYMENT_RESOURCE,
                    payment.id().toString(),
                    "Local payment succeeded but provider payment is not succeeded: provider=" + providerCode
                            + " providerPaymentId=" + payment.providerPaymentId()
                            + " providerStatus=" + currentState.status()
            ));
        }
    }

    private void checkRefundProviderState(
            List<RefundReconciliationItem> refunds,
            List<ReconciliationIssue> issues
    ) {
        for (RefundReconciliationItem refund : refunds) {
            String providerCode = providerCode(refund.providerCode());
            if (!isSupportedProvider(providerCode)) {
                issues.add(issue(
                        ReconciliationIssueCode.UNSUPPORTED_PAYMENT_PROVIDER,
                        REFUND_RESOURCE,
                        refund.id().toString(),
                        "Refund provider code is missing or unsupported: provider=" + providerLabel(providerCode)
                ));
                continue;
            }

            if (refund.status() == RefundStatus.SUCCEEDED && isBlank(refund.providerRefundId())) {
                issues.add(issue(
                        ReconciliationIssueCode.MISSING_PROVIDER_REFERENCE,
                        REFUND_RESOURCE,
                        refund.id().toString(),
                        "Succeeded refund is missing provider refund reference: provider=" + providerCode
                ));
                continue;
            }

            if (STRIPE_PROVIDER.equals(providerCode) && !isBlank(refund.providerRefundId())) {
                compareStripeRefundState(refund, providerCode, issues);
            }
        }
    }

    private void compareStripeRefundState(
            RefundReconciliationItem refund,
            String providerCode,
            List<ReconciliationIssue> issues
    ) {
        Optional<StripeRefundSnapshot> current = fetchStripeRefund(refund.providerRefundId());
        if (current.isEmpty()) {
            if (refund.status() == RefundStatus.SUCCEEDED) {
                issues.add(issue(
                        ReconciliationIssueCode.PROVIDER_REFUND_STATE_MISMATCH,
                        REFUND_RESOURCE,
                        refund.id().toString(),
                        "Succeeded refund is missing at provider: provider=" + providerCode
                                + " providerRefundId=" + refund.providerRefundId()
                ));
            }
            return;
        }

        StripeRefundSnapshot currentState = current.get();
        if (currentState.isSucceeded() && refund.status() != RefundStatus.SUCCEEDED) {
            issues.add(issue(
                    ReconciliationIssueCode.PROVIDER_REFUND_STATE_MISMATCH,
                    REFUND_RESOURCE,
                    refund.id().toString(),
                    "Provider refund succeeded but local refund is not succeeded: provider=" + providerCode
                            + " providerRefundId=" + refund.providerRefundId()
            ));
        }
        if (refund.status() == RefundStatus.SUCCEEDED && !currentState.isSucceeded()) {
            issues.add(issue(
                    ReconciliationIssueCode.PROVIDER_REFUND_STATE_MISMATCH,
                    REFUND_RESOURCE,
                    refund.id().toString(),
                    "Local refund succeeded but provider refund is not succeeded: provider=" + providerCode
                            + " providerRefundId=" + refund.providerRefundId()
                            + " providerStatus=" + currentState.status()
            ));
        }
    }

    private void checkProviderWebhookEvents(
            List<ProviderWebhookReconciliationItem> webhookEvents,
            List<PaymentReconciliationItem> payments,
            List<RefundReconciliationItem> refunds,
            List<ReconciliationIssue> issues
    ) {
        Set<ProviderReference> paymentReferences = payments.stream()
                .filter(payment -> !isBlank(payment.providerPaymentId()))
                .map(payment -> new ProviderReference(providerCode(payment.providerCode()), payment.providerPaymentId()))
                .collect(Collectors.toSet());
        Set<ProviderReference> refundReferences = refunds.stream()
                .filter(refund -> !isBlank(refund.providerRefundId()))
                .map(refund -> new ProviderReference(providerCode(refund.providerCode()), refund.providerRefundId()))
                .collect(Collectors.toSet());

        for (ProviderWebhookReconciliationItem event : webhookEvents) {
            if (!isReconciliationRelevant(event) || isBlank(event.providerObjectId())) {
                continue;
            }

            ProviderReference reference = new ProviderReference(providerCode(event.providerCode()), event.providerObjectId());
            if (isPaymentEvent(event.eventType()) && !paymentReferences.contains(reference)) {
                issues.add(orphanedProviderWebhookIssue(event, "payment"));
            }
            if (isRefundEvent(event.eventType()) && !refundReferences.contains(reference)) {
                issues.add(orphanedProviderWebhookIssue(event, "refund"));
            }
        }
    }

    private void checkLedgerBalances(
            List<LedgerTransactionReconciliationItem> transactions,
            Map<UUID, List<LedgerEntryReconciliationItem>> entriesByTransaction,
            List<ReconciliationIssue> issues
    ) {
        for (LedgerTransactionReconciliationItem transaction : transactions) {
            List<LedgerEntryReconciliationItem> entries = entriesByTransaction.getOrDefault(transaction.id(), List.of());
            if (entries.size() < 2) {
                issues.add(issue(
                        ReconciliationIssueCode.UNBALANCED_LEDGER_TRANSACTION,
                        LEDGER_TRANSACTION_RESOURCE,
                        transaction.id().toString(),
                        "Ledger transaction must contain at least two entries"
                ));
                continue;
            }

            Map<String, Map<LedgerEntryDirection, BigDecimal>> totals = totalsByCurrencyAndDirection(entries);
            for (Map.Entry<String, Map<LedgerEntryDirection, BigDecimal>> currencyTotals : totals.entrySet()) {
                BigDecimal debits = currencyTotals.getValue().getOrDefault(LedgerEntryDirection.DEBIT, BigDecimal.ZERO);
                BigDecimal credits = currencyTotals.getValue().getOrDefault(LedgerEntryDirection.CREDIT, BigDecimal.ZERO);
                if (debits.compareTo(credits) != 0) {
                    issues.add(issue(
                            ReconciliationIssueCode.UNBALANCED_LEDGER_TRANSACTION,
                            LEDGER_TRANSACTION_RESOURCE,
                            transaction.id().toString(),
                            "Ledger transaction debits and credits do not balance for " + currencyTotals.getKey()
                    ));
                }
            }
        }
    }

    private void checkPayments(
            List<PaymentReconciliationItem> payments,
            Map<LedgerReference, LedgerTransactionReconciliationItem> transactionsByReference,
            Map<UUID, List<LedgerEntryReconciliationItem>> entriesByTransaction,
            List<ReconciliationIssue> issues
    ) {
        for (PaymentReconciliationItem payment : payments) {
            if (!requiresCaptureLedger(payment.status())) {
                continue;
            }

            LedgerTransactionReconciliationItem transaction = transactionsByReference.get(paymentReference(payment.id()));
            if (transaction == null) {
                issues.add(issue(
                        ReconciliationIssueCode.MISSING_PAYMENT_LEDGER_TRANSACTION,
                        PAYMENT_RESOURCE,
                        payment.id().toString(),
                        "Successful payment is missing its capture ledger transaction"
                ));
                continue;
            }

            List<LedgerEntryReconciliationItem> entries = entriesByTransaction.getOrDefault(transaction.id(), List.of());
            if (!matchesExpectedPosting(
                    entries,
                    payment.amount(),
                    payment.currency(),
                    expected(PROVIDER_CLEARING_ACCOUNT, LedgerEntryDirection.DEBIT),
                    expected(ORDER_REVENUE_ACCOUNT, LedgerEntryDirection.CREDIT)
            )) {
                issues.add(issue(
                        ReconciliationIssueCode.PAYMENT_LEDGER_MISMATCH,
                        PAYMENT_RESOURCE,
                        payment.id().toString(),
                        "Payment capture ledger entries do not match payment amount, currency, and account directions"
                ));
            }
        }
    }

    private void checkRefunds(
            List<RefundReconciliationItem> refunds,
            Set<UUID> paymentIds,
            Map<LedgerReference, LedgerTransactionReconciliationItem> transactionsByReference,
            Map<UUID, List<LedgerEntryReconciliationItem>> entriesByTransaction,
            List<ReconciliationIssue> issues
    ) {
        for (RefundReconciliationItem refund : refunds) {
            if (refund.status() != RefundStatus.SUCCEEDED) {
                continue;
            }

            if (!paymentIds.contains(refund.paymentId())) {
                issues.add(issue(
                        ReconciliationIssueCode.ORPHANED_REFUND,
                        REFUND_RESOURCE,
                        refund.id().toString(),
                        "Succeeded refund references a missing payment"
                ));
            }

            LedgerTransactionReconciliationItem transaction = transactionsByReference.get(refundReference(refund.id()));
            if (transaction == null) {
                issues.add(issue(
                        ReconciliationIssueCode.MISSING_REFUND_LEDGER_TRANSACTION,
                        REFUND_RESOURCE,
                        refund.id().toString(),
                        "Succeeded refund is missing its reversing ledger transaction"
                ));
                continue;
            }

            List<LedgerEntryReconciliationItem> entries = entriesByTransaction.getOrDefault(transaction.id(), List.of());
            if (!matchesExpectedPosting(
                    entries,
                    refund.amount(),
                    refund.currency(),
                    expected(ORDER_REVENUE_ACCOUNT, LedgerEntryDirection.DEBIT),
                    expected(PROVIDER_CLEARING_ACCOUNT, LedgerEntryDirection.CREDIT)
            )) {
                issues.add(issue(
                        ReconciliationIssueCode.REFUND_LEDGER_MISMATCH,
                        REFUND_RESOURCE,
                        refund.id().toString(),
                        "Refund ledger entries do not reverse payment revenue and provider-clearing movement"
                ));
            }
        }
    }

    private void checkOrphanedLedgerTransactions(
            List<LedgerTransactionReconciliationItem> transactions,
            Map<UUID, PaymentReconciliationItem> paymentsById,
            Map<UUID, RefundReconciliationItem> refundsById,
            List<ReconciliationIssue> issues
    ) {
        for (LedgerTransactionReconciliationItem transaction : transactions) {
            LedgerReference reference = LedgerReference.from(transaction);
            if (isPaymentCaptureReference(reference)) {
                Optional<UUID> paymentId = parseUuid(reference.referenceId());
                boolean matched = paymentId
                        .map(paymentsById::get)
                        .map(payment -> requiresCaptureLedger(payment.status()))
                        .orElse(false);
                if (!matched) {
                    issues.add(orphanLedgerIssue(transaction, "Payment capture ledger transaction has no successful payment"));
                }
                continue;
            }

            if (isRefundReference(reference)) {
                Optional<UUID> refundId = parseUuid(reference.referenceId());
                boolean matched = refundId
                        .map(refundsById::get)
                        .map(refund -> refund.status() == RefundStatus.SUCCEEDED)
                        .orElse(false);
                if (!matched) {
                    issues.add(orphanLedgerIssue(transaction, "Refund ledger transaction has no succeeded refund"));
                }
                continue;
            }

            issues.add(orphanLedgerIssue(transaction, "Ledger transaction does not reference a recognized money movement"));
        }
    }

    private boolean matchesExpectedPosting(
            List<LedgerEntryReconciliationItem> entries,
            BigDecimal amount,
            String currency,
            ExpectedEntry debitEntry,
            ExpectedEntry creditEntry
    ) {
        String expectedCurrency = normalize(currency);
        if (entries.isEmpty() || expectedCurrency.isBlank()) {
            return false;
        }

        Set<String> currencies = entries.stream()
                .map(LedgerEntryReconciliationItem::currency)
                .map(ReconciliationService::normalize)
                .collect(Collectors.toCollection(HashSet::new));
        if (!currencies.equals(Set.of(expectedCurrency))) {
            return false;
        }

        return total(entries, null, LedgerEntryDirection.DEBIT, expectedCurrency).compareTo(amount) == 0
                && total(entries, null, LedgerEntryDirection.CREDIT, expectedCurrency).compareTo(amount) == 0
                && total(entries, debitEntry.accountCode(), debitEntry.direction(), expectedCurrency).compareTo(amount) == 0
                && total(entries, creditEntry.accountCode(), creditEntry.direction(), expectedCurrency).compareTo(amount) == 0;
    }

    private BigDecimal total(
            Collection<LedgerEntryReconciliationItem> entries,
            String accountCode,
            LedgerEntryDirection direction,
            String currency
    ) {
        String normalizedAccountCode = accountCode == null ? null : normalize(accountCode);
        String normalizedCurrency = normalize(currency);
        return entries.stream()
                .filter(entry -> entry.direction() == direction)
                .filter(entry -> normalize(entry.currency()).equals(normalizedCurrency))
                .filter(entry -> normalizedAccountCode == null || normalize(entry.accountCode()).equals(normalizedAccountCode))
                .map(LedgerEntryReconciliationItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, Map<LedgerEntryDirection, BigDecimal>> totalsByCurrencyAndDirection(
            List<LedgerEntryReconciliationItem> entries
    ) {
        Map<String, Map<LedgerEntryDirection, BigDecimal>> totals = new HashMap<>();
        for (LedgerEntryReconciliationItem entry : entries) {
            totals.computeIfAbsent(normalize(entry.currency()), ignored -> new EnumMap<>(LedgerEntryDirection.class))
                    .merge(entry.direction(), entry.amount(), BigDecimal::add);
        }
        return totals;
    }

    private boolean requiresCaptureLedger(PaymentStatus status) {
        return status == PaymentStatus.SUCCEEDED || status == PaymentStatus.REFUNDED;
    }

    private boolean isPaymentCaptureReference(LedgerReference reference) {
        return reference.transactionType() == LedgerTransactionType.PAYMENT_CAPTURE
                && PAYMENT_REFERENCE_TYPE.equals(reference.referenceType());
    }

    private boolean isRefundReference(LedgerReference reference) {
        return reference.transactionType() == LedgerTransactionType.REFUND
                && REFUND_REFERENCE_TYPE.equals(reference.referenceType());
    }

    private LedgerReference paymentReference(UUID paymentId) {
        return new LedgerReference(LedgerTransactionType.PAYMENT_CAPTURE, PAYMENT_REFERENCE_TYPE, paymentId.toString());
    }

    private LedgerReference refundReference(UUID refundId) {
        return new LedgerReference(LedgerTransactionType.REFUND, REFUND_REFERENCE_TYPE, refundId.toString());
    }

    private ReconciliationIssue orphanLedgerIssue(
            LedgerTransactionReconciliationItem transaction,
            String message
    ) {
        return issue(
                ReconciliationIssueCode.ORPHANED_LEDGER_TRANSACTION,
                LEDGER_TRANSACTION_RESOURCE,
                transaction.id().toString(),
                message
        );
    }

    private ReconciliationIssue orphanedProviderWebhookIssue(
            ProviderWebhookReconciliationItem event,
            String resourceKind
    ) {
        return issue(
                ReconciliationIssueCode.ORPHANED_PROVIDER_WEBHOOK_EVENT,
                PROVIDER_WEBHOOK_EVENT_RESOURCE,
                event.id().toString(),
                "Provider webhook references a missing internal " + resourceKind
                        + ": provider=" + providerLabel(event.providerCode())
                        + " providerObjectId=" + event.providerObjectId()
        );
    }

    private Optional<StripePaymentIntentSnapshot> fetchStripePaymentIntent(String providerPaymentId) {
        StripeProviderReadPort readPort = stripeProviderReadPort.getIfAvailable();
        if (readPort == null) {
            return Optional.empty();
        }
        return readPort.fetchPaymentIntent(providerPaymentId);
    }

    private Optional<StripeRefundSnapshot> fetchStripeRefund(String providerRefundId) {
        StripeProviderReadPort readPort = stripeProviderReadPort.getIfAvailable();
        if (readPort == null) {
            return Optional.empty();
        }
        return readPort.fetchRefund(providerRefundId);
    }

    private boolean isSupportedProvider(String providerCode) {
        return FAKE_PROVIDER.equals(providerCode) || STRIPE_PROVIDER.equals(providerCode);
    }

    private boolean isReconciliationRelevant(ProviderWebhookReconciliationItem event) {
        return event.processingStatus() == ProviderWebhookProcessingStatus.PROCESSED
                || event.processingStatus() == ProviderWebhookProcessingStatus.RECONCILIATION_REQUIRED;
    }

    private boolean isPaymentEvent(ProviderWebhookEventType eventType) {
        return eventType == ProviderWebhookEventType.PAYMENT_SUCCEEDED
                || eventType == ProviderWebhookEventType.PAYMENT_FAILED;
    }

    private boolean isRefundEvent(ProviderWebhookEventType eventType) {
        return eventType == ProviderWebhookEventType.REFUND_SUCCEEDED
                || eventType == ProviderWebhookEventType.REFUND_FAILED;
    }

    private static String providerCode(String providerCode) {
        return providerCode == null ? "" : providerCode.trim().toLowerCase(Locale.ROOT);
    }

    private static String providerLabel(String providerCode) {
        String normalizedProviderCode = providerCode(providerCode);
        return normalizedProviderCode.isBlank() ? "missing" : normalizedProviderCode;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ReconciliationIssue issue(
            ReconciliationIssueCode code,
            String resourceType,
            String resourceId,
            String message
    ) {
        return new ReconciliationIssue(code, resourceType, resourceId, message);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private <T> Map<UUID, T> byId(List<T> items, Function<T, UUID> idExtractor) {
        return items.stream()
                .collect(Collectors.toMap(
                        idExtractor,
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }

    private ExpectedEntry expected(String accountCode, LedgerEntryDirection direction) {
        return new ExpectedEntry(accountCode, direction);
    }

    private record ExpectedEntry(String accountCode, LedgerEntryDirection direction) {
    }

    private record ProviderReference(String providerCode, String providerObjectId) {

        private ProviderReference {
            providerCode = ReconciliationService.providerCode(providerCode);
            providerObjectId = providerObjectId == null ? "" : providerObjectId.trim();
        }
    }

    private record LedgerReference(
            LedgerTransactionType transactionType,
            String referenceType,
            String referenceId
    ) {

        private LedgerReference {
            referenceType = normalize(referenceType);
            referenceId = referenceId == null ? "" : referenceId.trim();
        }

        private static LedgerReference from(LedgerTransactionReconciliationItem transaction) {
            return new LedgerReference(
                    transaction.transactionType(),
                    transaction.referenceType(),
                    transaction.referenceId()
            );
        }
    }
}
