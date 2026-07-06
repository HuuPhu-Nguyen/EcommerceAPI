package com.phu.ecommerceapi.catalog.api;

import com.phu.ecommerceapi.Product.ProductModel;
import com.phu.ecommerceapi.Product.ProductRepo;
import com.phu.ecommerceapi.cart.infrastructure.CartRepo;
import com.phu.ecommerceapi.inventory.infrastructure.InventoryRepository;
import com.phu.ecommerceapi.order.infrastructure.CustomerOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductCatalogBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepo productRepo;

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Autowired
    private CartRepo cartRepo;

    @Autowired
    private InventoryRepository inventoryRepository;

    @BeforeEach
    void resetProducts() {
        orderRepository.deleteAll();
        cartRepo.deleteAll();
        inventoryRepository.deleteAll();
        productRepo.deleteAll();
    }

    @Test
    void catalogListReturnsPagedDtoWithoutPersistenceInternals() throws Exception {
        productRepo.save(product("Active Keyboard", true));
        productRepo.save(product("Inactive Monitor", false));

        mockMvc.perform(get("/products/getAll")
                        .param("page", "0")
                        .param("size", "10")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Active Keyboard"))
                .andExpect(jsonPath("$.content[0].active").doesNotExist())
                .andExpect(jsonPath("$.content[0].cartItems").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void catalogSearchFiltersActiveProductsByName() throws Exception {
        productRepo.save(product("Alpha Phone", true));
        productRepo.save(product("Beta Chair", true));
        productRepo.save(product("Inactive Phone Case", false));

        mockMvc.perform(get("/products")
                        .param("search", "phone")
                        .param("page", "0")
                        .param("size", "10")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Alpha Phone"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void catalogPaginationReturnsMetadata() throws Exception {
        productRepo.save(product("A Product", true));
        productRepo.save(product("B Product", true));

        mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("size", "1")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void inactiveProductDetailIsHidden() throws Exception {
        ProductModel inactiveProduct = productRepo.save(product("Inactive Product", false));

        mockMvc.perform(get("/products/getById")
                        .param("id", Long.toString(inactiveProduct.getProductId()))
                        .with(jwt()))
                .andExpect(status().isNotFound());
    }

    private ProductModel product(String name, boolean active) {
        return ProductModel.builder()
                .name(name)
                .price(10.00)
                .stock(5)
                .active(active)
                .build();
    }
}
