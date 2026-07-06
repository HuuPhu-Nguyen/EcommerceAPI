package com.phu.ecommerceapi.ledger.application;

public interface PaymentLedgerPostingPort {

    void postPaymentSucceeded(PaymentLedgerPostingCommand command);
}
