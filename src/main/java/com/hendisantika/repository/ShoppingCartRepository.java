package com.hendisantika.repository;

import com.hendisantika.model.ShoppingCart;
import com.hendisantika.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShoppingCartRepository extends JpaRepository<ShoppingCart, Long> {
    ShoppingCart findBySessionToken(String sessionToken);
    Optional<ShoppingCart> findByUser(User user);
}
