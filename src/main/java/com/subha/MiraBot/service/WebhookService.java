package com.subha.MiraBot.service;

import com.subha.MiraBot.dto.WebhookRequest;
import com.subha.MiraBot.dto.WebhookResponse;
import com.subha.MiraBot.entity.FoodItem;
import com.subha.MiraBot.entity.Order;
import com.subha.MiraBot.entity.OrderItem;
import com.subha.MiraBot.entity.OrderTracking;
import com.subha.MiraBot.exception.ItemNotFoundException;
import com.subha.MiraBot.repository.FoodItemRepository;
import com.subha.MiraBot.repository.OrderRepository;
import com.subha.MiraBot.repository.OrderItemRepository;
import com.subha.MiraBot.repository.OrderTrackingRepository;
import com.subha.MiraBot.util.GenericHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
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

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    
    private static final String INTENT_ORDER_ADD = "order.add - context: ongoing-order";
    private static final String INTENT_ORDER_REMOVE = "order.remove - context: ongoing-order";
    private static final String INTENT_ORDER_COMPLETE = "order.complete - context: ongoing-order";
    private static final String INTENT_TRACK_ORDER = "track.order - context: ongoing-tracking";

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

        if (intent == null || parameters == null || sessionId.isEmpty()) {
            log.warn("Received a request with missing intent, parameters, or session ID.");
            return new WebhookResponse("Sorry, there was a problem with your request. Please try again.");
        }

        return switch (intent) {
            case INTENT_ORDER_ADD -> addToOrder(parameters, sessionId);
            case INTENT_ORDER_REMOVE -> removeFromOrder(parameters, sessionId);
            case INTENT_ORDER_COMPLETE -> completeOrder(sessionId);
            case INTENT_TRACK_ORDER -> trackOrder(parameters);
            default -> new WebhookResponse("Sorry, I didn't understand that. Please try again.");
        };
    }

    @Transactional
    private WebhookResponse completeOrder(String sessionId) {
        if (!inProgressOrders.containsKey(sessionId)) {
            return new WebhookResponse("I'm having trouble finding your order. Sorry! Can you place a new order please?");
        }
        Map<String, Integer> orderMap = inProgressOrders.get(sessionId);
        if (orderMap.isEmpty()) {
            return new WebhookResponse("Your order is empty. Please add some items from the menu.");
        }
        try {
            Order savedOrder = saveOrderToDb(orderMap);
            double orderTotal = getTotalOrderPriceFromDb(savedOrder.getOrderId());
            String fulfillmentText = String.format(
                    "Awesome. We have placed your order. Here is your order id # %d. Your order total is ₹%.2f which you can pay at the time of delivery!",
                    savedOrder.getOrderId(), orderTotal);
            inProgressOrders.remove(sessionId);
            return new WebhookResponse(fulfillmentText);
        } catch (ItemNotFoundException e) {
            log.warn("Order completion failed for session {}: {}", sessionId, e.getMessage());
            return new WebhookResponse(String.format("Sorry, an item in your order is not on the menu: %s. Please start a new order.", e.getMessage()));
        } catch (DataAccessException e) {
            log.error("Database error for session {}: {}", sessionId, e.getMessage(), e);
            return new WebhookResponse("Sorry, I couldn't process your order due to a database error. Please try again later.");
        } catch (Exception e) {
            log.error("Unexpected error for session {}: {}", sessionId, e.getMessage(), e);
            return new WebhookResponse("Sorry, an unexpected error occurred. Please try again.");
        }
    }

    @Transactional(rollbackFor = ItemNotFoundException.class)
    public Order saveOrderToDb(Map<String, Integer> orderMap) throws ItemNotFoundException {
        Order order = new Order();
        for (Map.Entry<String, Integer> entry : orderMap.entrySet()) {
            FoodItem foodItem = foodItemRepository.findByName(entry.getKey())
                    .orElseThrow(() -> new ItemNotFoundException(entry.getKey()));
            OrderItem orderItem = new OrderItem();
            orderItem.setFoodItem(foodItem);
            orderItem.setQuantity(entry.getValue());
            orderItem.setOrder(order);
            order.getOrderItems().add(orderItem);
        }
        // --- REFINEMENT 3: Using save() is sufficient here ---
        // The transaction ensures everything is saved together.
        Order savedOrder = orderRepository.save(order);
        OrderTracking tracking = new OrderTracking();
        tracking.setOrderId(savedOrder.getOrderId());
        tracking.setStatus("in progress");
        orderTrackingRepository.save(tracking);
        return savedOrder;
    }

    private double getTotalOrderPriceFromDb(int orderId) {
        BigDecimal total = orderItemRepository.findTotalOrderPriceByOrderId(orderId);
        return total == null ? 0.0 : total.doubleValue();
    }

    private WebhookResponse trackOrder(Map<String, Object> parameters) {
        Object orderIdObj = parameters.get("order_id");
        if (!(orderIdObj instanceof Number)) {
            log.warn("Invalid or missing 'order_id' in trackOrder request: {}", orderIdObj);
            return new WebhookResponse("Please provide a valid numeric order ID.");
        }
        int orderId = ((Number) orderIdObj).intValue();
        String status = orderTrackingRepository.findByOrderId(orderId)
                .map(OrderTracking::getStatus)
                .orElse(null);
        String fulfillmentText = (status != null)
                ? String.format("The order status for order id: %d is: %s", orderId, status.toUpperCase())
                : String.format("No order found with order id: %d", orderId);
        return new WebhookResponse(fulfillmentText);
    }

    private WebhookResponse addToOrder(Map<String, Object> parameters, String sessionId) {
        // --- REFINEMENT 2: Safer parameter casting ---
        if (!(parameters.get("food-item") instanceof List<?> foodItems) || !(parameters.get("number") instanceof List<?> numbers)) {
            log.warn("Invalid parameters for addToOrder: food-item or number is not a List.");
            return new WebhookResponse("Sorry, I didn't understand the items and quantities. Please try again.");
        }
        if (foodItems.size() != numbers.size()) {
            return new WebhookResponse("Sorry, I didn't understand. Please specify quantities for each food item.");
        }
        
        Map<String, Integer> currentOrder = inProgressOrders.computeIfAbsent(sessionId, k -> new HashMap<>());
        for (int i = 0; i < foodItems.size(); i++) {
            if (foodItems.get(i) instanceof String name && numbers.get(i) instanceof Number num) {
                currentOrder.put(name, num.intValue());
            }
        }
        
        String orderStr = GenericHelper.getStrFromFoodMap(currentOrder);
        String fulfillmentText = String.format("So far you have: %s. Do you need anything else?", orderStr);
        return new WebhookResponse(fulfillmentText);
    }

    private WebhookResponse removeFromOrder(Map<String, Object> parameters, String sessionId) {
        if (!inProgressOrders.containsKey(sessionId)) {
            return new WebhookResponse("I'm having trouble finding your order. Sorry! Can you place a new order please?");
        }
        
        if (!(parameters.get("food-item") instanceof List<?> foodItems)) {
            log.warn("Invalid parameters for removeFromOrder: food-item is not a List.");
            return new WebhookResponse("Sorry, I didn't understand which items to remove.");
        }
        
        Map<String, Integer> currentOrder = inProgressOrders.get(sessionId);
        List<String> removedItems = new ArrayList<>();
        List<String> noSuchItems = new ArrayList<>();

        for (Object item : foodItems) {
            if (item instanceof String itemName) {
                if (currentOrder.containsKey(itemName)) {
                    removedItems.add(itemName);
                    currentOrder.remove(itemName);
                } else {
                    noSuchItems.add(itemName);
                }
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