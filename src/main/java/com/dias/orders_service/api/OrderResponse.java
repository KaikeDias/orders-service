package com.dias.orders_service.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.dias.orders_service.domain.entity.Order;
import com.dias.orders_service.domain.enums.OrderStatus;

public record OrderResponse(UUID id, OrderStatus status, BigDecimal total, String currency, Instant createdAt) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.id(), order.status(), order.total(), order.currency(), order.createdAt());
    }
}
