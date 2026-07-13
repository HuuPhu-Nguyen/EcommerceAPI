package com.phu.ecommerceapi.reconciliation.application;

import com.phu.ecommerceapi.ledger.domain.LedgerEntryDirection;
import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;
import com.phu.ecommerceapi.payment.application.PaymentIdempotencyRecoveryService;
import com.phu.ecommerceapi.payment.application.StripeProviderReadPort;
import com.phu.ecommerceapi.payment.domain.PaymentStatus;
import com.phu.ecommerceapi.payment.domain.ProviderWebhookEventType;
import com.phu.ecommerceapi.payment.domain.ProviderWebhookProcessingStatus;
import com.phu.ecommerceapi.payment.domain.RefundStatus;
import com.phu.ecommerceapi.shared.api.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReconciliationServiceTest {

    private static final String PROVIDER_CLEARING_ACCOUNT = "PAYMENT_PROVIDER_CLEARING";
    private static final String ORDER_REVENUE_ACCOUNT = "ORDER_REVENUE";

    private final PaymentIdempotencyRecoveryService recoveryService = mock(PaymentIdempotencyRecoveryService.class);
    private final ObjectProvider<StripeProviderReadPort> stripeReadPortProvider = new EmptyStripeProvider();

    @Test
    void runReportDoesNotOpenBroadTransactionAroundProviderReads() throws NoSuchMethodException {
        assertThat(ReconciliationService.class.getMethod("runReport").isAnnotationPresent(Transactional.class))
                .isFalse();
    }

    @Test
    void readPortNoLongerExposesFullTableReconciliationMethods() {
        assertThat(Stream.of(ReconciliationReadPort.class.getMethods())
                .map(Method::getName)
                .noneMatch(name -> name.equals("findAllForReconciliation") || name.startsWith("findAll")))
                .isTrue();
    }

    @Test
    void runReportReturnsConflictWhenAnotherRunHoldsTheLock() {
        UUID activeRunId = uuid(99);
        InMemoryRunStore runStore = new InMemoryRunStore();
        runStore.activeRunId = activeRunId;
        InMemoryRunLockPort runLock = new InMemoryRunLockPort(false);
        ReconciliationService service = service(FakeReadPort.empty(), runStore, runLock, 10, 10);

        assertThatThrownBy(service::runReport)
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(activeRunId.toString());

        assertThat(runStore.runId).isNull();
        assertThat(runLock.acquireAttempts).isEqualTo(1);
        assertThat(runLock.closeCount).isZero();
    }

    @Test
    void scheduledRunSkipsWhenAnotherRunHoldsTheLock() {
        InMemoryRunStore runStore = new InMemoryRunStore();
        InMemoryRunLockPort runLock = new InMemoryRunLockPort(false);
        ReconciliationService service = service(FakeReadPort.empty(), runStore, runLock, 10, 10);

        Optional<ReconciliationReport> report = service.runScheduledReport();

        assertThat(report).isEmpty();
        assertThat(runStore.runId).isNull();
        assertThat(runLock.acquireAttempts).isEqualTo(1);
        assertThat(runLock.closeCount).isZero();
    }

    @Test
    void failedRunIsMarkedFailedAndReleasesTheLock() {
        FakeReadPort readPort = FakeReadPort.empty();
        readPort.failOnPaymentPage = true;
        InMemoryRunStore runStore = new InMemoryRunStore();
        InMemoryRunLockPort runLock = new InMemoryRunLockPort();
        ReconciliationService service = service(readPort, runStore, runLock, 10, 10);

        assertThatThrownBy(service::runReport)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(runStore.failedRunId).isEqualTo(runStore.runId);
        assertThat(runStore.failureMessage).isEqualTo("boom");
        assertThat(runStore.activeRunId).isNull();
        assertThat(runLock.closeCount).isEqualTo(1);
    }

    @Test
    void runProcessesEveryPageAndStoresHealthyMaterializedReport() {
        UUID firstPaymentId = uuid(1);
        UUID secondPaymentId = uuid(2);
        UUID firstRefundId = uuid(3);
        UUID secondRefundId = uuid(4);
        UUID firstCaptureTransactionId = uuid(11);
        UUID secondCaptureTransactionId = uuid(12);
        UUID firstRefundTransactionId = uuid(13);
        UUID secondRefundTransactionId = uuid(14);
        FakeReadPort readPort = new FakeReadPort(
                List.of(
                        payment(firstPaymentId, PaymentStatus.REFUNDED, "fake_payment_1"),
                        payment(secondPaymentId, PaymentStatus.REFUNDED, "fake_payment_2")
                ),
                List.of(
                        refund(firstRefundId, firstPaymentId, "fake_refund_1"),
                        refund(secondRefundId, secondPaymentId, "fake_refund_2")
                ),
                List.of(
                        webhook(uuid(21), ProviderWebhookEventType.PAYMENT_SUCCEEDED, "fake_payment_1"),
                        webhook(uuid(22), ProviderWebhookEventType.REFUND_SUCCEEDED, "fake_refund_2")
                ),
                List.of(
                        transaction(firstCaptureTransactionId, LedgerTransactionType.PAYMENT_CAPTURE, "PAYMENT", firstPaymentId),
                        transaction(secondCaptureTransactionId, LedgerTransactionType.PAYMENT_CAPTURE, "PAYMENT", secondPaymentId),
                        transaction(firstRefundTransactionId, LedgerTransactionType.REFUND, "REFUND", firstRefundId),
                        transaction(secondRefundTransactionId, LedgerTransactionType.REFUND, "REFUND", secondRefundId)
                ),
                List.of(
                        debit(firstCaptureTransactionId, PROVIDER_CLEARING_ACCOUNT),
                        credit(firstCaptureTransactionId, ORDER_REVENUE_ACCOUNT),
                        debit(secondCaptureTransactionId, PROVIDER_CLEARING_ACCOUNT),
                        credit(secondCaptureTransactionId, ORDER_REVENUE_ACCOUNT),
                        debit(firstRefundTransactionId, ORDER_REVENUE_ACCOUNT),
                        credit(firstRefundTransactionId, PROVIDER_CLEARING_ACCOUNT),
                        debit(secondRefundTransactionId, ORDER_REVENUE_ACCOUNT),
                        credit(secondRefundTransactionId, PROVIDER_CLEARING_ACCOUNT)
                )
        );
        InMemoryRunStore runStore = new InMemoryRunStore();
        ReconciliationService service = service(readPort, runStore, 1, 10);

        ReconciliationReport report = service.runReport();

        assertThat(report.healthy()).isTrue();
        assertThat(report.checkedPayments()).isEqualTo(2);
        assertThat(report.checkedRefunds()).isEqualTo(2);
        assertThat(report.checkedLedgerTransactions()).isEqualTo(4);
        assertThat(report.issueCount()).isZero();
        assertThat(report.issues()).isEmpty();
        assertThat(readPort.paymentPageCalls).isEqualTo(3);
        assertThat(readPort.refundPageCalls).isEqualTo(3);
        assertThat(readPort.webhookPageCalls).isEqualTo(3);
        assertThat(readPort.ledgerTransactionPageCalls).isEqualTo(5);
        assertThat(readPort.ledgerEntryBatchCalls).isGreaterThanOrEqualTo(3);
        verify(recoveryService).recoverExpired();
    }

    @Test
    void issueStorageStopsAtLimitButIssueCountReflectsAllDiscoveredIssues() {
        FakeReadPort readPort = new FakeReadPort(
                List.of(
                        payment(uuid(1), PaymentStatus.SUCCEEDED, "fake_payment_1"),
                        payment(uuid(2), PaymentStatus.SUCCEEDED, "fake_payment_2"),
                        payment(uuid(3), PaymentStatus.SUCCEEDED, "fake_payment_3"),
                        payment(uuid(4), PaymentStatus.SUCCEEDED, "fake_payment_4"),
                        payment(uuid(5), PaymentStatus.SUCCEEDED, "fake_payment_5")
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        InMemoryRunStore runStore = new InMemoryRunStore();
        ReconciliationService service = service(readPort, runStore, 2, 2);

        ReconciliationReport report = service.runReport();

        assertThat(report.healthy()).isFalse();
        assertThat(report.issueCount()).isEqualTo(5);
        assertThat(report.issues()).hasSize(2);
        assertThat(report.issuesTruncated()).isTrue();
        assertThat(runStore.storedIssues).hasSize(2);
    }

    @Test
    void staleProviderSuccessPendingLocalCompletionIsReportedAfterRecoveryRun() {
        UUID paymentId = uuid(1);
        UUID refundId = uuid(2);
        FakeReadPort readPort = new FakeReadPort(
                List.of(payment(
                        paymentId,
                        PaymentStatus.PROVIDER_SUCCEEDED_LEDGER_PENDING,
                        "fake_payment_pending"
                )),
                List.of(refund(
                        refundId,
                        paymentId,
                        RefundStatus.PROVIDER_SUCCEEDED_LEDGER_PENDING,
                        "fake_refund_pending"
                )),
                List.of(),
                List.of(),
                List.of()
        );
        ReconciliationService service = service(readPort, new InMemoryRunStore(), 10, 10);

        ReconciliationReport report = service.runReport();

        assertThat(report.healthy()).isFalse();
        assertThat(report.issues())
                .extracting(ReconciliationIssue::code)
                .containsExactly(
                        ReconciliationIssueCode.PROVIDER_SUCCESS_LOCAL_COMPLETION_PENDING,
                        ReconciliationIssueCode.PROVIDER_SUCCESS_LOCAL_COMPLETION_PENDING
                );
    }

    private ReconciliationService service(
            ReconciliationReadPort readPort,
            ReconciliationRunStorePort runStore,
            int batchSize,
            int maxIssues
    ) {
        return service(readPort, runStore, new InMemoryRunLockPort(), batchSize, maxIssues);
    }

    private ReconciliationService service(
            ReconciliationReadPort readPort,
            ReconciliationRunStorePort runStore,
            ReconciliationRunLockPort runLock,
            int batchSize,
            int maxIssues
    ) {
        return new ReconciliationService(
                readPort,
                runStore,
                runLock,
                new ReconciliationProperties(batchSize, maxIssues),
                stripeReadPortProvider,
                recoveryService
        );
    }

    private PaymentReconciliationItem payment(
            UUID id,
            PaymentStatus status,
            String providerPaymentId
    ) {
        return new PaymentReconciliationItem(
                id,
                new BigDecimal("25.00"),
                "USD",
                status,
                "fake",
                providerPaymentId
        );
    }

    private RefundReconciliationItem refund(UUID id, UUID paymentId, String providerRefundId) {
        return refund(id, paymentId, RefundStatus.SUCCEEDED, providerRefundId);
    }

    private RefundReconciliationItem refund(
            UUID id,
            UUID paymentId,
            RefundStatus status,
            String providerRefundId
    ) {
        return new RefundReconciliationItem(
                id,
                paymentId,
                new BigDecimal("25.00"),
                "USD",
                status,
                "fake",
                providerRefundId
        );
    }

    private ProviderWebhookReconciliationItem webhook(
            UUID id,
            ProviderWebhookEventType eventType,
            String providerObjectId
    ) {
        return new ProviderWebhookReconciliationItem(
                id,
                "fake",
                eventType,
                ProviderWebhookProcessingStatus.PROCESSED,
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

    private LedgerEntryReconciliationItem debit(UUID transactionId, String accountCode) {
        return entry(transactionId, LedgerEntryDirection.DEBIT, accountCode);
    }

    private LedgerEntryReconciliationItem credit(UUID transactionId, String accountCode) {
        return entry(transactionId, LedgerEntryDirection.CREDIT, accountCode);
    }

    private LedgerEntryReconciliationItem entry(
            UUID transactionId,
            LedgerEntryDirection direction,
            String accountCode
    ) {
        return new LedgerEntryReconciliationItem(
                transactionId,
                direction,
                new BigDecimal("25.00"),
                "USD",
                accountCode
        );
    }

    private UUID uuid(int value) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(value));
    }

    private static final class InMemoryRunStore implements ReconciliationRunStorePort {

        private UUID runId;
        private UUID activeRunId;
        private UUID failedRunId;
        private String failureMessage;
        private ReconciliationRunCompletion completion;
        private List<ReconciliationIssue> storedIssues = List.of();

        @Override
        public UUID startRun(Instant startedAt) {
            runId = UUID.randomUUID();
            activeRunId = runId;
            return runId;
        }

        @Override
        public void completeRun(
                UUID runId,
                ReconciliationRunCompletion completion,
                List<ReconciliationIssue> storedIssues
        ) {
            this.completion = completion;
            this.storedIssues = List.copyOf(storedIssues);
            activeRunId = null;
        }

        @Override
        public void failRun(UUID runId, Instant completedAt, String failureMessage) {
            this.failedRunId = runId;
            this.failureMessage = failureMessage;
            activeRunId = null;
        }

        @Override
        public Optional<UUID> findActiveRunId() {
            return Optional.ofNullable(activeRunId);
        }

        @Override
        public Optional<ReconciliationReport> findCompleted(UUID runId, int issueLimit) {
            if (!runId.equals(this.runId) || completion == null) {
                return Optional.empty();
            }
            List<ReconciliationIssue> issues = storedIssues.stream()
                    .limit(issueLimit)
                    .toList();
            return Optional.of(new ReconciliationReport(
                    completion.healthy(),
                    completion.completedAt(),
                    completion.paymentCount(),
                    completion.refundCount(),
                    completion.ledgerTransactionCount(),
                    completion.issueCount(),
                    completion.issueCount() > issues.size(),
                    issues
            ));
        }

        @Override
        public Optional<ReconciliationReport> findLatestCompleted(int issueLimit) {
            return findCompleted(runId, issueLimit);
        }
    }

    private static final class InMemoryRunLockPort implements ReconciliationRunLockPort {

        private final boolean available;
        private int acquireAttempts;
        private int closeCount;

        private InMemoryRunLockPort() {
            this(true);
        }

        private InMemoryRunLockPort(boolean available) {
            this.available = available;
        }

        @Override
        public Optional<ReconciliationRunLock> tryAcquire() {
            acquireAttempts++;
            if (!available) {
                return Optional.empty();
            }
            return Optional.of(() -> closeCount++);
        }
    }

    private static final class FakeReadPort implements ReconciliationReadPort {

        private final List<PaymentReconciliationItem> payments;
        private final List<RefundReconciliationItem> refunds;
        private final List<ProviderWebhookReconciliationItem> webhookEvents;
        private final List<LedgerTransactionReconciliationItem> transactions;
        private final List<LedgerEntryReconciliationItem> entries;
        private int paymentPageCalls;
        private int refundPageCalls;
        private int webhookPageCalls;
        private int ledgerTransactionPageCalls;
        private int ledgerEntryBatchCalls;
        private boolean failOnPaymentPage;

        private static FakeReadPort empty() {
            return new FakeReadPort(List.of(), List.of(), List.of(), List.of(), List.of());
        }

        private FakeReadPort(
                List<PaymentReconciliationItem> payments,
                List<RefundReconciliationItem> refunds,
                List<ProviderWebhookReconciliationItem> webhookEvents,
                List<LedgerTransactionReconciliationItem> transactions,
                List<LedgerEntryReconciliationItem> entries
        ) {
            this.payments = sortById(payments, PaymentReconciliationItem::id);
            this.refunds = sortById(refunds, RefundReconciliationItem::id);
            this.webhookEvents = sortById(webhookEvents, ProviderWebhookReconciliationItem::id);
            this.transactions = sortById(transactions, LedgerTransactionReconciliationItem::id);
            this.entries = List.copyOf(entries);
        }

        @Override
        public List<PaymentReconciliationItem> findPaymentsAfterId(UUID afterIdExclusive, int limit) {
            if (failOnPaymentPage) {
                throw new IllegalStateException("boom");
            }
            paymentPageCalls++;
            return page(payments, afterIdExclusive, limit, PaymentReconciliationItem::id);
        }

        @Override
        public List<PaymentReconciliationItem> findPaymentsByIds(Collection<UUID> paymentIds) {
            return payments.stream()
                    .filter(payment -> paymentIds.contains(payment.id()))
                    .toList();
        }

        @Override
        public Set<UUID> findPaymentIdsByIds(Collection<UUID> paymentIds) {
            return payments.stream()
                    .map(PaymentReconciliationItem::id)
                    .filter(paymentIds::contains)
                    .collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public Set<ProviderReconciliationReference> findPaymentProviderReferences(
                Collection<ProviderReconciliationReference> providerReferences
        ) {
            return payments.stream()
                    .filter(payment -> payment.providerPaymentId() != null)
                    .map(payment -> new ProviderReconciliationReference(
                            payment.providerCode(),
                            payment.providerPaymentId()
                    ))
                    .filter(providerReferences::contains)
                    .collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public List<RefundReconciliationItem> findRefundsAfterId(UUID afterIdExclusive, int limit) {
            refundPageCalls++;
            return page(refunds, afterIdExclusive, limit, RefundReconciliationItem::id);
        }

        @Override
        public List<RefundReconciliationItem> findRefundsByIds(Collection<UUID> refundIds) {
            return refunds.stream()
                    .filter(refund -> refundIds.contains(refund.id()))
                    .toList();
        }

        @Override
        public Set<UUID> findRefundIdsByIds(Collection<UUID> refundIds) {
            return refunds.stream()
                    .map(RefundReconciliationItem::id)
                    .filter(refundIds::contains)
                    .collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public Set<ProviderReconciliationReference> findRefundProviderReferences(
                Collection<ProviderReconciliationReference> providerReferences
        ) {
            return refunds.stream()
                    .filter(refund -> refund.providerRefundId() != null)
                    .map(refund -> new ProviderReconciliationReference(
                            refund.providerCode(),
                            refund.providerRefundId()
                    ))
                    .filter(providerReferences::contains)
                    .collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public List<ProviderWebhookReconciliationItem> findProviderWebhookEventsAfterId(
                UUID afterIdExclusive,
                int limit
        ) {
            webhookPageCalls++;
            return page(webhookEvents, afterIdExclusive, limit, ProviderWebhookReconciliationItem::id);
        }

        @Override
        public List<LedgerTransactionReconciliationItem> findLedgerTransactionsAfterId(
                UUID afterIdExclusive,
                int limit
        ) {
            ledgerTransactionPageCalls++;
            return page(transactions, afterIdExclusive, limit, LedgerTransactionReconciliationItem::id);
        }

        @Override
        public List<LedgerTransactionReconciliationItem> findLedgerTransactionsByReferences(
                Collection<LedgerTransactionLookupKey> references
        ) {
            return transactions.stream()
                    .filter(transaction -> references.contains(new LedgerTransactionLookupKey(
                            transaction.transactionType(),
                            transaction.referenceType(),
                            transaction.referenceId()
                    )))
                    .toList();
        }

        @Override
        public List<LedgerEntryReconciliationItem> findLedgerEntriesByTransactionIds(Collection<UUID> transactionIds) {
            ledgerEntryBatchCalls++;
            return entries.stream()
                    .filter(entry -> transactionIds.contains(entry.transactionId()))
                    .toList();
        }

        private static <T> List<T> sortById(List<T> items, java.util.function.Function<T, UUID> idExtractor) {
            return items.stream()
                    .sorted(Comparator.comparing(idExtractor))
                    .toList();
        }

        private static <T> List<T> page(
                List<T> items,
                UUID afterIdExclusive,
                int limit,
                java.util.function.Function<T, UUID> idExtractor
        ) {
            return items.stream()
                    .filter(item -> afterIdExclusive == null || idExtractor.apply(item).compareTo(afterIdExclusive) > 0)
                    .limit(limit)
                    .toList();
        }
    }

    private static final class EmptyStripeProvider implements ObjectProvider<StripeProviderReadPort> {

        @Override
        public StripeProviderReadPort getObject(Object... args) {
            return null;
        }

        @Override
        public StripeProviderReadPort getIfAvailable() {
            return null;
        }

        @Override
        public StripeProviderReadPort getIfUnique() {
            return null;
        }

        @Override
        public StripeProviderReadPort getObject() {
            return null;
        }
    }
}
