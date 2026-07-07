package com.phu.ecommerceapi.cart.application;

import com.phu.ecommerceapi.Product.ProductModel;
import com.phu.ecommerceapi.Product.ProductRepo;
import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.User.UserRepo;
import com.phu.ecommerceapi.cart.infrastructure.CartItemModel;
import com.phu.ecommerceapi.cart.infrastructure.CartModel;
import com.phu.ecommerceapi.cart.infrastructure.CartRepo;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.inventory.application.InventoryReservationService;
import com.phu.ecommerceapi.inventory.application.InventorySnapshot;
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
    private final ProductRepo productRepo;
    private final UserRepo userRepo;
    private final InventoryReservationService inventoryReservationService;

    public CartService(
            CartRepo cartRepo,
            ProductRepo productRepo,
            UserRepo userRepo,
            InventoryReservationService inventoryReservationService
    ) {
        this.cartRepo = cartRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
        this.inventoryReservationService = inventoryReservationService;
    }

    @Transactional
    public CartResponse createCart(CurrentUser currentUser) {
        UserModel owner = resolveOwner(currentUser);
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
        CartModel cart = getOwnedCart(cartId, currentUser);
        ProductModel product = getActiveProduct(productId);

        int requestedTotalQuantity = cart.quantityForProduct(productId) + requestedQuantity.value();
        assertInventoryCanSupport(productId, requestedTotalQuantity);

        cart.addItem(product, requestedQuantity.value());
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItemQuantity(long cartId, long productId, int quantity, CurrentUser currentUser) {
        Quantity requestedQuantity = Quantity.of(quantity);
        CartModel cart = getOwnedCart(cartId, currentUser);
        ProductModel product = getActiveProduct(productId);

        assertInventoryCanSupport(productId, requestedQuantity.value());

        cart.updateItemQuantity(product, requestedQuantity.value());
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(long cartId, long productId, CurrentUser currentUser) {
        CartModel cart = getOwnedCart(cartId, currentUser);
        cart.removeItem(productId);
        return toResponse(cart);
    }

    private CartModel getOwnedCart(long cartId, CurrentUser currentUser) {
        CartModel cart = cartRepo.findWithItemsById(cartId)
                .orElseThrow(() -> new NotFoundException("Cart not found"));
        assertCartOwner(cart, currentUser);
        return cart;
    }

    private ProductModel getActiveProduct(long productId) {
        return productRepo.findByProductIdAndActiveTrue(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    private void assertInventoryCanSupport(long productId, int requestedQuantity) {
        InventorySnapshot inventory = inventoryReservationService.getInventory(productId);
        if (inventory.availableQuantity() < requestedQuantity) {
            throw new OutOfStockException("Requested quantity exceeds available stock");
        }
    }

    private UserModel resolveOwner(CurrentUser currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authenticated customer is required");
        }

        UserModel owner = userRepo.findByUsername(currentUser.username());
        if (owner == null && currentUser.email() != null) {
            owner = userRepo.findByEmail(currentUser.email());
        }
        if (owner == null) {
            throw new NotFoundException("Customer profile not found");
        }
        return owner;
    }

    private void assertCartOwner(CartModel cart, CurrentUser currentUser) {
        if (currentUser == null || cart.getOwner() == null || !belongsToCurrentUser(cart.getOwner(), currentUser)) {
            throw new AccessDeniedException("Cart does not belong to current user");
        }
    }

    private boolean belongsToCurrentUser(UserModel owner, CurrentUser currentUser) {
        return matches(owner.getUsername(), currentUser.username())
                || matches(owner.getEmail(), currentUser.email());
    }

    private boolean matches(String ownerValue, String currentUserValue) {
        return ownerValue != null
                && currentUserValue != null
                && ownerValue.equalsIgnoreCase(currentUserValue);
    }

    private CartResponse toResponse(CartModel cart) {
        List<CartItemResponse> items = cart.getItems()
                .stream()
                .sorted(Comparator.comparingLong(CartItemModel::getProductId))
                .map(this::toItemResponse)
                .toList();

        Money total = cart.totalMoney();
        return new CartResponse(cart.getId(), total.amount(), total.currency().getCurrencyCode(), items);
    }

    private CartItemResponse toItemResponse(CartItemModel item) {
        ProductModel product = item.getProductModel();
        Money unitPrice = product.priceMoney();
        Money lineTotal = item.lineTotalMoney();

        return new CartItemResponse(
                product.getProductId(),
                product.getName(),
                item.getQuantity(),
                unitPrice.amount(),
                unitPrice.currency().getCurrencyCode(),
                lineTotal.amount()
        );
    }
}
