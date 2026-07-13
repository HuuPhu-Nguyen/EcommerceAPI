package com.phu.ecommerceapi.cart.application;

import com.phu.ecommerceapi.cart.infrastructure.CartModel;
import com.phu.ecommerceapi.cart.infrastructure.CartRepo;
import com.phu.ecommerceapi.catalog.application.CartProductLookupPort;
import com.phu.ecommerceapi.catalog.application.CartProductSnapshot;
import com.phu.ecommerceapi.customer.application.CustomerIdentity;
import com.phu.ecommerceapi.customer.application.CustomerIdentityLookupPort;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.inventory.application.InventoryReservationService;
import com.phu.ecommerceapi.inventory.application.InventorySnapshot;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRepository;
import com.phu.ecommerceapi.shared.api.ConflictException;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import com.phu.ecommerceapi.shared.api.OutOfStockException;
import com.phu.ecommerceapi.shared.domain.Money;
import com.phu.ecommerceapi.shared.domain.Quantity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class CartService {

    private final CartRepo cartRepo;
    private final CartProductLookupPort cartProductLookupPort;
    private final CustomerIdentityLookupPort customerIdentityLookupPort;
    private final InventoryReservationService inventoryReservationService;
    private final CustomerOrderRepository orderRepository;

    public CartService(
            CartRepo cartRepo,
            CartProductLookupPort cartProductLookupPort,
            CustomerIdentityLookupPort customerIdentityLookupPort,
            InventoryReservationService inventoryReservationService,
            CustomerOrderRepository orderRepository
    ) {
        this.cartRepo = cartRepo;
        this.cartProductLookupPort = cartProductLookupPort;
        this.customerIdentityLookupPort = customerIdentityLookupPort;
        this.inventoryReservationService = inventoryReservationService;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public CartResponse createCart(CurrentUser currentUser) {
        CustomerIdentity owner = resolveOwner(currentUser);
        CartModel cart = new CartModel(owner);
        return toResponse(cartRepo.save(cart));
    }

    @Transactional(readOnly = true)
    public CartResponse viewCart(long cartId, CurrentUser currentUser) {
        return toResponse(getOwnedCart(cartId, currentUser));
    }

    @Transactional(readOnly = true)
    public List<CartItemResponse> getCartItems(long cartId, CurrentUser currentUser) {
        return toResponse(getOwnedCart(cartId, currentUser)).items();
    }

    @Transactional
    public CartResponse addItem(long cartId, long productId, int quantity, CurrentUser currentUser) {
        Quantity requestedQuantity = Quantity.of(quantity);
        CartModel cart = getOwnedMutableCart(cartId, currentUser);
        CartProductSnapshot product = getActiveProduct(productId);

        int requestedTotalQuantity = cart.quantityForProduct(productId) + requestedQuantity.value();
        assertInventoryCanSupport(productId, requestedTotalQuantity);

        cart.addItem(product, requestedQuantity.value());
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItemQuantity(long cartId, long productId, int quantity, CurrentUser currentUser) {
        Quantity requestedQuantity = Quantity.of(quantity);
        CartModel cart = getOwnedMutableCart(cartId, currentUser);
        CartProductSnapshot product = getActiveProduct(productId);

        assertInventoryCanSupport(productId, requestedQuantity.value());

        cart.updateItemQuantity(product, requestedQuantity.value());
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(long cartId, long productId, CurrentUser currentUser) {
        CartModel cart = getOwnedMutableCart(cartId, currentUser);
        cart.removeItem(productId);
        return toResponse(cart);
    }

    private CartModel getOwnedMutableCart(long cartId, CurrentUser currentUser) {
        CartModel cart = cartRepo.findForUpdateWithItemsById(cartId)
                .orElseThrow(() -> new NotFoundException("Cart not found"));
        assertCartOwner(cart, currentUser);
        assertCartNotCheckedOut(cart);
        return cart;
    }

    private CartModel getOwnedCart(long cartId, CurrentUser currentUser) {
        CartModel cart = cartRepo.findWithItemsById(cartId)
                .orElseThrow(() -> new NotFoundException("Cart not found"));
        assertCartOwner(cart, currentUser);
        return cart;
    }

    private void assertCartNotCheckedOut(CartModel cart) {
        if (orderRepository.existsByCartId(cart.getId())) {
            throw new ConflictException("Cart has already been checked out");
        }
    }

    private CartProductSnapshot getActiveProduct(long productId) {
        return cartProductLookupPort.findActiveForCart(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    private void assertInventoryCanSupport(long productId, int requestedQuantity) {
        InventorySnapshot inventory = inventoryReservationService.getInventory(productId);
        if (inventory.availableQuantity() < requestedQuantity) {
            throw new OutOfStockException("Requested quantity exceeds available stock");
        }
    }

    private CustomerIdentity resolveOwner(CurrentUser currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authenticated customer is required");
        }

        return customerIdentityLookupPort.findByIdentitySubject(currentUser.subject())
                .orElseThrow(() -> new NotFoundException("Customer profile not found"));
    }

    private void assertCartOwner(CartModel cart, CurrentUser currentUser) {
        if (currentUser == null || !cart.belongsToIdentitySubject(currentUser.subject())) {
            throw new AccessDeniedException("Cart does not belong to current user");
        }
    }

    private CartResponse toResponse(CartModel cart) {
        List<CartItemResponse> items = cart.itemSnapshots()
                .stream()
                .sorted(Comparator.comparingLong(CartItemSnapshot::productId))
                .map(this::toItemResponse)
                .toList();

        Money total = cart.totalMoney();
        return new CartResponse(cart.getId(), total.amount(), total.currency().getCurrencyCode(), items);
    }

    private CartItemResponse toItemResponse(CartItemSnapshot item) {
        return new CartItemResponse(
                item.productId(),
                item.productName(),
                item.quantity(),
                item.unitPrice().amount(),
                item.unitPrice().currency().getCurrencyCode(),
                item.lineTotal().amount()
        );
    }
}
