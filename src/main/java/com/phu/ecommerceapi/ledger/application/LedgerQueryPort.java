package com.phu.ecommerceapi.ledger.application;

import java.util.List;

public interface LedgerQueryPort {

    List<LedgerTransactionView> recentTransactions(int limit);
}
