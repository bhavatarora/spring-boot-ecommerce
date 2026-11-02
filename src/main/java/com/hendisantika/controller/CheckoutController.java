package com.hendisantika.controller;

import com.hendisantika.model.*;
import com.hendisantika.repository.UserRepository;
import com.hendisantika.service.AddressService;
import com.hendisantika.service.OrderService;
import com.hendisantika.service.ShoppingCartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/checkout")
@RequiredArgsConstructor
@Slf4j
public class CheckoutController {

    private final ShoppingCartService shoppingCartService;
    private final AddressService addressService;
    private final OrderService orderService;
    private final UserRepository userRepository;

    @GetMapping
    public String startCheckout(@AuthenticationPrincipal UserDetails userDetails,
                                HttpServletRequest request,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            redirectAttributes.addFlashAttribute("error", "Please login to checkout");
            return "redirect:/login";
        }

        String sessionToken = (String) request.getSession(false).getAttribute("sessionToken");
        ShoppingCart cart = shoppingCartService.getShoppingCartBySessionToken(sessionToken);

        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Your cart is empty");
            return "redirect:/shoppingCart";
        }

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Address> addresses = addressService.findByUser(user);

        model.addAttribute("addresses", addresses);
        model.addAttribute("cart", cart);
        model.addAttribute("user", user);

        if (addresses.isEmpty()) {
            model.addAttribute("address", new Address());
            return "checkout-address";
        } else {
            return "checkout-select-address";
        }
    }

    @PostMapping("/add-address")
    public String addAddress(@AuthenticationPrincipal UserDetails userDetails,
                             @Valid @ModelAttribute("address") Address address,
                             BindingResult result,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        if (result.hasErrors()) {
            return "checkout-address";
        }

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        address.setUser(user);
        addressService.save(address);

        redirectAttributes.addFlashAttribute("message", "Address saved successfully");
        return "redirect:/checkout/confirm?addressId=" + address.getId();
    }

    @GetMapping("/use-address/{addressId}")
    public String useExistingAddress(@PathVariable Long addressId,
                                      @AuthenticationPrincipal UserDetails userDetails,
                                      RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        Address address = addressService.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!address.getUser().getId().equals(user.getId())) {
            redirectAttributes.addFlashAttribute("error", "Unauthorized access");
            return "redirect:/checkout";
        }

        return "redirect:/checkout/confirm?addressId=" + addressId;
    }

    @GetMapping("/confirm")
    public String confirmOrder(@RequestParam Long addressId,
                                @AuthenticationPrincipal UserDetails userDetails,
                                HttpServletRequest request,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Address address = addressService.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        String sessionToken = (String) request.getSession(false).getAttribute("sessionToken");
        ShoppingCart cart = shoppingCartService.getShoppingCartBySessionToken(sessionToken);

        if (cart == null || cart.getItems().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Your cart is empty");
            return "redirect:/shoppingCart";
        }

        model.addAttribute("cart", cart);
        model.addAttribute("address", address);
        model.addAttribute("user", user);

        return "checkout-confirm";
    }

    @PostMapping("/place-order")
    public String placeOrder(@RequestParam Long addressId,
                              @AuthenticationPrincipal UserDetails userDetails,
                              HttpServletRequest request,
                              RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        try {
            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Address address = addressService.findById(addressId)
                    .orElseThrow(() -> new RuntimeException("Address not found"));

            String sessionToken = (String) request.getSession(false).getAttribute("sessionToken");
            ShoppingCart cart = shoppingCartService.getShoppingCartBySessionToken(sessionToken);

            if (cart == null || cart.getItems().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Your cart is empty");
                return "redirect:/shoppingCart";
            }

            Order order = orderService.createOrder(user, cart, address);

            shoppingCartService.clearShoppingCart(sessionToken);
            request.getSession(false).removeAttribute("sessionToken");

            redirectAttributes.addFlashAttribute("message", "Order placed successfully");
            return "redirect:/checkout/order-confirmation/" + order.getId();

        } catch (IllegalStateException e) {
            log.error("Error placing order: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/checkout/confirm?addressId=" + addressId;
        } catch (Exception e) {
            log.error("Unexpected error placing order", e);
            redirectAttributes.addFlashAttribute("error", "An error occurred while placing your order");
            return "redirect:/checkout";
        }
    }

    @GetMapping("/order-confirmation/{orderId}")
    public String orderConfirmation(@PathVariable Long orderId,
                                     @AuthenticationPrincipal UserDetails userDetails,
                                     Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Order order = orderService.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getUser().getId().equals(user.getId())) {
            return "redirect:/";
        }

        model.addAttribute("order", order);
        return "order-confirmation";
    }

    @GetMapping("/new-address")
    public String showNewAddressForm(@AuthenticationPrincipal UserDetails userDetails,
                                      HttpServletRequest request,
                                      Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        String sessionToken = (String) request.getSession(false).getAttribute("sessionToken");
        ShoppingCart cart = shoppingCartService.getShoppingCartBySessionToken(sessionToken);

        model.addAttribute("address", new Address());
        model.addAttribute("cart", cart);

        return "checkout-address";
    }
}
