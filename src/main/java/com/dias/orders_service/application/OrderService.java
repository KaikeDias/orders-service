package com.dias.orders_service.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import com.dias.orders_service.api.CreateOrderRequest;
import com.dias.orders_service.api.OrderResponse;
import com.dias.orders_service.catalog.CatalogClient;
import com.dias.orders_service.domain.entity.Order;
import com.dias.orders_service.domain.entity.OrderItem;
import com.dias.orders_service.domain.entity.OutboxMessage;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OrderService {
    private final CatalogClient catalog;
    private final EntityManager entityManager;
    private final JsonMapper mapper;
    private final TransactionTemplate transaction;

    public OrderService(CatalogClient catalog, EntityManager entityManager, JsonMapper mapper, PlatformTransactionManager transactionManager) {
        this.catalog = catalog;
        this.entityManager = entityManager;
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public OrderResponse create(CreateOrderRequest request) {
        var products = new HashSet<UUID>();
        for (var item : request.items()) {
            if (!products.add(item.productId())) {
                throw new OrderCreationException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_REQUEST", "Duplicate product");
            }
        }
        List<OrderItem> items = new ArrayList<>();
        String currency = null;
        for (var item : request.items()) {
            var product = catalog.getProduct(item.productId());
            if (!product.active()) {
                throw new OrderCreationException(HttpStatus.UNPROCESSABLE_ENTITY, "PRODUCT_INACTIVE", "Product inactive");
            }
            if (currency != null && !currency.equals(product.currency())) {
                throw new OrderCreationException(HttpStatus.UNPROCESSABLE_ENTITY, "MIXED_CURRENCIES", "Products must use the same currency");
            }
            currency = product.currency();
            items.add(new OrderItem(product.productId(), product.name(), product.price(), item.quantity(), currency));
        }
        Order order = Order.create(UUID.randomUUID(), currency, items);
        UUID messageId = UUID.randomUUID();
        var command = new ReserveStock(messageId, "ReserveStock", 1, order.id(), order.id(), null, Instant.now(),
                new Payload(request.items()));
        String payload = mapper.writeValueAsString(command);
        OutboxMessage outbox = OutboxMessage.create(messageId, "inventory.commands", order.id().toString(), payload);
        return transaction.execute(status -> {
            entityManager.persist(order);
            entityManager.flush();
            entityManager.persist(outbox);
            entityManager.flush();
            return OrderResponse.from(order);
        });
    }

    public record ReserveStock(UUID messageId, String type, int version, UUID orderId, UUID correlationId,
                               UUID causationId, Instant occurredAt, Payload payload) {}
    public record Payload(List<CreateOrderRequest.Item> items) {}
}
