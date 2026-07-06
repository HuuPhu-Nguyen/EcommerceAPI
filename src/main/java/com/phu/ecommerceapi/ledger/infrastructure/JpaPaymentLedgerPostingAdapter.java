package com.phu.ecommerceapi.ledger.infrastructure;

import com.phu.ecommerceapi.ledger.application.LedgerEntryDraft;
import com.phu.ecommerceapi.ledger.application.LedgerPostingService;
import com.phu.ecommerceapi.ledger.application.PaymentLedgerPostingCommand;
import com.phu.ecommerceapi.ledger.application.PaymentLedgerPostingPort;
import com.phu.ecommerceapi.ledger.application.RefundLedgerPostingCommand;
import com.phu.ecommerceapi.ledger.domain.LedgerEntryDirection;
import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JpaPaymentLedgerPostingAdapter implements PaymentLedgerPostingPort {

    public static final String PROVIDER_CLEARING_ACCOUNT = "PAYMENT_PROVIDER_CLEARING";
    public static final String ORDER_REVENUE_ACCOUNT = "ORDER_REVENUE";

    private final LedgerPostingService ledgerPostingService;

    public JpaPaymentLedgerPostingAdapter(LedgerPostingService ledgerPostingService) {
        this.ledgerPostingService = ledgerPostingService;
    }

    @Override
    public void postPaymentSucceeded(PaymentLedgerPostingCommand command) {
        ledgerPostingService.postTransaction(
                LedgerTransactionType.PAYMENT_CAPTURE,
                "PAYMENT",
                command.paymentId().toString(),
                "Payment captured for order " + command.orderId(),
                List.of(
                        new LedgerEntryDraft(
                                PROVIDER_CLEARING_ACCOUNT,
                                LedgerEntryDirection.DEBIT,
                                command.amount(),
                                command.currency()
                        ),
                        new LedgerEntryDraft(
                                ORDER_REVENUE_ACCOUNT,
                                LedgerEntryDirection.CREDIT,
                                command.amount(),
                                command.currency()
                        )
                )
        );
    }

    @Override
    public void postRefundSucceeded(RefundLedgerPostingCommand command) {
        ledgerPostingService.postTransaction(
                LedgerTransactionType.REFUND,
                "REFUND",
                command.refundId().toString(),
                "Refund issued for payment " + command.paymentId(),
                List.of(
                        new LedgerEntryDraft(
                                ORDER_REVENUE_ACCOUNT,
                                LedgerEntryDirection.DEBIT,
                                command.amount(),
                                command.currency()
                        ),
                        new LedgerEntryDraft(
                                PROVIDER_CLEARING_ACCOUNT,
                                LedgerEntryDirection.CREDIT,
                                command.amount(),
                                command.currency()
                        )
                )
        );
    }
}
