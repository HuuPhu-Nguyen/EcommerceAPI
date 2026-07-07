package com.phu.ecommerceapi.ledger.application;

import com.phu.ecommerceapi.ledger.domain.LedgerEntryDirection;
import com.phu.ecommerceapi.ledger.domain.LedgerEntryLine;

import java.math.BigDecimal;
import java.util.Locale;

public record LedgerEntryDraft(
        String accountCode,
        LedgerEntryDirection direction,
        BigDecimal amount,
        String currency
) {

    public LedgerEntryDraft {
        if (accountCode == null || accountCode.isBlank()) {
            throw new IllegalArgumentException("ledger account code is required");
        }
        accountCode = accountCode.trim().toUpperCase(Locale.ROOT);
        LedgerEntryLine line = new LedgerEntryLine(direction, amount, currency);
        direction = line.direction();
        amount = line.amount();
        currency = line.currency();
    }

    public LedgerEntryLine toLedgerEntryLine() {
        return new LedgerEntryLine(direction, amount, currency);
    }
}
