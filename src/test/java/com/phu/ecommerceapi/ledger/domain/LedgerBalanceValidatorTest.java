package com.phu.ecommerceapi.ledger.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerBalanceValidatorTest {

    @Test
    void balancedEntriesAreValidPerCurrency() {
        LedgerBalanceValidator.requireBalanced(List.of(
                entry(LedgerEntryDirection.DEBIT, "10.00", "usd"),
                entry(LedgerEntryDirection.CREDIT, "10.00", "USD"),
                entry(LedgerEntryDirection.DEBIT, "7.50", "EUR"),
                entry(LedgerEntryDirection.CREDIT, "7.50", "eur")
        ));
    }

    @Test
    void unbalancedEntriesAreRejectedPerCurrency() {
        assertThatThrownBy(() -> LedgerBalanceValidator.requireBalanced(List.of(
                entry(LedgerEntryDirection.DEBIT, "10.00", "USD"),
                entry(LedgerEntryDirection.CREDIT, "9.99", "USD")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ledger transaction is not balanced for USD");
    }

    @Test
    void transactionRequiresAtLeastTwoEntries() {
        assertThatThrownBy(() -> LedgerBalanceValidator.requireBalanced(List.of(
                entry(LedgerEntryDirection.DEBIT, "10.00", "USD")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ledger transaction requires at least two entries");
    }

    @Test
    void ledgerEntryLineNormalizesCurrencyAndScale() {
        LedgerEntryLine line = entry(LedgerEntryDirection.DEBIT, "10.0", "usd");

        assertThat(line.amount()).isEqualByComparingTo("10.00");
        assertThat(line.currency()).isEqualTo("USD");
    }

    private LedgerEntryLine entry(LedgerEntryDirection direction, String amount, String currency) {
        return new LedgerEntryLine(direction, new BigDecimal(amount), currency);
    }
}
