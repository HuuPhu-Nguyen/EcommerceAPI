package com.phu.ecommerceapi.Cart;

import com.phu.ecommerceapi.Product.ProductRepo;
import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.User.UserRepo;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
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

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepo, productRepo, userRepo);
    }

    @Test
    void ownerCanReadCartItems() {
        CartModel cart = cartOwnedBy("customer@example.com", "customer@example.com");
        when(cartRepo.findById(10L)).thenReturn(Optional.of(cart));

        assertThat(cartService.getCartItems(10L, currentUser("customer@example.com")).isEmpty())
                .isTrue();
    }

    @Test
    void crossCustomerCartReadIsDenied() {
        CartModel cart = cartOwnedBy("owner@example.com", "owner@example.com");
        when(cartRepo.findById(10L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.getCartItems(10L, currentUser("attacker@example.com")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Cart does not belong to current user");
    }

    @Test
    void missingCartOwnerIsDenied() {
        CartModel cart = new CartModel();
        cart.setItems(new ArrayList<>());
        when(cartRepo.findById(10L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.getCartItems(10L, currentUser("customer@example.com")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Cart does not belong to current user");
    }

    private CartModel cartOwnedBy(String username, String email) {
        UserModel owner = UserModel.builder()
                .username(username)
                .email(email)
                .build();
        CartModel cart = new CartModel();
        cart.setOwner(owner);
        cart.setItems(new ArrayList<>());
        return cart;
    }

    private CurrentUser currentUser(String username) {
        return new CurrentUser("subject-1", username, username, Set.of("customer"), Set.of("cart:read"));
    }
}
