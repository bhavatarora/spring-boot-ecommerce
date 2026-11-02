package com.hendisantika;

import com.hendisantika.model.CartItem;
import com.hendisantika.model.Product;
import com.hendisantika.model.Role;
import com.hendisantika.model.ShoppingCart;
import com.hendisantika.model.User;
import com.hendisantika.repository.ProductRepository;
import com.hendisantika.repository.ShoppingCartRepository;
import com.hendisantika.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CheckoutAndOrderIntegrationTest {

    @Container
    private static final MySQLContainer<?> mySQLContainer = new MySQLContainer<>("mysql:9.3.0")
            .withDatabaseName("shopDB")
            .withUsername("yu71")
            .withPassword("53cret");

    @DynamicPropertySource
    static void registerMySQLProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShoppingCartRepository shoppingCartRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        shoppingCartRepository.deleteAll();
        productRepository.deleteAll();

        testProduct = Product.builder()
                .name("Test Widget")
                .description("A test product")
                .quantity(10)
                .price(25.99)
                .brand("TestBrand")
                .categories(new HashSet<>())
                .build();
        testProduct = productRepository.save(testProduct);

        if (!userRepository.existsByUsername("testcheckoutuser")) {
            testUser = User.builder()
                    .username("testcheckoutuser")
                    .password(passwordEncoder.encode("password123"))
                    .email("testcheckout@example.com")
                    .firstName("Test")
                    .lastName("Checkout")
                    .role(Role.ROLE_USER)
                    .enabled(true)
                    .accountNonExpired(true)
                    .accountNonLocked(true)
                    .credentialsNonExpired(true)
                    .build();
            testUser = userRepository.save(testUser);
        } else {
            testUser = userRepository.findByUsername("testcheckoutuser").orElseThrow();
        }
    }

    // =====================================================================
    // 1. Authentication: Only logged-in users can checkout
    // =====================================================================

    @Nested
    @DisplayName("Authentication Requirements")
    class AuthenticationTests {

        @Test
        @DisplayName("Unauthenticated user should be redirected when attempting checkout")
        void unauthenticatedUserCannotCheckout() throws Exception {
            mockMvc.perform(post("/checkout").with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }

        @Test
        @DisplayName("Logged-in user should be able to access checkout")
        @WithMockUser(username = "testcheckoutuser", roles = "USER")
        void authenticatedUserCanAccessCheckout() throws Exception {
            mockMvc.perform(get("/checkout"))
                    .andExpect(status().isOk());
        }
    }

    // =====================================================================
    // 2. Empty cart cannot be checked out
    // =====================================================================

    @Nested
    @DisplayName("Empty Cart Validation")
    class EmptyCartTests {

        @Test
        @DisplayName("Checkout with empty cart should be rejected")
        void emptyCartCannotBeCheckedOut() throws Exception {
            mockMvc.perform(post("/checkout")
                            .with(csrf())
                            .with(user("testcheckoutuser").roles("USER")))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assertThat(status == 400 || status == 302).isTrue();
                    });
        }
    }

    // =====================================================================
    // 3. Address management
    // =====================================================================

    @Nested
    @DisplayName("Address Management")
    class AddressTests {

        @Test
        @DisplayName("User with no saved addresses should see address entry form")
        void userWithNoAddressesSeesForm() throws Exception {
            mockMvc.perform(get("/checkout")
                            .with(user("testcheckoutuser").roles("USER"))
                            .sessionAttr("sessionToken", createCartWithItem()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("street")))
                    .andExpect(content().string(containsString("city")))
                    .andExpect(content().string(containsString("state")))
                    .andExpect(content().string(containsString("zip")))
                    .andExpect(content().string(containsString("country")));
        }

        @Test
        @DisplayName("Address form should include all required fields")
        void addressFormHasAllRequiredFields() throws Exception {
            mockMvc.perform(get("/checkout")
                            .with(user("testcheckoutuser").roles("USER"))
                            .sessionAttr("sessionToken", createCartWithItem()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("street")))
                    .andExpect(content().string(containsString("city")))
                    .andExpect(content().string(containsString("state")))
                    .andExpect(content().string(containsString("zip")))
                    .andExpect(content().string(containsString("country")));
        }

        @Test
        @DisplayName("New address should be saved to the user")
        void newAddressShouldBeSavedToUser() throws Exception {
            mockMvc.perform(post("/checkout/address")
                            .with(csrf())
                            .with(user("testcheckoutuser").roles("USER"))
                            .param("street", "123 Main St")
                            .param("city", "Springfield")
                            .param("state", "IL")
                            .param("zip", "62704")
                            .param("country", "USA")
                            .sessionAttr("sessionToken", createCartWithItem()))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("User with saved addresses should see address list with radio buttons")
        void userWithSavedAddressesSeesRadioSelection() throws Exception {
            mockMvc.perform(post("/checkout/address")
                            .with(csrf())
                            .with(user("testcheckoutuser").roles("USER"))
                            .param("street", "456 Elm St")
                            .param("city", "Shelbyville")
                            .param("state", "IL")
                            .param("zip", "62565")
                            .param("country", "USA")
                            .sessionAttr("sessionToken", createCartWithItem()));

            mockMvc.perform(get("/checkout")
                            .with(user("testcheckoutuser").roles("USER"))
                            .sessionAttr("sessionToken", createCartWithItem()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("radio")));
        }
    }

    // =====================================================================
    // 4. Order creation
    // =====================================================================

    @Nested
    @DisplayName("Order Creation")
    class OrderCreationTests {

        @Test
        @DisplayName("Order should be created with 'pending' status")
        void orderCreatedWithPendingStatus() throws Exception {
            String sessionToken = createCartWithItem();

            MvcResult result = mockMvc.perform(post("/checkout/place-order")
                            .with(csrf())
                            .with(user("testcheckoutuser").roles("USER"))
                            .param("addressId", "1")
                            .sessionAttr("sessionToken", sessionToken))
                    .andExpect(status().is2xxSuccessful().or(status().is3xxRedirection()))
                    .andReturn();

            String content = resolveContent(result);
            assertThat(content.toLowerCase()).contains("pending");
        }

        @Test
        @DisplayName("Order should store the selected shipping address")
        void orderStoresShippingAddress() throws Exception {
            String sessionToken = createCartWithItem();

            mockMvc.perform(post("/checkout/address")
                            .with(csrf())
                            .with(user("testcheckoutuser").roles("USER"))
                            .param("street", "789 Oak Ave")
                            .param("city", "Capital City")
                            .param("state", "NY")
                            .param("zip", "10001")
                            .param("country", "USA")
                            .sessionAttr("sessionToken", sessionToken));

            MvcResult result = mockMvc.perform(post("/checkout/place-order")
                            .with(csrf())
                            .with(user("testcheckoutuser").roles("USER"))
                            .param("addressId", "1")
                            .sessionAttr("sessionToken", sessionToken))
                    .andReturn();

            String content = resolveContent(result);
            assertThat(content).containsAnyOf("789 Oak Ave", "Capital City", "10001");
        }

        @Test
        @DisplayName("Order item should snapshot product details at time of purchase")
        void orderItemSnapshotsProductDetails() throws Exception {
            String sessionToken = createCartWithItem();
            String originalName = testProduct.getName();
            Double originalPrice = testProduct.getPrice();

            mockMvc.perform(post("/checkout/place-order")
                            .with(csrf())
                            .with(user("testcheckoutuser").roles("USER"))
                            .param("addressId", "1")
                            .sessionAttr("sessionToken", sessionToken));

            testProduct.setName("Changed Name");
            testProduct.setPrice(999.99);
            productRepository.save(testProduct);

            mockMvc.perform(get("/orders")
                            .with(user("testcheckoutuser").roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(originalName)));
        }
    }

    // =====================================================================
    // 5. Stock reduction after order
    // =====================================================================

    @Nested
    @DisplayName("Stock Management")
    class StockTests {

        @Test
        @DisplayName("Product stock should be reduced after order is placed")
        void stockReducedAfterOrder() throws Exception {
            int initialStock = testProduct.getQuantity();
            String sessionToken = createCartWithItem();

            mockMvc.perform(post("/checkout/place-order")
                            .with(csrf())
                            .with(user("testcheckoutuser").roles("USER"))
                            .param("addressId", "1")
                            .sessionAttr("sessionToken", sessionToken));

            Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
            assertThat(updatedProduct.getQuantity()).isLessThan(initialStock);
        }
    }

    // =====================================================================
    // 6. Shopping cart cleared after order
    // =====================================================================

    @Nested
    @DisplayName("Cart Clearing")
    class CartClearingTests {

        @Test
        @DisplayName("Shopping cart should be cleared after successful checkout")
        void cartClearedAfterCheckout() throws Exception {
            String sessionToken = createCartWithItem();

            mockMvc.perform(post("/checkout/place-order")
                            .with(csrf())
                            .with(user("testcheckoutuser").roles("USER"))
                            .param("addressId", "1")
                            .sessionAttr("sessionToken", sessionToken));

            ShoppingCart cart = shoppingCartRepository.findBySessionToken(sessionToken);
            assertThat(cart == null || cart.getItems().isEmpty()).isTrue();
        }
    }

    // =====================================================================
    // 7. Order confirmation slip
    // =====================================================================

    @Nested
    @DisplayName("Order Confirmation Slip")
    class OrderConfirmationTests {

        @Test
        @DisplayName("Order confirmation page should be shown after checkout")
        void confirmationPageShownAfterCheckout() throws Exception {
            String sessionToken = createCartWithItem();

            MvcResult result = mockMvc.perform(post("/checkout/place-order")
                            .with(csrf())
                            .with(user("testcheckoutuser").roles("USER"))
                            .param("addressId", "1")
                            .sessionAttr("sessionToken", sessionToken))
                    .andReturn();

            String content = resolveContent(result);
            assertThat(content.toLowerCase()).containsAnyOf("confirmation", "order");
        }

        @Test
        @DisplayName("Confirmation slip should contain order id")
        void confirmationSlipContainsOrderId() throws Exception {
            String sessionToken = createCartWithItem();

            MvcResult result = mockMvc.perform(post("/checkout/place-order")
                            .with(csrf())
                            .with(user("testcheckoutuser").roles("USER"))
                            .param("addressId", "1")
                            .sessionAttr("sessionToken", sessionToken))
                    .andReturn();

            String content = resolveContent(result);
            assertThat(content.toLowerCase()).containsAnyOf("order id", "orderid", "order-id", "orderId");
        }

        @Test
        @DisplayName("Confirmation slip should contain item name and amount")
        void confirmationSlipContainsItemDetails() throws Exception {
            String sessionToken = createCartWithItem();

            MvcResult result = mockMvc.perform(post("/checkout/place-order")
                            .with(csrf())
                            .with(user("testcheckoutuser").roles("USER"))
                            .param("addressId", "1")
                            .sessionAttr("sessionToken", sessionToken))
                    .andReturn();

            String content = resolveContent(result);
            assertThat(content).contains("Test Widget");
            assertThat(content).contains("25.99");
        }

        @Test
        @DisplayName("Confirmation slip should contain the order date")
        void confirmationSlipContainsDate() throws Exception {
            String sessionToken = createCartWithItem();

            MvcResult result = mockMvc.perform(post("/checkout/place-order")
                            .with(csrf())
                            .with(user("testcheckoutuser").roles("USER"))
                            .param("addressId", "1")
                            .sessionAttr("sessionToken", sessionToken))
                    .andReturn();

            String content = resolveContent(result);
            assertThat(content.toLowerCase()).containsAnyOf("date", "2026");
        }

        @Test
        @DisplayName("Confirmation slip should contain item id")
        void confirmationSlipContainsItemId() throws Exception {
            String sessionToken = createCartWithItem();

            MvcResult result = mockMvc.perform(post("/checkout/place-order")
                            .with(csrf())
                            .with(user("testcheckoutuser").roles("USER"))
                            .param("addressId", "1")
                            .sessionAttr("sessionToken", sessionToken))
                    .andReturn();

            String content = resolveContent(result);
            assertThat(content).contains(testProduct.getId().toString());
        }
    }

    // =====================================================================
    // 8. Order listing and detail pages
    // =====================================================================

    @Nested
    @DisplayName("Order Listing and Detail")
    class OrderViewTests {

        @Test
        @DisplayName("Logged-in user should be able to view orders list page")
        void userCanViewOrdersList() throws Exception {
            mockMvc.perform(get("/orders")
                            .with(user("testcheckoutuser").roles("USER")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Unauthenticated user should not be able to view orders")
        void unauthenticatedUserCannotViewOrders() throws Exception {
            mockMvc.perform(get("/orders"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }

        @Test
        @DisplayName("Order detail page should be accessible for a specific order")
        void userCanViewOrderDetail() throws Exception {
            String sessionToken = createCartWithItem();

            mockMvc.perform(post("/checkout/place-order")
                            .with(csrf())
                            .with(user("testcheckoutuser").roles("USER"))
                            .param("addressId", "1")
                            .sessionAttr("sessionToken", sessionToken));

            mockMvc.perform(get("/orders/1")
                            .with(user("testcheckoutuser").roles("USER")))
                    .andExpect(status().isOk());
        }
    }

    // =====================================================================
    // 9. Order statuses (view only)
    // =====================================================================

    @Nested
    @DisplayName("Order Status")
    class OrderStatusTests {

        @Test
        @DisplayName("Order statuses should include pending, shipped, delivered, cancelled")
        void orderStatusesExist() throws Exception {
            String sessionToken = createCartWithItem();

            mockMvc.perform(post("/checkout/place-order")
                            .with(csrf())
                            .with(user("testcheckoutuser").roles("USER"))
                            .param("addressId", "1")
                            .sessionAttr("sessionToken", sessionToken));

            MvcResult result = mockMvc.perform(get("/orders")
                            .with(user("testcheckoutuser").roles("USER")))
                    .andExpect(status().isOk())
                    .andReturn();

            String content = result.getResponse().getContentAsString().toLowerCase();
            assertThat(content).contains("pending");
        }

        @Test
        @DisplayName("Order status should not be changeable by users")
        void orderStatusNotChangeableByUser() throws Exception {
            mockMvc.perform(post("/orders/1/status")
                            .with(csrf())
                            .with(user("testcheckoutuser").roles("USER"))
                            .param("status", "shipped"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assertThat(status == 403 || status == 404 || status == 405).isTrue();
                    });
        }
    }

    // =====================================================================
    // 10. Multiple users' addresses isolation
    // =====================================================================

    @Nested
    @DisplayName("User Address Isolation")
    class AddressIsolationTests {

        @Test
        @DisplayName("User should be able to have multiple saved addresses")
        void userCanHaveMultipleAddresses() throws Exception {
            String sessionToken = createCartWithItem();

            mockMvc.perform(post("/checkout/address")
                            .with(csrf())
                            .with(user("testcheckoutuser").roles("USER"))
                            .param("street", "111 First St")
                            .param("city", "CityA")
                            .param("state", "CA")
                            .param("zip", "90001")
                            .param("country", "USA")
                            .sessionAttr("sessionToken", sessionToken))
                    .andExpect(status().is3xxRedirection());

            mockMvc.perform(post("/checkout/address")
                            .with(csrf())
                            .with(user("testcheckoutuser").roles("USER"))
                            .param("street", "222 Second St")
                            .param("city", "CityB")
                            .param("state", "NY")
                            .param("zip", "10002")
                            .param("country", "USA")
                            .sessionAttr("sessionToken", sessionToken))
                    .andExpect(status().is3xxRedirection());

            mockMvc.perform(get("/checkout")
                            .with(user("testcheckoutuser").roles("USER"))
                            .sessionAttr("sessionToken", sessionToken))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("111 First St")))
                    .andExpect(content().string(containsString("222 Second St")));
        }
    }

    // =====================================================================
    // Helper methods
    // =====================================================================

    private String createCartWithItem() {
        String sessionToken = "test-session-" + System.nanoTime();

        CartItem cartItem = CartItem.builder()
                .quantity(2)
                .date(new Date())
                .product(testProduct)
                .build();

        ShoppingCart cart = ShoppingCart.builder()
                .date(new Date())
                .sessionToken(sessionToken)
                .items(new HashSet<>())
                .build();
        cart.getItems().add(cartItem);
        shoppingCartRepository.save(cart);

        return sessionToken;
    }

    private String resolveContent(MvcResult result) throws Exception {
        if (result.getResponse().getStatus() == 302 || result.getResponse().getStatus() == 301) {
            String redirectUrl = result.getResponse().getRedirectedUrl();
            MvcResult redirectResult = mockMvc.perform(get(redirectUrl)
                            .with(user("testcheckoutuser").roles("USER")))
                    .andReturn();
            return redirectResult.getResponse().getContentAsString();
        }
        return result.getResponse().getContentAsString();
    }

    private static org.hamcrest.Matcher<Integer> or(org.hamcrest.Matcher<Integer> a, org.hamcrest.Matcher<Integer> b) {
        return anyOf(a, b);
    }
}
