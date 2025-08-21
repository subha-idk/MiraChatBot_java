package com.subha.MiraBot.repository;

import com.subha.MiraBot.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {

    // NEW METHOD: Calculates the total price by joining items with food prices.
    @Query("SELECT SUM(oi.quantity * fi.price) FROM OrderItem oi JOIN oi.foodItem fi WHERE oi.order.orderId = :orderId")
    BigDecimal findTotalOrderPriceByOrderId(@Param("orderId") Integer orderId);
}
