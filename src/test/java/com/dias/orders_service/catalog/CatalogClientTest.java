package com.dias.orders_service.catalog;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import com.dias.orders_service.application.OrderCreationException;
import com.dias.orders_service.support.CatalogStub;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class CatalogClientTest {
    private static final UUID ID = UUID.randomUUID();

    private CatalogClient client(String url, Duration readTimeout) {
        return new CatalogClient(new CatalogProperties(URI.create(url), Duration.ofSeconds(1), readTimeout), JsonMapper.builder().build());
    }

    @Test
    void readsExactSnapshotsOverHttpAndIgnoresAdditionalCatalogFields() throws IOException {
        try (var stub = new CatalogStub()) {
            var client = client(stub.url(), Duration.ofSeconds(2));
            for (String price : new String[]{"0", "1.000", "12345678901234567890.12"}) {
                stub.product(ID, price, "BRL", true);
                var product = client.getProduct(ID);
                assertThat(product.price()).isEqualByComparingTo(price);
                assertThat(product.name()).isEqualTo("Café");
                assertThat(product.active()).isTrue();
            }
            stub.reply(ID, 200, "{\"productId\":\"" + ID + "\",\"name\":\"A\",\"price\":0,\"currency\":\"USD\",\"active\":false,\"future\":1}");
            assertThat(client.getProduct(ID).active()).isFalse();
            assertThat(stub.calls.get()).isEqualTo(4);
            assertThat(stub.lastMethod).isEqualTo("GET");
            assertThat(stub.lastPath).isEqualTo("/products/" + ID);
        }
    }

    @Test
    void distinguishesHttpFailuresWithoutRetriesOrRedirects() throws IOException {
        try (var stub = new CatalogStub()) {
            var client = client(stub.url(), Duration.ofSeconds(2));
            for (int status : new int[]{404, 429, 500, 503, 302, 401, 403, 204}) {
                stub.reply(ID, status, "{}");
                String code = status == 404 ? "PRODUCT_NOT_FOUND" : status == 429 || status >= 500 ? "CATALOG_UNAVAILABLE" : "INVALID_CATALOG_RESPONSE";
                assertThatThrownBy(() -> client.getProduct(ID)).isInstanceOfSatisfying(OrderCreationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
            }
            assertThat(stub.calls.get()).isEqualTo(8);
        }
    }

    @Test
    void rejectsInvalidCatalogDataRatherThanCoercingOrRounding() throws IOException {
        try (var stub = new CatalogStub()) {
            var client = client(stub.url(), Duration.ofSeconds(2));
            String valid = "{\"productId\":\"" + ID + "\",\"name\":\"A\",\"price\":1.00,\"currency\":\"USD\",\"active\":true}";
            var mapper = JsonMapper.builder().build();
            for (String field : new String[]{"productId", "name", "price", "currency", "active"}) {
                var missing = mapper.readTree(valid).asObject();
                missing.remove(field);
                stub.reply(ID, 200, missing.toString());
                assertInvalid(client);
                var nulled = mapper.readTree(valid).asObject();
                nulled.putNull(field);
                stub.reply(ID, 200, nulled.toString());
                assertInvalid(client);
            }
            for (String body : new String[]{"null", "[]", "{", valid.replace(ID.toString(), UUID.randomUUID().toString()),
                    valid.replace(ID.toString(), "invalid"), valid.replace("\"A\"", "\" \""),
                    valid.replace("\"USD\"", "\"invalid\""), valid.replace("1.00", "-1"),
                    valid.replace("1.00", "1.001"), valid.replace("1.00", "\"1.00\""),
                    valid.replace("true", "\"true\""), valid.replace("\"A\"", "4")}) {
                stub.reply(ID, 200, body);
                assertInvalid(client);
            }
            stub.reply(ID, 200, valid, 0, "text/plain");
            assertInvalid(client);
            stub.reply(ID, 200, valid, 0, "malformed");
            assertInvalid(client);
        }
    }

    private void assertInvalid(CatalogClient client) {
        assertThatThrownBy(() -> client.getProduct(ID)).isInstanceOfSatisfying(OrderCreationException.class,
                exception -> assertThat(exception.code()).isEqualTo("INVALID_CATALOG_RESPONSE"));
    }

    @Test
    void boundsResponseTimeAndReportsConnectionRefusal() throws IOException {
        try (var stub = new CatalogStub()) {
            var client = client(stub.url(), Duration.ofMillis(60));
            stub.reply(ID, 200, "{}", 300, "application/json");
            assertThatThrownBy(() -> client.getProduct(ID)).isInstanceOfSatisfying(OrderCreationException.class,
                    exception -> assertThat(exception.code()).isEqualTo("CATALOG_UNAVAILABLE"));
            assertThat(stub.calls.get()).isEqualTo(1);
            stub.slowBody(ID, 300);
            assertThatThrownBy(() -> client.getProduct(ID)).isInstanceOfSatisfying(OrderCreationException.class,
                    exception -> assertThat(exception.code()).isEqualTo("CATALOG_UNAVAILABLE"));
            assertThat(stub.calls.get()).isEqualTo(2);
        }
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        var client = client("http://127.0.0.1:" + port, Duration.ofSeconds(1));
        assertThatThrownBy(() -> client.getProduct(ID)).isInstanceOfSatisfying(OrderCreationException.class,
                exception -> assertThat(exception.code()).isEqualTo("CATALOG_UNAVAILABLE"));
    }
}
