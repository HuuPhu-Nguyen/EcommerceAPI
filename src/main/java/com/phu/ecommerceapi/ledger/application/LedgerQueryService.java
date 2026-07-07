package com.phu.ecommerceapi.ledger.application;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LedgerQueryService {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final LedgerQueryPort ledgerQueryPort;

    public LedgerQueryService(LedgerQueryPort ledgerQueryPort) {
        this.ledgerQueryPort = ledgerQueryPort;
    }

    public List<LedgerTransactionView> recentTransactions(int limit) {
        int safeLimit = Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT));
        return ledgerQueryPort.recentTransactions(safeLimit);
    }
}
