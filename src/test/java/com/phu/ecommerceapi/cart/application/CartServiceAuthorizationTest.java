package com.phu.ecommerceapi.cart.application;

import com.phu.ecommerceapi.catalog.application.CartProductLookupPort;
import com.phu.ecommerceapi.customer.application.CustomerIdentityLookupPort;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.inventory.application.InventoryReservationService;
import com.phu.ecommerceapi.shared.domain.Money;
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
    private CartPersistencePort cartPersistencePort;

    @Mock
    private CartProductLookupPort cartProductLookupPort;

    @Mock
    private CustomerIdentityLookupPort customerIdentityLookupPort;

    @Mock
    private InventoryReservationService inventoryReservationService;

    @Mock
    private CartCheckoutStatusPort cartCheckoutStatusPort;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(
                cartPersistencePort,
                cartProductLookupPort,
                customerIdentityLookupPort,
                inventoryReservationService,
                cartCheckoutStatusPort
        );
    }

    @Test
    void ownerCanReadCartItems() {
        CartSnapshot cart = cartOwnedBy("subject-1");
        when(cartPersistencePort.findWithItemsById(10L)).thenReturn(Optional.of(cart));

        assertThat(cartService.getCartItems(10L, currentUser("customer@example.com")).isEmpty())
                .isTrue();
    }

    @Test
    void crossCustomerCartReadIsDenied() {
        CartSnapshot cart = cartOwnedBy("owner-subject");
        when(cartPersistencePort.findWithItemsById(10L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.getCartItems(10L, currentUser("attacker@example.com")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Cart does not belong to current user");
    }

    @Test
    void matchingUsernameAndEmailDoNotGrantOwnershipWhenSubjectDiffers() {
        CartSnapshot cart = cartOwnedBy("owner-subject");
        when(cartPersistencePort.findWithItemsById(10L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.getCartItems(10L, currentUser("customer@example.com")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Cart does not belong to current user");
    }

    private CartSnapshot cartOwnedBy(String identitySubject) {
        return new CartSnapshot(
                10L,
                100L,
                identitySubject,
                Money.of("0.00", "USD"),
                "USD",
                java.util.List.of()
        );
    }

    private CurrentUser currentUser(String username) {
        return new CurrentUser("subject-1", username, username, Set.of("customer"), Set.of("cart:read"));
    }
}
