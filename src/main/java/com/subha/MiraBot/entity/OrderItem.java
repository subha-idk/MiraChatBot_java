package com.subha.MiraBot.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "order_items")
@Getter
@Setter
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Integer itemId;

    private int quantity;

    @ManyToOne
    @JoinColumn(name = "food_item_id") // Foreign key column to link to food_items table
    private FoodItem foodItem;

    @ManyToOne
    @JoinColumn(name = "order_id") // Foreign key column to link to orders table
    private Order order;
}