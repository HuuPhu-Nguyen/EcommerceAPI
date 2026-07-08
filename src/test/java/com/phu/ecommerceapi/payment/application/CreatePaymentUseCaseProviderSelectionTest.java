package com.phu.ecommerceapi.payment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.config.AppProperties;
import com.phu.ecommerceapi.customer.application.CustomerProfile;
import com.phu.ecommerceapi.customer.application.CustomerProfileLookup;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatePaymentUseCaseProviderSelectionTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            "customer-subject",
            "customer@example.com",
            "customer@example.com",
            Set.of("customer"),
            Set.of("payment:create")
    );

    private static final long CUSTOMER_ID = 42L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CustomerProfileLookup customerProfileLookup;

    @Mock
    private PaymentIdempotencyService idempotencyService;

    @Mock
    private PaymentAttemptService paymentAttemptService;

    @Test
    void multiProviderConfigRequiresProviderBeforeCreatingIdempotencyRecordOrAttempt() {
        UUID orderId = UUID.randomUUID();
        CreatePaymentUseCase useCase = useCaseWithProviders(
                properties("fake", List.of("fake", "stripe")),
                List.of(provider("fake"), provider("stripe"))
        );
        stubCustomerProfile();
        when(idempotencyService.findExisting(any(PaymentIdempotencyCommand.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.create(command(orderId, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("provider is required when multiple payment providers are enabled");

        verify(idempotencyService, never()).start(any(PaymentIdempotencyCommand.class));
        verifyNoInteractions(paymentAttemptService);
    }

    @Test
    void disabledProviderIsRejectedBeforeCreatingIdempotencyRecordOrAttempt() {
        UUID orderId = UUID.randomUUID();
        CreatePaymentUseCase useCase = useCaseWithProviders(
                properties("fake", List.of("fake")),
                List.of(provider("fake"), provider("stripe"))
        );
        stubCustomerProfile();
        when(idempotencyService.findExisting(any(PaymentIdempotencyCommand.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.create(command(orderId, "stripe")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payment provider is disabled: stripe");

        verify(idempotencyService, never()).start(any(PaymentIdempotencyCommand.class));
        verifyNoInteractions(paymentAttemptService);
    }

    @Test
    void selectedProviderReceivesProviderScopedIdempotencyKeyAndIsReturnedInResponse() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        CapturingPaymentProvider stripeProvider = provider("stripe");
        CreatePaymentUseCase useCase = useCaseWithProviders(
                properties("fake", List.of("fake", "stripe")),
                List.of(provider("fake"), stripeProvider)
        );
        stubCustomerProfile();
        when(idempotencyService.findExisting(any(PaymentIdempotencyCommand.class))).thenReturn(Optional.empty());
        when(paymentAttemptService.validatePayable(CUSTOMER_ID, orderId))
                .thenReturn(new PaymentPayableOrder(orderId, new BigDecimal("20.00"), "USD"));
        when(idempotencyService.start(any(PaymentIdempotencyCommand.class)))
                .thenReturn(PaymentIdempotencyDecision.started(99L));
        when(paymentAttemptService.startAttempt(CUSTOMER_ID, orderId, "payment-key", "stripe"))
                .thenReturn(new PaymentAttemptSnapshot(paymentId, orderId, new BigDecimal("20.00"), "USD"));
        when(paymentAttemptService.completeAttempt(eq(paymentId), any(PaymentProviderResult.class), eq(CURRENT_USER)))
                .thenReturn(paymentResponse(paymentId, orderId));

        CreatePaymentResult result = useCase.create(command(orderId, "stripe"));

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.responseBody()).contains("\"provider\":\"stripe\"");
        assertThat(stripeProvider.lastPaymentRequest.idempotencyKey())
                .isEqualTo("payment:stripe:%d:%s:payment-key".formatted(CUSTOMER_ID, orderId));
        verify(paymentAttemptService).startAttempt(CUSTOMER_ID, orderId, "payment-key", "stripe");
        verify(idempotencyService).complete(eq(99L), eq(HttpStatus.OK.value()), contains("\"provider\":\"stripe\""));
    }

    private void stubCustomerProfile() {
        when(customerProfileLookup.findCurrentUserProfile(CURRENT_USER))
                .thenReturn(Optional.of(new CustomerProfile(
                        CUSTOMER_ID,
                        "customer-subject",
                        "customer@example.com",
                        "Payment",
                        "Customer",
                        "customer@example.com"
                )));
    }

    private CreatePaymentUseCase useCaseWithProviders(
            AppProperties properties,
            List<PaymentProvider> providers
    ) {
        PaymentProviderRegistry registry = new PaymentProviderRegistry(providers, properties);
        return new CreatePaymentUseCase(
                objectMapper,
                customerProfileLookup,
                idempotencyService,
                paymentAttemptService,
                registry,
                new PaymentProviderAvailabilityService(registry)
        );
    }

    private AppProperties properties(String activeProvider, List<String> enabledProviders) {
        return new AppProperties(
                "test",
                "keycloak",
                new AppProperties.PaymentProviderProperties(activeProvider, enabledProviders),
                new AppProperties.FakeProvider("fake-webhook-secret")
        );
    }

    private CreatePaymentCommand command(UUID orderId, String provider) {
        return new CreatePaymentCommand(
                CURRENT_USER,
                "payment-key",
                requestBody(orderId, provider),
                orderId,
                provider,
                "pm_approved"
        );
    }

    private String requestBody(UUID orderId, String provider) {
        if (provider == null) {
            return """
                    {
                      "orderId": "%s",
                      "paymentMethodToken": "pm_approved"
                    }
                    """.formatted(orderId);
        }
        return """
                {
                  "orderId": "%s",
                  "provider": "%s",
                  "paymentMethodToken": "pm_approved"
                }
                """.formatted(orderId, provider);
    }

    private PaymentAttemptResponse paymentResponse(UUID paymentId, UUID orderId) {
        return new PaymentAttemptResponse(
                paymentId,
                orderId,
                null,
                "SUCCEEDED",
                "SUCCEEDED",
                "stripe_payment",
                null,
                "approved",
                new BigDecimal("20.00"),
                "USD"
        );
    }

    private CapturingPaymentProvider provider(String providerCode) {
        return new CapturingPaymentProvider(providerCode);
    }

    private static final class CapturingPaymentProvider implements PaymentProvider {

        private final String providerCode;
        private PaymentProviderRequest lastPaymentRequest;

        private CapturingPaymentProvider(String providerCode) {
            this.providerCode = providerCode;
        }

        @Override
        public String providerCode() {
            return providerCode;
        }

        @Override
        public PaymentProviderCapabilities capabilities() {
            return new PaymentProviderCapabilities(
                    Set.of("USD"),
                    new BigDecimal("0.50"),
                    new BigDecimal("999999.99"),
                    true,
                    true,
                    true,
                    null
            );
        }

        @Override
        public PaymentProviderResult createPayment(PaymentProviderRequest request) {
            lastPaymentRequest = request;
            return PaymentProviderResult.succeeded(providerCode + "_payment", "approved");
        }

        @Override
        public PaymentRefundProviderResult refundPayment(PaymentRefundProviderRequest request) {
            return PaymentRefundProviderResult.succeeded(providerCode + "_refund", "approved");
        }
    }
}
