package com.phu.ecommerceapi.payment.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefundStateMachineTest {

    @Test
    void pendingRefundCanCompleteFromProviderOutcome() {
        assertThat(RefundStateMachine.providerSucceeded(RefundStatus.PENDING))
                .isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(RefundStateMachine.providerFailed(RefundStatus.PENDING))
                .isEqualTo(RefundStatus.FAILED);
        assertThat(RefundStateMachine.providerTimedOut(RefundStatus.PENDING))
                .isEqualTo(RefundStatus.PROVIDER_TIMEOUT);
    }

    @Test
    void providerTimeoutRefundCanCompleteFromCurrentProviderOutcome() {
        assertThat(RefundStateMachine.providerSucceeded(RefundStatus.PROVIDER_TIMEOUT))
                .isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(RefundStateMachine.providerFailed(RefundStatus.PROVIDER_TIMEOUT))
                .isEqualTo(RefundStatus.FAILED);
    }

    @Test
    void succeededOrFailedRefundIgnoresDuplicateProviderOutcome() {
        assertThat(RefundStateMachine.providerSucceeded(RefundStatus.FAILED))
                .isEqualTo(RefundStatus.FAILED);
        assertThat(RefundStateMachine.providerFailed(RefundStatus.SUCCEEDED))
                .isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(RefundStateMachine.providerTimedOut(RefundStatus.SUCCEEDED))
                .isEqualTo(RefundStatus.SUCCEEDED);
    }
}
