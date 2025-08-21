package com.subha.MiraBot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "order_tracking")
@Getter
@Setter
public class OrderTracking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tracking_id")
    private Integer trackingId;

    @Column(name = "order_id", unique = true)
    private Integer orderId;

    private String status;
}