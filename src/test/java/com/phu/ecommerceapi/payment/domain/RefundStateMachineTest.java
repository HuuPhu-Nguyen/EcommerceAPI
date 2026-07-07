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
    void terminalRefundIgnoresDuplicateProviderOutcome() {
        assertThat(RefundStateMachine.providerSucceeded(RefundStatus.FAILED))
                .isEqualTo(RefundStatus.FAILED);
        assertThat(RefundStateMachine.providerFailed(RefundStatus.SUCCEEDED))
                .isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(RefundStateMachine.providerTimedOut(RefundStatus.PROVIDER_TIMEOUT))
                .isEqualTo(RefundStatus.PROVIDER_TIMEOUT);
    }
}
