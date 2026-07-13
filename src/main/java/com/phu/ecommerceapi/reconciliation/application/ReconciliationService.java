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
import com.phu.ecommerceapi.shared.api.ConflictException;
import com.phu.ecommerceapi.shared.api.NotFoundException;
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
    private final ReconciliationRunStorePort runStorePort;
    private final ReconciliationRunLockPort runLockPort;
    private final ReconciliationProperties properties;
    private final ObjectProvider<StripeProviderReadPort> stripeProviderReadPort;
    private final PaymentIdempotencyRecoveryService paymentIdempotencyRecoveryService;

    public ReconciliationService(
            ReconciliationReadPort reconciliationReadPort,
            ReconciliationRunStorePort runStorePort,
            ReconciliationRunLockPort runLockPort,
            ReconciliationProperties properties,
            ObjectProvider<StripeProviderReadPort> stripeProviderReadPort,
            PaymentIdempotencyRecoveryService paymentIdempotencyRecoveryService
    ) {
        this.reconciliationReadPort = reconciliationReadPort;
        this.runStorePort = runStorePort;
        this.runLockPort = runLockPort;
        this.properties = properties;
        this.stripeProviderReadPort = stripeProviderReadPort;
        this.paymentIdempotencyRecoveryService = paymentIdempotencyRecoveryService;
    }

    public ReconciliationReport latestCompletedReport() {
        return runStorePort.findLatestCompleted(properties.maxIssuesPerRun())
                .orElseThrow(() -> new NotFoundException("Reconciliation report not found"));
    }

    public ReconciliationReport runReport() {
        ReconciliationRunLockPort.ReconciliationRunLock lock = runLockPort.tryAcquire()
                .orElseThrow(this::activeRunConflict);
        try (lock) {
            return runReportWithLock();
        }
    }

    public Optional<ReconciliationReport> runScheduledReport() {
        Optional<ReconciliationRunLockPort.ReconciliationRunLock> lock = runLockPort.tryAcquire();
        if (lock.isEmpty()) {
            return Optional.empty();
        }
        try (ReconciliationRunLockPort.ReconciliationRunLock acquiredLock = lock.get()) {
            return Optional.of(runReportWithLock());
        }
    }

    private ReconciliationReport runReportWithLock() {
        UUID runId = runStorePort.startRun(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        try {
            paymentIdempotencyRecoveryService.recoverExpired();

            ReconciliationRunAccumulator accumulator = new ReconciliationRunAccumulator(
                    properties.maxIssuesPerRun()
            );
            processPaymentPages(accumulator);
            processRefundPages(accumulator);
            processProviderWebhookPages(accumulator);
            processLedgerTransactionPages(accumulator);

            Instant completedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            runStorePort.completeRun(
                    runId,
                    new ReconciliationRunCompletion(
                            completedAt,
                            accumulator.healthy(),
                            accumulator.paymentCount(),
                            accumulator.refundCount(),
                            accumulator.ledgerTransactionCount(),
                            accumulator.issueCount()
                    ),
                    accumulator.storedIssues()
            );
            return runStorePort.findCompleted(runId, properties.maxIssuesPerRun())
                    .orElseThrow(() -> new IllegalStateException("Completed reconciliation run could not be read"));
        } catch (RuntimeException exception) {
            runStorePort.failRun(
                    runId,
                    Instant.now().truncatedTo(ChronoUnit.MILLIS),
                    exception.getMessage()
            );
            throw exception;
        }
    }

    private ConflictException activeRunConflict() {
        return new ConflictException(runStorePort.findActiveRunId()
                .map(runId -> "Reconciliation run is already active: runId=" + runId)
                .orElse("Reconciliation run is already active"));
    }

    private void processPaymentPages(ReconciliationRunAccumulator accumulator) {
        UUID afterIdExclusive = null;
        while (true) {
            List<PaymentReconciliationItem> page = reconciliationReadPort.findPaymentsAfterId(
                    afterIdExclusive,
                    properties.batchSize()
            );
            if (page.isEmpty()) {
                return;
            }
            accumulator.addPaymentCount(page.size());
            checkPaymentProviderState(page, accumulator);
            checkPayments(page, accumulator);
            afterIdExclusive = page.getLast().id();
        }
    }

    private void processRefundPages(ReconciliationRunAccumulator accumulator) {
        UUID afterIdExclusive = null;
        while (true) {
            List<RefundReconciliationItem> page = reconciliationReadPort.findRefundsAfterId(
                    afterIdExclusive,
                    properties.batchSize()
            );
            if (page.isEmpty()) {
                return;
            }
            accumulator.addRefundCount(page.size());
            checkRefundProviderState(page, accumulator);
            checkRefunds(page, accumulator);
            afterIdExclusive = page.getLast().id();
        }
    }

    private void processProviderWebhookPages(ReconciliationRunAccumulator accumulator) {
        UUID afterIdExclusive = null;
        while (true) {
            List<ProviderWebhookReconciliationItem> page = reconciliationReadPort
                    .findProviderWebhookEventsAfterId(afterIdExclusive, properties.batchSize());
            if (page.isEmpty()) {
                return;
            }
            checkProviderWebhookEvents(page, accumulator);
            afterIdExclusive = page.getLast().id();
        }
    }

    private void processLedgerTransactionPages(ReconciliationRunAccumulator accumulator) {
        UUID afterIdExclusive = null;
        while (true) {
            List<LedgerTransactionReconciliationItem> page = reconciliationReadPort
                    .findLedgerTransactionsAfterId(afterIdExclusive, properties.batchSize());
            if (page.isEmpty()) {
                return;
            }
            accumulator.addLedgerTransactionCount(page.size());
            Map<UUID, List<LedgerEntryReconciliationItem>> entriesByTransaction = entriesByTransaction(page);
            checkLedgerBalances(page, entriesByTransaction, accumulator);
            checkOrphanedLedgerTransactions(page, accumulator);
            afterIdExclusive = page.getLast().id();
        }
    }

    private void checkPaymentProviderState(
            List<PaymentReconciliationItem> payments,
            ReconciliationRunAccumulator accumulator
    ) {
        for (PaymentReconciliationItem payment : payments) {
            String providerCode = providerCode(payment.providerCode());
            if (!isSupportedProvider(providerCode)) {
                accumulator.add(issue(
                        ReconciliationIssueCode.UNSUPPORTED_PAYMENT_PROVIDER,
                        PAYMENT_RESOURCE,
                        payment.id().toString(),
                        "Payment provider code is missing or unsupported: provider=" + providerLabel(providerCode)
                ));
                continue;
            }

            if (payment.status() == PaymentStatus.PROVIDER_SUCCEEDED_LEDGER_PENDING) {
                accumulator.add(issue(
                        ReconciliationIssueCode.PROVIDER_SUCCESS_LOCAL_COMPLETION_PENDING,
                        PAYMENT_RESOURCE,
                        payment.id().toString(),
                        "Provider payment succeeded but local ledger/audit completion is still pending after recovery"
                ));
                continue;
            }

            if (requiresCaptureLedger(payment.status()) && isBlank(payment.providerPaymentId())) {
                accumulator.add(issue(
                        ReconciliationIssueCode.MISSING_PROVIDER_REFERENCE,
                        PAYMENT_RESOURCE,
                        payment.id().toString(),
                        "Successful payment is missing provider payment reference: provider=" + providerCode
                ));
                continue;
            }

            if (STRIPE_PROVIDER.equals(providerCode) && !isBlank(payment.providerPaymentId())) {
                compareStripePaymentState(payment, providerCode, accumulator);
            }
        }
    }

    private void compareStripePaymentState(
            PaymentReconciliationItem payment,
            String providerCode,
            ReconciliationRunAccumulator accumulator
    ) {
        Optional<StripePaymentIntentSnapshot> current = fetchStripePaymentIntent(payment.providerPaymentId());
        if (current.isEmpty()) {
            if (requiresCaptureLedger(payment.status())) {
                accumulator.add(issue(
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
            accumulator.add(issue(
                    ReconciliationIssueCode.PROVIDER_PAYMENT_STATE_MISMATCH,
                    PAYMENT_RESOURCE,
                    payment.id().toString(),
                    "Provider payment succeeded but local payment is not succeeded: provider=" + providerCode
                            + " providerPaymentId=" + payment.providerPaymentId()
            ));
        }
        if (requiresCaptureLedger(payment.status()) && !currentState.isSucceeded()) {
            accumulator.add(issue(
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
            ReconciliationRunAccumulator accumulator
    ) {
        for (RefundReconciliationItem refund : refunds) {
            String providerCode = providerCode(refund.providerCode());
            if (!isSupportedProvider(providerCode)) {
                accumulator.add(issue(
                        ReconciliationIssueCode.UNSUPPORTED_PAYMENT_PROVIDER,
                        REFUND_RESOURCE,
                        refund.id().toString(),
                        "Refund provider code is missing or unsupported: provider=" + providerLabel(providerCode)
                ));
                continue;
            }

            if (refund.status() == RefundStatus.PROVIDER_SUCCEEDED_LEDGER_PENDING) {
                accumulator.add(issue(
                        ReconciliationIssueCode.PROVIDER_SUCCESS_LOCAL_COMPLETION_PENDING,
                        REFUND_RESOURCE,
                        refund.id().toString(),
                        "Provider refund succeeded but local ledger/audit completion is still pending after recovery"
                ));
                continue;
            }

            if (refund.status() == RefundStatus.SUCCEEDED && isBlank(refund.providerRefundId())) {
                accumulator.add(issue(
                        ReconciliationIssueCode.MISSING_PROVIDER_REFERENCE,
                        REFUND_RESOURCE,
                        refund.id().toString(),
                        "Succeeded refund is missing provider refund reference: provider=" + providerCode
                ));
                continue;
            }

            if (STRIPE_PROVIDER.equals(providerCode) && !isBlank(refund.providerRefundId())) {
                compareStripeRefundState(refund, providerCode, accumulator);
            }
        }
    }

    private void compareStripeRefundState(
            RefundReconciliationItem refund,
            String providerCode,
            ReconciliationRunAccumulator accumulator
    ) {
        Optional<StripeRefundSnapshot> current = fetchStripeRefund(refund.providerRefundId());
        if (current.isEmpty()) {
            if (refund.status() == RefundStatus.SUCCEEDED) {
                accumulator.add(issue(
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
            accumulator.add(issue(
                    ReconciliationIssueCode.PROVIDER_REFUND_STATE_MISMATCH,
                    REFUND_RESOURCE,
                    refund.id().toString(),
                    "Provider refund succeeded but local refund is not succeeded: provider=" + providerCode
                            + " providerRefundId=" + refund.providerRefundId()
            ));
        }
        if (refund.status() == RefundStatus.SUCCEEDED && !currentState.isSucceeded()) {
            accumulator.add(issue(
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
            ReconciliationRunAccumulator accumulator
    ) {
        Set<ProviderReconciliationReference> paymentReferences = webhookEvents.stream()
                .filter(this::isReconciliationRelevant)
                .filter(event -> isPaymentEvent(event.eventType()))
                .filter(event -> !isBlank(event.providerObjectId()))
                .map(event -> new ProviderReconciliationReference(
                        providerCode(event.providerCode()),
                        event.providerObjectId()
                ))
                .collect(Collectors.toSet());
        Set<ProviderReconciliationReference> refundReferences = webhookEvents.stream()
                .filter(this::isReconciliationRelevant)
                .filter(event -> isRefundEvent(event.eventType()))
                .filter(event -> !isBlank(event.providerObjectId()))
                .map(event -> new ProviderReconciliationReference(
                        providerCode(event.providerCode()),
                        event.providerObjectId()
                ))
                .collect(Collectors.toSet());

        Set<ProviderReconciliationReference> existingPaymentReferences = reconciliationReadPort
                .findPaymentProviderReferences(paymentReferences);
        Set<ProviderReconciliationReference> existingRefundReferences = reconciliationReadPort
                .findRefundProviderReferences(refundReferences);

        for (ProviderWebhookReconciliationItem event : webhookEvents) {
            if (!isReconciliationRelevant(event) || isBlank(event.providerObjectId())) {
                continue;
            }

            ProviderReconciliationReference reference = new ProviderReconciliationReference(
                    providerCode(event.providerCode()),
                    event.providerObjectId()
            );
            if (isPaymentEvent(event.eventType()) && !existingPaymentReferences.contains(reference)) {
                accumulator.add(orphanedProviderWebhookIssue(event, "payment"));
            }
            if (isRefundEvent(event.eventType()) && !existingRefundReferences.contains(reference)) {
                accumulator.add(orphanedProviderWebhookIssue(event, "refund"));
            }
        }
    }

    private void checkLedgerBalances(
            List<LedgerTransactionReconciliationItem> transactions,
            Map<UUID, List<LedgerEntryReconciliationItem>> entriesByTransaction,
            ReconciliationRunAccumulator accumulator
    ) {
        for (LedgerTransactionReconciliationItem transaction : transactions) {
            List<LedgerEntryReconciliationItem> entries = entriesByTransaction.getOrDefault(transaction.id(), List.of());
            if (entries.size() < 2) {
                accumulator.add(issue(
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
                    accumulator.add(issue(
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
            ReconciliationRunAccumulator accumulator
    ) {
        List<PaymentReconciliationItem> ledgerRequiredPayments = payments.stream()
                .filter(payment -> requiresCaptureLedger(payment.status()))
                .toList();
        Map<LedgerTransactionLookupKey, LedgerTransactionReconciliationItem> transactionsByReference =
                ledgerTransactionsByReference(ledgerRequiredPayments.stream()
                        .map(payment -> paymentReference(payment.id()))
                        .toList());
        Map<UUID, List<LedgerEntryReconciliationItem>> entriesByTransaction = entriesByTransaction(
                transactionsByReference.values()
        );

        for (PaymentReconciliationItem payment : ledgerRequiredPayments) {
            LedgerTransactionReconciliationItem transaction = transactionsByReference.get(paymentReference(payment.id()));
            if (transaction == null) {
                accumulator.add(issue(
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
                accumulator.add(issue(
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
            ReconciliationRunAccumulator accumulator
    ) {
        List<RefundReconciliationItem> ledgerRequiredRefunds = refunds.stream()
                .filter(refund -> refund.status() == RefundStatus.SUCCEEDED)
                .toList();
        Set<UUID> paymentIds = reconciliationReadPort.findPaymentIdsByIds(ledgerRequiredRefunds.stream()
                .map(RefundReconciliationItem::paymentId)
                .toList());
        Map<LedgerTransactionLookupKey, LedgerTransactionReconciliationItem> transactionsByReference =
                ledgerTransactionsByReference(ledgerRequiredRefunds.stream()
                        .map(refund -> refundReference(refund.id()))
                        .toList());
        Map<UUID, List<LedgerEntryReconciliationItem>> entriesByTransaction = entriesByTransaction(
                transactionsByReference.values()
        );

        for (RefundReconciliationItem refund : ledgerRequiredRefunds) {
            if (!paymentIds.contains(refund.paymentId())) {
                accumulator.add(issue(
                        ReconciliationIssueCode.ORPHANED_REFUND,
                        REFUND_RESOURCE,
                        refund.id().toString(),
                        "Succeeded refund references a missing payment"
                ));
            }

            LedgerTransactionReconciliationItem transaction = transactionsByReference.get(refundReference(refund.id()));
            if (transaction == null) {
                accumulator.add(issue(
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
                accumulator.add(issue(
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
            ReconciliationRunAccumulator accumulator
    ) {
        Map<UUID, PaymentReconciliationItem> paymentsById = reconciliationReadPort.findPaymentsByIds(transactions.stream()
                        .filter(transaction -> isPaymentCaptureReference(ledgerReference(transaction)))
                        .map(transaction -> ledgerReference(transaction).referenceId())
                        .map(this::parseUuid)
                        .flatMap(Optional::stream)
                        .toList())
                .stream()
                .collect(Collectors.toMap(PaymentReconciliationItem::id, Function.identity()));
        Map<UUID, RefundReconciliationItem> refundsById = reconciliationReadPort.findRefundsByIds(transactions.stream()
                        .filter(transaction -> isRefundReference(ledgerReference(transaction)))
                        .map(transaction -> ledgerReference(transaction).referenceId())
                        .map(this::parseUuid)
                        .flatMap(Optional::stream)
                        .toList())
                .stream()
                .collect(Collectors.toMap(RefundReconciliationItem::id, Function.identity()));

        for (LedgerTransactionReconciliationItem transaction : transactions) {
            LedgerReference reference = ledgerReference(transaction);
            if (isPaymentCaptureReference(reference)) {
                Optional<UUID> paymentId = parseUuid(reference.referenceId());
                boolean matched = paymentId
                        .map(paymentsById::get)
                        .map(payment -> requiresCaptureLedger(payment.status()))
                        .orElse(false);
                if (!matched) {
                    accumulator.add(orphanLedgerIssue(
                            transaction,
                            "Payment capture ledger transaction has no successful payment"
                    ));
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
                    accumulator.add(orphanLedgerIssue(transaction, "Refund ledger transaction has no succeeded refund"));
                }
                continue;
            }

            accumulator.add(orphanLedgerIssue(
                    transaction,
                    "Ledger transaction does not reference a recognized money movement"
            ));
        }
    }

    private Map<UUID, List<LedgerEntryReconciliationItem>> entriesByTransaction(
            Collection<LedgerTransactionReconciliationItem> transactions
    ) {
        List<UUID> transactionIds = transactions.stream()
                .map(LedgerTransactionReconciliationItem::id)
                .toList();
        return reconciliationReadPort.findLedgerEntriesByTransactionIds(transactionIds)
                .stream()
                .collect(Collectors.groupingBy(LedgerEntryReconciliationItem::transactionId));
    }

    private Map<LedgerTransactionLookupKey, LedgerTransactionReconciliationItem> ledgerTransactionsByReference(
            Collection<LedgerTransactionLookupKey> references
    ) {
        return reconciliationReadPort.findLedgerTransactionsByReferences(references)
                .stream()
                .collect(Collectors.toMap(
                        transaction -> new LedgerTransactionLookupKey(
                                transaction.transactionType(),
                                transaction.referenceType(),
                                transaction.referenceId()
                        ),
                        Function.identity(),
                        (first, ignored) -> first
                ));
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

    private LedgerTransactionLookupKey paymentReference(UUID paymentId) {
        return new LedgerTransactionLookupKey(
                LedgerTransactionType.PAYMENT_CAPTURE,
                PAYMENT_REFERENCE_TYPE,
                paymentId.toString()
        );
    }

    private LedgerTransactionLookupKey refundReference(UUID refundId) {
        return new LedgerTransactionLookupKey(
                LedgerTransactionType.REFUND,
                REFUND_REFERENCE_TYPE,
                refundId.toString()
        );
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

    private LedgerReference ledgerReference(LedgerTransactionReconciliationItem transaction) {
        return new LedgerReference(
                transaction.transactionType(),
                transaction.referenceType(),
                transaction.referenceId()
        );
    }

    private ExpectedEntry expected(String accountCode, LedgerEntryDirection direction) {
        return new ExpectedEntry(accountCode, direction);
    }

    private static final class ReconciliationRunAccumulator {

        private final int maxStoredIssues;
        private final List<ReconciliationIssue> storedIssues = new ArrayList<>();
        private long paymentCount;
        private long refundCount;
        private long ledgerTransactionCount;
        private long issueCount;

        private ReconciliationRunAccumulator(int maxStoredIssues) {
            this.maxStoredIssues = Math.max(1, maxStoredIssues);
        }

        private void add(ReconciliationIssue issue) {
            issueCount++;
            if (storedIssues.size() < maxStoredIssues) {
                storedIssues.add(issue);
            }
        }

        private void addPaymentCount(long count) {
            paymentCount += count;
        }

        private void addRefundCount(long count) {
            refundCount += count;
        }

        private void addLedgerTransactionCount(long count) {
            ledgerTransactionCount += count;
        }

        private boolean healthy() {
            return issueCount == 0;
        }

        private long paymentCount() {
            return paymentCount;
        }

        private long refundCount() {
            return refundCount;
        }

        private long ledgerTransactionCount() {
            return ledgerTransactionCount;
        }

        private long issueCount() {
            return issueCount;
        }

        private List<ReconciliationIssue> storedIssues() {
            return List.copyOf(storedIssues);
        }
    }

    private record ExpectedEntry(String accountCode, LedgerEntryDirection direction) {
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
    }
}
