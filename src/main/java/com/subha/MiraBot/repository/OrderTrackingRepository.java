package com.subha.MiraBot.repository;

import com.subha.MiraBot.entity.OrderTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderTrackingRepository extends JpaRepository<OrderTracking, Integer> {

    /**
     * Finds the tracking status by the order's ID.
     * Spring Data JPA automatically creates the query from the method name.
     */
    Optional<OrderTracking> findByOrderId(Integer orderId);

    @Query("SELECT COALESCE(MAX(ot.orderId), 0) FROM OrderTracking ot")
    Integer findMaxOrderId();
}
