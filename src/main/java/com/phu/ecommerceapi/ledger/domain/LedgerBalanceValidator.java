package com.phu.ecommerceapi.ledger.domain;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class LedgerBalanceValidator {

    private LedgerBalanceValidator() {
    }

    public static void requireBalanced(List<LedgerEntryLine> entries) {
        if (entries == null || entries.size() < 2) {
            throw new IllegalArgumentException("ledger transaction requires at least two entries");
        }

        Map<String, Map<LedgerEntryDirection, BigDecimal>> totalsByCurrency = entries.stream()
                .map(entry -> Objects.requireNonNull(entry, "ledger entry is required"))
                .collect(Collectors.groupingBy(
                        LedgerEntryLine::currency,
                        Collectors.groupingBy(
                                LedgerEntryLine::direction,
                                () -> new EnumMap<>(LedgerEntryDirection.class),
                                Collectors.reducing(BigDecimal.ZERO, LedgerEntryLine::amount, BigDecimal::add)
                        )
                ));

        for (Map.Entry<String, Map<LedgerEntryDirection, BigDecimal>> currencyTotals : totalsByCurrency.entrySet()) {
            BigDecimal debits = currencyTotals.getValue().getOrDefault(LedgerEntryDirection.DEBIT, BigDecimal.ZERO);
            BigDecimal credits = currencyTotals.getValue().getOrDefault(LedgerEntryDirection.CREDIT, BigDecimal.ZERO);
            if (debits.compareTo(credits) != 0) {
                throw new IllegalArgumentException(
                        "ledger transaction is not balanced for " + currencyTotals.getKey()
                );
            }
        }
    }
}
