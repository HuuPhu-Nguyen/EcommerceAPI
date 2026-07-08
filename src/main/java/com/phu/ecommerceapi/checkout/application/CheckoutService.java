package com.phu.ecommerceapi.checkout.application;

import com.phu.ecommerceapi.Product.ProductModel;
import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import com.phu.ecommerceapi.cart.infrastructure.CartItemModel;
import com.phu.ecommerceapi.cart.infrastructure.CartModel;
import com.phu.ecommerceapi.cart.infrastructure.CartRepo;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.inventory.application.InventoryReservationService;
import com.phu.ecommerceapi.order.application.OrderItemResponse;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRecord;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRepository;
import com.phu.ecommerceapi.order.infrastructure.OrderItemRecord;
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
    private static final String DEFAULT_CURRENCY = "USD";

    private final CartRepo cartRepo;
    private final InventoryReservationService inventoryReservationService;
    private final CustomerOrderRepository orderRepository;
    private final PaymentProviderAvailabilityService paymentProviderAvailabilityService;
    private final AuditEventRecorder auditEventRecorder;
    private final BusinessMetrics businessMetrics;

    public CheckoutService(
            CartRepo cartRepo,
            InventoryReservationService inventoryReservationService,
            CustomerOrderRepository orderRepository,
            PaymentProviderAvailabilityService paymentProviderAvailabilityService,
            AuditEventRecorder auditEventRecorder,
            BusinessMetrics businessMetrics
    ) {
        this.cartRepo = cartRepo;
        this.inventoryReservationService = inventoryReservationService;
        this.orderRepository = orderRepository;
        this.paymentProviderAvailabilityService = paymentProviderAvailabilityService;
        this.auditEventRecorder = auditEventRecorder;
        this.businessMetrics = businessMetrics;
    }

    @Transactional
    public CheckoutResponse checkout(long cartId, CurrentUser currentUser) {
        try {
            CartModel cart = cartRepo.findForCheckoutById(cartId)
                    .orElseThrow(() -> new NotFoundException("Cart not found"));
            assertCartOwner(cart, currentUser);

            if (orderRepository.existsByCartId(cart.getId())) {
                throw new ConflictException("Cart has already been checked out");
            }
            if (cart.isEmpty()) {
                throw new ConflictException("Cannot checkout an empty cart");
            }

            requireAvailablePaymentProvider(cart.getTotal(), cart.getCurrency());

            CustomerOrderRecord order = CustomerOrderRecord.pendingPayment(
                    cart.getOwner(),
                    cart.getId(),
                    DEFAULT_CURRENCY
            );
            for (CartItemModel item : cart.getItems()) {
                inventoryReservationService.reserve(item.getProductId(), item.getQuantity());
                ProductModel product = item.getProductModel();
                order.addItem(product, item.getQuantity(), product.priceMoney());
            }

            CustomerOrderRecord savedOrder = orderRepository.save(order);
            cart.clear();
            cartRepo.save(cart);
            recordAudit(currentUser, savedOrder);
            businessMetrics.checkoutAttempt("success");
            return toResponse(
                    savedOrder,
                    requireAvailablePaymentProvider(savedOrder.getTotalAmount(), savedOrder.getCurrency())
            );
        } catch (RuntimeException exception) {
            businessMetrics.checkoutAttempt("failure");
            throw exception;
        }
    }

    private void assertCartOwner(CartModel cart, CurrentUser currentUser) {
        if (currentUser == null || cart.getOwner() == null || !belongsToCurrentUser(cart.getOwner(), currentUser)) {
            throw new AccessDeniedException("Cart does not belong to current user");
        }
    }

    private boolean belongsToCurrentUser(UserModel owner, CurrentUser currentUser) {
        return currentUser.hasSubject(owner.getIdentitySubject());
    }

    private List<String> requireAvailablePaymentProvider(BigDecimal amount, String currency) {
        List<String> allowedProviders = paymentProviderAvailabilityService.allowedProviderCodes(amount, currency);
        if (allowedProviders.isEmpty()) {
            throw new ConflictException("No enabled payment provider is available for this order");
        }
        return allowedProviders;
    }

    private void recordAudit(CurrentUser actor, CustomerOrderRecord order) {
        auditEventRecorder.record(new AuditEventCommand(
                actor.subject(),
                "CHECKOUT_ORDER_CREATED",
                ORDER_RESOURCE_TYPE,
                order.getId().toString(),
                "cartId=%d;status=%s;total=%s %s".formatted(
                        order.getCartId(),
                        order.getStatus(),
                        order.getTotalAmount(),
                        order.getCurrency()
                )
        ));
    }

    private CheckoutResponse toResponse(CustomerOrderRecord order, List<String> allowedPaymentProviders) {
        List<OrderItemResponse> items = order.getItems()
                .stream()
                .sorted(Comparator.comparingLong(OrderItemRecord::getProductId))
                .map(item -> new OrderItemResponse(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPriceAmount(),
                        order.getCurrency(),
                        item.getLineTotalAmount()
                ))
                .toList();

        return new CheckoutResponse(
                order.getId(),
                order.getCartId(),
                order.getCustomer().getId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getCreatedAt(),
                items,
                allowedPaymentProviders
        );
    }
}
