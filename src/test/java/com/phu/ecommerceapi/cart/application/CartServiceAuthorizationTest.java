package com.phu.ecommerceapi.cart.application;

import com.phu.ecommerceapi.Product.ProductRepo;
import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.User.UserRepo;
import com.phu.ecommerceapi.cart.infrastructure.CartModel;
import com.phu.ecommerceapi.cart.infrastructure.CartRepo;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.inventory.application.InventoryReservationService;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceAuthorizationTest {

    @Mock
    private CartRepo cartRepo;

    @Mock
    private ProductRepo productRepo;

    @Mock
    private UserRepo userRepo;

    @Mock
    private InventoryReservationService inventoryReservationService;

    @Mock
    private CustomerOrderRepository orderRepository;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepo, productRepo, userRepo, inventoryReservationService, orderRepository);
    }

    @Test
    void ownerCanReadCartItems() {
        CartModel cart = cartOwnedBy("subject-1", "customer@example.com", "customer@example.com");
        when(cartRepo.findWithItemsById(10L)).thenReturn(Optional.of(cart));

        assertThat(cartService.getCartItems(10L, currentUser("customer@example.com")).isEmpty())
                .isTrue();
    }

    @Test
    void crossCustomerCartReadIsDenied() {
        CartModel cart = cartOwnedBy("owner-subject", "owner@example.com", "owner@example.com");
        when(cartRepo.findWithItemsById(10L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.getCartItems(10L, currentUser("attacker@example.com")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Cart does not belong to current user");
    }

    @Test
    void matchingUsernameAndEmailDoNotGrantOwnershipWhenSubjectDiffers() {
        CartModel cart = cartOwnedBy("owner-subject", "customer@example.com", "customer@example.com");
        when(cartRepo.findWithItemsById(10L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.getCartItems(10L, currentUser("customer@example.com")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Cart does not belong to current user");
    }

    private CartModel cartOwnedBy(String identitySubject, String username, String email) {
        UserModel owner = UserModel.builder()
                .identitySubject(identitySubject)
                .username(username)
                .email(email)
                .build();
        return new CartModel(owner);
    }

    private CurrentUser currentUser(String username) {
        return new CurrentUser("subject-1", username, username, Set.of("customer"), Set.of("cart:read"));
    }
}
