package com.phu.ecommerceapi.payment.application;

import java.util.UUID;

public record RefundablePayment(
        UUID paymentId,
        String providerCode
) {
}
