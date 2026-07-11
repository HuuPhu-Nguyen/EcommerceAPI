package com.phu.ecommerceapi.payment.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStateMachineTest {

    @Test
    void pendingPaymentCanCompleteFromProviderOutcome() {
        assertThat(PaymentStateMachine.providerSucceeded(PaymentStatus.PENDING))
                .isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(PaymentStateMachine.providerFailed(PaymentStatus.PENDING))
                .isEqualTo(PaymentStatus.FAILED);
        assertThat(PaymentStateMachine.providerTimedOut(PaymentStatus.PENDING))
                .isEqualTo(PaymentStatus.PROVIDER_TIMEOUT);
    }

    @Test
    void failedOrUnknownPaymentCanRecoverFromCurrentProviderSuccess() {
        assertThat(PaymentStateMachine.providerSucceeded(PaymentStatus.FAILED))
                .isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(PaymentStateMachine.providerSucceeded(PaymentStatus.PROVIDER_TIMEOUT))
                .isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(PaymentStateMachine.providerFailed(PaymentStatus.PROVIDER_TIMEOUT))
                .isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void paidOrRefundedPaymentIgnoresDuplicateProviderOutcome() {
        assertThat(PaymentStateMachine.providerFailed(PaymentStatus.SUCCEEDED))
                .isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(PaymentStateMachine.providerTimedOut(PaymentStatus.REFUNDED))
                .isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void onlySucceededPaymentCanBecomeRefunded() {
        assertThat(PaymentStateMachine.refund(PaymentStatus.SUCCEEDED))
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(PaymentStateMachine.refund(PaymentStatus.REFUNDED))
                .isEqualTo(PaymentStatus.REFUNDED);

        assertThatThrownBy(() -> PaymentStateMachine.refund(PaymentStatus.FAILED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only successful payments can be refunded");
    }
}
