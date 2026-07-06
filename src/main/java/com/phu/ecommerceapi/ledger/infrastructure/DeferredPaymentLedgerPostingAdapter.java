package com.phu.ecommerceapi.ledger.infrastructure;

import com.phu.ecommerceapi.ledger.application.PaymentLedgerPostingCommand;
import com.phu.ecommerceapi.ledger.application.PaymentLedgerPostingPort;
import org.springframework.stereotype.Component;

@Component
public class DeferredPaymentLedgerPostingAdapter implements PaymentLedgerPostingPort {

    @Override
    public void postPaymentSucceeded(PaymentLedgerPostingCommand command) {
        // TASK-026 replaces this adapter with immutable ledger persistence.
    }
}
