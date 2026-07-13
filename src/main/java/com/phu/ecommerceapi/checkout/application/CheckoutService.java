package com.phu.ecommerceapi.checkout.application;

import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import com.phu.ecommerceapi.cart.application.CartItemSnapshot;
import com.phu.ecommerceapi.cart.application.CartPersistencePort;
import com.phu.ecommerceapi.cart.application.CartSnapshot;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.order.application.OrderItemResponse;
import com.phu.ecommerceapi.payment.application.PaymentProviderAvailabilityService;
import com.phu.ecommerceapi.shared.api.ConflictException;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import com.phu.ecommerceapi.shared.observability.BusinessMetrics;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class CheckoutService {

    private static final String ORDER_RESOURCE_TYPE = "ORDER";

    private final CartPersistencePort cartPersistencePort;
    private final CheckoutInventoryReservationPort inventoryReservationPort;
    private final CheckoutOrderStorePort checkoutOrderStorePort;
    private final PaymentProviderAvailabilityService paymentProviderAvailabilityService;
    private final AuditEventRecorder auditEventRecorder;
    private final BusinessMetrics businessMetrics;

    public CheckoutService(
            CartPersistencePort cartPersistencePort,
            CheckoutInventoryReservationPort inventoryReservationPort,
            CheckoutOrderStorePort checkoutOrderStorePort,
            PaymentProviderAvailabilityService paymentProviderAvailabilityService,
            AuditEventRecorder auditEventRecorder,
            BusinessMetrics businessMetrics
    ) {
        this.cartPersistencePort = cartPersistencePort;
        this.inventoryReservationPort = inventoryReservationPort;
        this.checkoutOrderStorePort = checkoutOrderStorePort;
        this.paymentProviderAvailabilityService = paymentProviderAvailabilityService;
        this.auditEventRecorder = auditEventRecorder;
        this.businessMetrics = businessMetrics;
    }

    @Transactional
    public CheckoutResponse checkout(long cartId, CurrentUser currentUser) {
        try {
            CheckoutOrderSnapshot savedOrder = cartPersistencePort.updateWithItemsForCheckout(cartId, cart -> {
                CartSnapshot snapshot = cart.snapshot();
                assertCartOwner(snapshot, currentUser);

                if (checkoutOrderStorePort.existsByCartId(snapshot.id())) {
                    throw new ConflictException("Cart has already been checked out");
                }
                if (snapshot.isEmpty()) {
                    throw new ConflictException("Cannot checkout an empty cart");
                }

                validateCartItemsForCheckout(snapshot);
                requireAvailablePaymentProvider(snapshot.total().amount(), snapshot.currency());

                for (CartItemSnapshot item : snapshot.items()) {
                    inventoryReservationPort.reserve(item.productId(), item.quantity());
                }

                CheckoutOrderSnapshot order = checkoutOrderStorePort.createPendingPayment(snapshot);
                cart.clear();
                recordAudit(currentUser, order);
                return order;
            }).orElseThrow(() -> new NotFoundException("Cart not found"));
            businessMetrics.checkoutAttempt("success");
            return toResponse(
                    savedOrder,
                    requireAvailablePaymentProvider(savedOrder.totalAmount(), savedOrder.currency())
            );
        } catch (RuntimeException exception) {
            businessMetrics.checkoutAttempt("failure");
            throw exception;
        }
    }

    private void validateCartItemsForCheckout(CartSnapshot cart) {
        for (CartItemSnapshot item : cart.items()) {
            if (!item.active()) {
                throw new ConflictException("Product is no longer available for checkout");
            }
            if (!item.unitPrice().currency().getCurrencyCode().equals(cart.currency())) {
                throw new ConflictException("Cart contains mixed currencies");
            }
        }
    }

    private void assertCartOwner(CartSnapshot cart, CurrentUser currentUser) {
        if (currentUser == null || !cart.belongsToIdentitySubject(currentUser.subject())) {
            throw new AccessDeniedException("Cart does not belong to current user");
        }
    }

    private List<String> requireAvailablePaymentProvider(BigDecimal amount, String currency) {
        List<String> allowedProviders = paymentProviderAvailabilityService.allowedProviderCodes(amount, currency);
        if (allowedProviders.isEmpty()) {
            throw new ConflictException("No enabled payment provider is available for this order");
        }
        return allowedProviders;
    }

    private void recordAudit(CurrentUser actor, CheckoutOrderSnapshot order) {
        auditEventRecorder.record(new AuditEventCommand(
                actor.subject(),
                "CHECKOUT_ORDER_CREATED",
                ORDER_RESOURCE_TYPE,
                order.id().toString(),
                "cartId=%d;status=%s;total=%s %s".formatted(
                        order.cartId(),
                        order.status(),
                        order.totalAmount(),
                        order.currency()
                )
        ));
    }

    private CheckoutResponse toResponse(CheckoutOrderSnapshot order, List<String> allowedPaymentProviders) {
        List<OrderItemResponse> items = order.items()
                .stream()
                .sorted(Comparator.comparingLong(CheckoutOrderItemSnapshot::productId))
                .map(item -> new OrderItemResponse(
                        item.productId(),
                        item.productName(),
                        item.quantity(),
                        item.unitPriceAmount(),
                        order.currency(),
                        item.lineTotalAmount()
                ))
                .toList();

        return new CheckoutResponse(
                order.id(),
                order.cartId(),
                order.customerId(),
                order.status(),
                order.totalAmount(),
                order.currency(),
                order.createdAt(),
                items,
                allowedPaymentProviders
        );
    }
}
