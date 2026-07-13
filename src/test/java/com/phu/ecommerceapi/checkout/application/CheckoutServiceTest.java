package com.phu.ecommerceapi.checkout.application;

import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import com.phu.ecommerceapi.cart.application.CartPersistencePort;
import com.phu.ecommerceapi.cart.application.CartItemSnapshot;
import com.phu.ecommerceapi.cart.application.CartSnapshot;
import com.phu.ecommerceapi.cart.application.MutableCart;
import com.phu.ecommerceapi.catalog.application.CartProductSnapshot;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.order.domain.OrderStatus;
import com.phu.ecommerceapi.payment.application.PaymentProviderAvailabilityService;
import com.phu.ecommerceapi.shared.domain.Money;
import com.phu.ecommerceapi.shared.observability.BusinessMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    private PaymentProviderAvailabilityService paymentProviderAvailabilityService;

    @Mock
    private AuditEventRecorder auditEventRecorder;

    @Mock
    private BusinessMetrics businessMetrics;

    private FakeCartPersistencePort cartPersistencePort;
    private FakeInventoryReservationPort inventoryReservationPort;
    private FakeCheckoutOrderStorePort checkoutOrderStorePort;
    private CheckoutService checkoutService;

    @BeforeEach
    void setUp() {
        cartPersistencePort = new FakeCartPersistencePort(cartWithOneItem());
        inventoryReservationPort = new FakeInventoryReservationPort();
        checkoutOrderStorePort = new FakeCheckoutOrderStorePort();
        checkoutService = new CheckoutService(
                cartPersistencePort,
                inventoryReservationPort,
                checkoutOrderStorePort,
                paymentProviderAvailabilityService,
                auditEventRecorder,
                businessMetrics
        );
    }

    @Test
    void checkoutCreatesOrderReservesInventoryClearsCartAndAuditsThroughPorts() {
        when(paymentProviderAvailabilityService.allowedProviderCodes(Money.of("20.00", "USD").amount(), "USD"))
                .thenReturn(List.of("fake"));

        CheckoutResponse response = checkoutService.checkout(10L, currentUser());

        assertThat(response.cartId()).isEqualTo(10L);
        assertThat(response.customerId()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.total()).isEqualByComparingTo("20.00");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.allowedPaymentProviders()).containsExactly("fake");
        assertThat(response.items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.productId()).isEqualTo(200L);
                    assertThat(item.quantity()).isEqualTo(2);
                    assertThat(item.lineTotal()).isEqualByComparingTo("20.00");
                });
        assertThat(inventoryReservationPort.reservations).containsExactly(new Reservation(200L, 2));
        assertThat(checkoutOrderStorePort.createdFromCart).isEqualTo(cartPersistencePort.originalCart);
        assertThat(cartPersistencePort.cleared).isTrue();
        verify(businessMetrics).checkoutAttempt("success");

        ArgumentCaptor<AuditEventCommand> auditCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(auditEventRecorder).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().actorSubject()).isEqualTo("subject-1");
        assertThat(auditCaptor.getValue().action()).isEqualTo("CHECKOUT_ORDER_CREATED");
        assertThat(auditCaptor.getValue().resourceType()).isEqualTo("ORDER");
    }

    private CartSnapshot cartWithOneItem() {
        Money unitPrice = Money.of("10.00", "USD");
        return new CartSnapshot(
                10L,
                100L,
                "subject-1",
                Money.of("20.00", "USD"),
                "USD",
                List.of(new CartItemSnapshot(200L, "Keyboard", 2, unitPrice, unitPrice.multiply(
                        com.phu.ecommerceapi.shared.domain.Quantity.of(2)
                ), true))
        );
    }

    private CurrentUser currentUser() {
        return new CurrentUser(
                "subject-1",
                "customer@example.com",
                "customer@example.com",
                Set.of("customer"),
                Set.of()
        );
    }

    private static final class FakeCartPersistencePort implements CartPersistencePort {

        private final CartSnapshot originalCart;
        private boolean cleared;

        private FakeCartPersistencePort(CartSnapshot originalCart) {
            this.originalCart = originalCart;
        }

        @Override
        public CartSnapshot create(com.phu.ecommerceapi.customer.application.CustomerIdentity owner) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public Optional<CartSnapshot> findWithItemsById(long cartId) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public <T> Optional<T> updateWithItemsForMutation(long cartId, Function<MutableCart, T> mutation) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public <T> Optional<T> updateWithItemsForCheckout(long cartId, Function<MutableCart, T> mutation) {
            assertThat(cartId).isEqualTo(originalCart.id());
            return Optional.of(mutation.apply(new FakeMutableCart()));
        }

        private final class FakeMutableCart implements MutableCart {

            @Override
            public CartSnapshot snapshot() {
                return originalCart;
            }

            @Override
            public int quantityForProduct(long productId) {
                return 0;
            }

            @Override
            public void addItem(CartProductSnapshot product, int quantity) {
                throw new UnsupportedOperationException("not needed");
            }

            @Override
            public void updateItemQuantity(CartProductSnapshot product, int quantity) {
                throw new UnsupportedOperationException("not needed");
            }

            @Override
            public void removeItem(long productId) {
                throw new UnsupportedOperationException("not needed");
            }

            @Override
            public void clear() {
                cleared = true;
            }
        }
    }

    private static final class FakeInventoryReservationPort implements CheckoutInventoryReservationPort {

        private final List<Reservation> reservations = new ArrayList<>();

        @Override
        public void reserve(long productId, int requestedQuantity) {
            reservations.add(new Reservation(productId, requestedQuantity));
        }
    }

    private static final class FakeCheckoutOrderStorePort implements CheckoutOrderStorePort {

        private CartSnapshot createdFromCart;

        @Override
        public boolean existsByCartId(long cartId) {
            return false;
        }

        @Override
        public CheckoutOrderSnapshot createPendingPayment(CartSnapshot cart) {
            createdFromCart = cart;
            return new CheckoutOrderSnapshot(
                    UUID.fromString("00000000-0000-0000-0000-000000000123"),
                    cart.id(),
                    cart.ownerId(),
                    OrderStatus.PENDING_PAYMENT,
                    cart.total().amount(),
                    cart.currency(),
                    OffsetDateTime.parse("2026-07-13T00:00:00Z"),
                    cart.items()
                            .stream()
                            .map(item -> new CheckoutOrderItemSnapshot(
                                    item.productId(),
                                    item.productName(),
                                    item.quantity(),
                                    item.unitPrice().amount(),
                                    item.lineTotal().amount()
                            ))
                            .toList()
            );
        }
    }

    private record Reservation(long productId, int quantity) {
    }
}
