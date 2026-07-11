package com.phu.ecommerceapi.ledger.application;

import com.phu.ecommerceapi.ledger.domain.LedgerEntryDirection;
import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerEntryRecord;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerEntryRepository;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerTransactionRecord;
import com.phu.ecommerceapi.ledger.infrastructure.LedgerTransactionRepository;
import com.phu.ecommerceapi.ledger.infrastructure.JpaPaymentLedgerPostingAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class LedgerPostingServiceTest {

    @Autowired
    private LedgerPostingService ledgerPostingService;

    @Autowired
    private LedgerTransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository entryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetLedger() {
        truncateLedger();
    }

    @AfterEach
    void cleanUpLedger() {
        truncateLedger();
    }

    @Test
    void balancedLedgerTransactionIsPersisted() {
        String referenceId = UUID.randomUUID().toString();

        UUID transactionId = postBalancedPayment(referenceId, "42.50");

        LedgerTransactionRecord transaction = transactionRepository.findById(transactionId).orElseThrow();
        List<LedgerEntryRecord> entries = entryRepository.findByTransactionId(transactionId);

        assertThat(transaction.getTransactionType()).isEqualTo(LedgerTransactionType.PAYMENT_CAPTURE);
        assertThat(transaction.getReferenceType()).isEqualTo("PAYMENT");
        assertThat(transaction.getReferenceId()).isEqualTo(referenceId);
        assertThat(entries).hasSize(2);
        assertThat(total(entries, LedgerEntryDirection.DEBIT)).isEqualByComparingTo("42.50");
        assertThat(total(entries, LedgerEntryDirection.CREDIT)).isEqualByComparingTo("42.50");
        assertThat(entries).extracting(LedgerEntryRecord::getCurrency).containsOnly("USD");
    }

    @Test
    void sameBusinessReferenceDoesNotCreateDuplicateLedgerTransaction() {
        String referenceId = UUID.randomUUID().toString();

        UUID firstTransactionId = postBalancedPayment(referenceId, "15.00");
        UUID replayedTransactionId = postBalancedPayment(referenceId, "15.00");

        assertThat(replayedTransactionId).isEqualTo(firstTransactionId);
        assertThat(transactionRepository.findAll()).hasSize(1);
        assertThat(entryRepository.findAll()).hasSize(2);
    }

    @Test
    void concurrentPaymentPostingReturnsSameTransactionWithoutDuplicateEntries() throws Exception {
        assertConcurrentPost(
                LedgerTransactionType.PAYMENT_CAPTURE,
                "PAYMENT",
                "Concurrent payment capture test",
                paymentEntries(new BigDecimal("18.00"))
        );
    }

    @Test
    void concurrentRefundPostingReturnsSameTransactionWithoutDuplicateEntries() throws Exception {
        assertConcurrentPost(
                LedgerTransactionType.REFUND,
                "REFUND",
                "Concurrent refund test",
                refundEntries(new BigDecimal("18.00"))
        );
    }

    @Test
    void unbalancedLedgerTransactionIsRejected() {
        String referenceId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> ledgerPostingService.postTransaction(
                LedgerTransactionType.PAYMENT_CAPTURE,
                "PAYMENT",
                referenceId,
                "Unbalanced payment test",
                List.of(
                        new LedgerEntryDraft(
                                JpaPaymentLedgerPostingAdapter.PROVIDER_CLEARING_ACCOUNT,
                                LedgerEntryDirection.DEBIT,
                                new BigDecimal("10.00"),
                                "USD"
                        ),
                        new LedgerEntryDraft(
                                JpaPaymentLedgerPostingAdapter.ORDER_REVENUE_ACCOUNT,
                                LedgerEntryDirection.CREDIT,
                                new BigDecimal("9.99"),
                                "USD"
                        )
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ledger transaction is not balanced for USD");

        assertThat(transactionRepository.findAll()).isEmpty();
        assertThat(entryRepository.findAll()).isEmpty();
    }

    @Test
    void ledgerEntriesCannotBeUpdatedOrDeleted() {
        UUID transactionId = postBalancedPayment(UUID.randomUUID().toString(), "20.00");
        Long entryId = entryRepository.findByTransactionId(transactionId).getFirst().getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE ledger_entry SET amount = 99.00 WHERE id = ?",
                entryId
        ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ledger records are append-only");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM ledger_entry WHERE id = ?",
                entryId
        ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ledger records are append-only");
    }

    private UUID postBalancedPayment(String referenceId, String amount) {
        BigDecimal paymentAmount = new BigDecimal(amount);
        return ledgerPostingService.postTransaction(
                LedgerTransactionType.PAYMENT_CAPTURE,
                "PAYMENT",
                referenceId,
                "Balanced payment test",
                paymentEntries(paymentAmount)
        );
    }

    private void assertConcurrentPost(
            LedgerTransactionType transactionType,
            String referenceType,
            String description,
            List<LedgerEntryDraft> entries
    ) throws Exception {
        String referenceId = UUID.randomUUID().toString();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<UUID> postAttempt = () -> {
            ready.countDown();
            await(start);
            return ledgerPostingService.postTransaction(
                    transactionType,
                    referenceType,
                    referenceId,
                    description,
                    entries
            );
        };

        try {
            Future<UUID> first = executor.submit(postAttempt);
            Future<UUID> second = executor.submit(postAttempt);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<UUID> transactionIds = List.of(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)
            );
            assertThat(transactionIds).containsOnly(transactionIds.getFirst());
            assertThat(transactionRepository.findAll()).hasSize(1);
            assertThat(entryRepository.findAll()).hasSize(2);
            assertThat(entryRepository.findByTransactionId(transactionIds.getFirst())).hasSize(2);
        } finally {
            executor.shutdownNow();
        }
    }

    private List<LedgerEntryDraft> paymentEntries(BigDecimal amount) {
        return List.of(
                new LedgerEntryDraft(
                        JpaPaymentLedgerPostingAdapter.PROVIDER_CLEARING_ACCOUNT,
                        LedgerEntryDirection.DEBIT,
                        amount,
                        "USD"
                ),
                new LedgerEntryDraft(
                        JpaPaymentLedgerPostingAdapter.ORDER_REVENUE_ACCOUNT,
                        LedgerEntryDirection.CREDIT,
                        amount,
                        "USD"
                )
        );
    }

    private List<LedgerEntryDraft> refundEntries(BigDecimal amount) {
        return List.of(
                new LedgerEntryDraft(
                        JpaPaymentLedgerPostingAdapter.ORDER_REVENUE_ACCOUNT,
                        LedgerEntryDirection.DEBIT,
                        amount,
                        "USD"
                ),
                new LedgerEntryDraft(
                        JpaPaymentLedgerPostingAdapter.PROVIDER_CLEARING_ACCOUNT,
                        LedgerEntryDirection.CREDIT,
                        amount,
                        "USD"
                )
        );
    }

    private BigDecimal total(List<LedgerEntryRecord> entries, LedgerEntryDirection direction) {
        return entries.stream()
                .filter(entry -> entry.getDirection() == direction)
                .map(LedgerEntryRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void truncateLedger() {
        jdbcTemplate.execute("TRUNCATE TABLE ledger_entry, ledger_transaction RESTART IDENTITY");
    }

    private void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent ledger posting test did not start");
        }
    }
}
