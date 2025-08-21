package com.subha.MiraBot.service;

import com.subha.MiraBot.dto.WebhookRequest;
import com.subha.MiraBot.dto.WebhookResponse;
import com.subha.MiraBot.entity.FoodItem;
import com.subha.MiraBot.entity.Order; // <-- Important import
import com.subha.MiraBot.entity.OrderItem;
import com.subha.MiraBot.entity.OrderTracking;
import com.subha.MiraBot.repository.FoodItemRepository;
import com.subha.MiraBot.repository.OrderRepository; // <-- Important import
import com.subha.MiraBot.repository.OrderItemRepository;
import com.subha.MiraBot.repository.OrderTrackingRepository;
import com.subha.MiraBot.util.GenericHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class WebhookService {

    // Inject all required repositories
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private FoodItemRepository foodItemRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private OrderTrackingRepository orderTrackingRepository;

    private final Map<String, Map<String, Integer>> inProgressOrders = new ConcurrentHashMap<>();

    public WebhookResponse handleRequest(WebhookRequest request) {
        String intent = request.getQueryResult().getIntent().getDisplayName();
        Map<String, Object> parameters = request.getQueryResult().getParameters();
        String sessionId = GenericHelper.extractSessionId(request.getQueryResult().getOutputContexts());

        return switch (intent) {
            case "order.add - context: ongoing-order" -> addToOrder(parameters, sessionId);
            case "order.remove - context: ongoing-order" -> removeFromOrder(parameters, sessionId);
            case "order.complete - context: ongoing-order" -> completeOrder(sessionId);
            case "track.order - context: ongoing-tracking" -> trackOrder(parameters);
            default -> new WebhookResponse("Sorry, I didn't understand that. Please try again.");
        };
    }

    @Transactional
    private WebhookResponse completeOrder(String sessionId) {
        if (!inProgressOrders.containsKey(sessionId)) {
            return new WebhookResponse("I'm having trouble finding your order. Sorry! Can you place a new order please?");
        }

        Map<String, Integer> orderMap = inProgressOrders.get(sessionId);
        try {
            // This now returns the parent Order object
            Order savedOrder = saveOrderToDb(orderMap);

            // This now calls the new, precise repository method
            double orderTotal = getTotalOrderPriceFromDb(savedOrder.getOrderId());

            String fulfillmentText = String.format(
                    "Awesome. We have placed your order. Here is your order id # %d. Your order total is %.2f which you can pay at the time of delivery!",
                    savedOrder.getOrderId(),
                    orderTotal);

            inProgressOrders.remove(sessionId);
            return new WebhookResponse(fulfillmentText);
        } catch (Exception e) {
            e.printStackTrace();
            return new WebhookResponse("Sorry, I couldn't process your order due to a backend error. Please place a new order again.");
        }
    }

    /**
     * REWRITTEN LOGIC: Follows the proper object-oriented approach.
     */
    @Transactional
    public Order saveOrderToDb(Map<String, Integer> orderMap) {
        // 1. Create a new parent Order object and save it first.
        // This generates the primary key (order_id).
        Order order = orderRepository.save(new Order());

        // 2. Create a list to hold the child OrderItem objects
        List<OrderItem> orderItems = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : orderMap.entrySet()) {
            String foodItemName = entry.getKey();
            int quantity = entry.getValue();

            // 3. Find the corresponding FoodItem from the database
            FoodItem foodItem = foodItemRepository.findByName(foodItemName)
                    .orElseThrow(() -> new RuntimeException("Food item not found: " + foodItemName));

            // 4. Create a new OrderItem
            OrderItem orderItem = new OrderItem();
            orderItem.setFoodItem(foodItem);
            orderItem.setQuantity(quantity);
            // 5. THIS IS THE KEY: Link the child OrderItem back to the parent Order
            orderItem.setOrder(order);

            orderItems.add(orderItem);
        }

        // 6. Save all the child OrderItem objects to the database
        orderItemRepository.saveAll(orderItems);

        // 7. Create and save the tracking information for the new order
        OrderTracking tracking = new OrderTracking();
        tracking.setOrderId(order.getOrderId());
        tracking.setStatus("in progress");
        orderTrackingRepository.save(tracking);

        // 8. Return the parent Order object
        return order;
    }

    /**
     * CORRECTED LOGIC: Calls the new repository method.
     */
    private double getTotalOrderPriceFromDb(int orderId) {
        BigDecimal total = orderItemRepository.findTotalOrderPriceByOrderId(orderId);
        // Handle case where an order might be empty or not found, though unlikely here
        return total == null ? 0.0 : total.doubleValue();
    }

    private WebhookResponse trackOrder(Map<String, Object> parameters) {
        int orderId = ((Number) parameters.get("order_id")).intValue();

        // The query in OrderTrackingRepository was slightly wrong, this is the correct method call
        String status = orderTrackingRepository.findById(orderId) // Should find by its own PK
                .map(OrderTracking::getStatus)
                .orElse(null);

        String fulfillmentText = (status != null)
                ? String.format("The order status for order id: %d is: %s", orderId, status)
                : String.format("No order found with order id: %d", orderId);
        return new WebhookResponse(fulfillmentText);
    }

    // addToOrder and removeFromOrder methods remain unchanged...
    private WebhookResponse addToOrder(Map<String, Object> parameters, String sessionId) {
        @SuppressWarnings("unchecked")
        List<String> foodItems = (List<String>) parameters.get("food-item");
        @SuppressWarnings("unchecked")
        List<Double> quantitiesDouble = (List<Double>) parameters.get("number");
        List<Integer> quantities = quantitiesDouble.stream().map(Double::intValue).collect(Collectors.toList());


        if (foodItems.size() != quantities.size()) {
            return new WebhookResponse("Sorry I didn't understand. Can you please specify food items and quantities clearly?");
        }

        Map<String, Integer> newFoodDict = new HashMap<>();
        for (int i = 0; i < foodItems.size(); i++) {
            newFoodDict.put(foodItems.get(i), quantities.get(i));
        }

        Map<String, Integer> currentOrder = inProgressOrders.computeIfAbsent(sessionId, k -> new HashMap<>());
        currentOrder.putAll(newFoodDict);

        String orderStr = GenericHelper.getStrFromFoodMap(currentOrder);
        String fulfillmentText = String.format("So far you have: %s. Do you need anything else?", orderStr);

        return new WebhookResponse(fulfillmentText);
    }

    private WebhookResponse removeFromOrder(Map<String, Object> parameters, String sessionId) {
        if (!inProgressOrders.containsKey(sessionId)) {
            return new WebhookResponse("I'm having a trouble finding your order. Sorry! Can you place a new order please?");
        }

        @SuppressWarnings("unchecked")
        List<String> foodItems = (List<String>) parameters.get("food-item");
        Map<String, Integer> currentOrder = inProgressOrders.get(sessionId);

        List<String> removedItems = new ArrayList<>();
        List<String> noSuchItems = new ArrayList<>();

        for (String item : foodItems) {
            if (currentOrder.containsKey(item)) {
                removedItems.add(item);
                currentOrder.remove(item);
            } else {
                noSuchItems.add(item);
            }
        }

        StringBuilder fulfillmentText = new StringBuilder();
        if (!removedItems.isEmpty()) {
            fulfillmentText.append("Removed ").append(String.join(", ", removedItems)).append(" from your order! ");
        }
        if (!noSuchItems.isEmpty()) {
            fulfillmentText.append("Your current order does not have ").append(String.join(", ", noSuchItems)).append(". ");
        }
        if (currentOrder.isEmpty()) {
            fulfillmentText.append("Your order is empty!");
        } else {
            fulfillmentText.append("Here is what is left in your order: ").append(GenericHelper.getStrFromFoodMap(currentOrder));
        }

        return new WebhookResponse(fulfillmentText.toString());
    }
}