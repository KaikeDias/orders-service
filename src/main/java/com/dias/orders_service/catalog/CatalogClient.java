package com.dias.orders_service.catalog;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.util.UUID;
import com.dias.orders_service.application.OrderCreationException;
import com.dias.orders_service.domain.entity.OrderItem;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CatalogClient {
    private final RestClient client;
    private final JsonMapper mapper;

    public CatalogClient(CatalogProperties properties, JsonMapper mapper) {
        var http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.readTimeout());
        client = RestClient.builder().baseUrl(properties.baseUrl().toString()).requestFactory(factory).build();
        this.mapper = mapper.rebuild().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    }

    public ProductSnapshot getProduct(UUID productId) {
        try {
            return client.get().uri("/products/{id}", productId).accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 404) {
                            throw new OrderCreationException(HttpStatus.UNPROCESSABLE_ENTITY, "PRODUCT_NOT_FOUND", "Product not found");
                        }
                        if (status == 429 || status >= 500) {
                            throw unavailable();
                        }
                        byte[] body = response.getBody().readAllBytes();
                        try {
                            MediaType contentType = response.getHeaders().getContentType();
                            if (status != 200 || contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                                throw invalidResponse();
                            }
                            JsonNode node = mapper.readTree(body);
                            UUID receivedId = UUID.fromString(text(node, "productId"));
                            if (!productId.equals(receivedId) || !node.path("price").isNumber() || !node.path("active").isBoolean()) {
                                throw invalidResponse();
                            }
                            var snapshot = new ProductSnapshot(receivedId, text(node, "name"), node.path("price").decimalValue(),
                                    text(node, "currency"), node.path("active").booleanValue());
                            new OrderItem(snapshot.productId(), snapshot.name(), snapshot.price(), 1, snapshot.currency());
                            return snapshot;
                        } catch (RuntimeException exception) {
                            throw invalidResponse();
                        }
                    });
        } catch (ResourceAccessException exception) {
            throw unavailable();
        }
    }

    private static String text(JsonNode node, String field) {
        if (!node.path(field).isString()) {
            throw invalidResponse();
        }
        return node.path(field).stringValue();
    }

    private static OrderCreationException invalidResponse() {
        return new OrderCreationException(HttpStatus.BAD_GATEWAY, "INVALID_CATALOG_RESPONSE", "Invalid catalog response");
    }

    private static OrderCreationException unavailable() {
        return new OrderCreationException(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE", "Catalog unavailable");
    }

    public record ProductSnapshot(UUID productId, String name, BigDecimal price, String currency, boolean active) {}
}
