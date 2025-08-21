package com.subha.MiraBot.repository;

import com.subha.MiraBot.entity.FoodItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FoodItemRepository extends JpaRepository<FoodItem, Integer> {
    Optional<FoodItem> findByName(String name);
}
