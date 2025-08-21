package com.subha.MiraBot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "food_items")
@Getter
@Setter
public class FoodItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Integer itemId;

    private String name;

    private BigDecimal price;
}
