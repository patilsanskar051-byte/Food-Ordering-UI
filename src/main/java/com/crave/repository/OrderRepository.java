package com.crave.repository;

import com.crave.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for Order entity database operations.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
}
