package com.hendisantika.service;

import com.hendisantika.model.*;
import com.hendisantika.repository.OrderRepository;
import com.hendisantika.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public List<Order> findByUser(User user) {
        return orderRepository.findByUserOrderByOrderDateDesc(user);
    }

    public List<Order> findByUserId(Long userId) {
        return orderRepository.findByUserIdOrderByOrderDateDesc(userId);
    }

    public Optional<Order> findById(Long id) {
        return orderRepository.findById(id);
    }

    @Transactional
    public Order createOrder(User user, ShoppingCart cart, Address shippingAddress) {
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cannot create order with empty cart");
        }

        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .shippingStreet(shippingAddress.getStreet())
                .shippingCity(shippingAddress.getCity())
                .shippingState(shippingAddress.getState())
                .shippingZip(shippingAddress.getZip())
                .shippingCountry(shippingAddress.getCountry())
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            
            if (product.getQuantity() < cartItem.getQuantity()) {
                throw new IllegalStateException("Insufficient stock for product: " + product.getName());
            }

            BigDecimal itemPrice = BigDecimal.valueOf(product.getPrice());
            if (product.getDiscount() != null && product.getDiscount().getDiscount() > 0) {
                double discountAmount = product.getDiscount().getDiscount();
                itemPrice = itemPrice.subtract(BigDecimal.valueOf(discountAmount));
            }

            OrderItem orderItem = OrderItem.builder()
                    .itemId(product.getId())
                    .itemName(product.getName())
                    .quantity(cartItem.getQuantity())
                    .price(itemPrice)
                    .build();

            order.addOrderItem(orderItem);
            totalAmount = totalAmount.add(orderItem.getSubtotal());

            product.setQuantity(product.getQuantity() - cartItem.getQuantity());
            productRepository.save(product);
        }

        order.setTotalAmount(totalAmount);
        return orderRepository.save(order);
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }
}
