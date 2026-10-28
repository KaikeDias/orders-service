package com.dias.orders_service;

import java.time.Instant;
import java.util.UUID;
import com.dias.orders_service.application.OrderService;
import com.dias.orders_service.catalog.CatalogClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.dias.orders_service.domain.entity.Order;
import com.dias.orders_service.support.CatalogStub;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderCreationIntegrationTests {
    private static final CatalogStub CATALOG;
    static {
        try { CATALOG = new CatalogStub(); }
        catch (java.io.IOException exception) { throw new ExceptionInInitializerError(exception); }
    }
    @DynamicPropertySource static void catalog(DynamicPropertyRegistry registry) { registry.add("catalog.base-url", CATALOG::url); }
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @MockitoSpyBean private JsonMapper mapper;
    @MockitoSpyBean private CatalogClient catalogClient;

    @AfterEach void resetStub() { CATALOG.reset(); reset(mapper); }

    private String request(UUID first, UUID second) {
        return "{\"items\":[{\"productId\":\"" + first + "\",\"quantity\":2},{\"productId\":\"" + second + "\",\"quantity\":3}]}";
    }

    private UUID create(String request) throws Exception {
        String response = mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(20.8))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(response).get("id").stringValue());
    }

    @Test
    void commitsSnapshotsAndEnvelopeAndRepeatedRequestsProduceIndependentOrders() throws Exception {
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocation.callRealMethod();
        }).when(catalogClient).getProduct(any(UUID.class));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        CATALOG.product(first, "10.25", "BRL", true);
        CATALOG.product(second, "0.10", "BRL", true);
        String request = request(first, second);
        UUID id = create(request);
        UUID repeatedId = create(request);
        assertThat(repeatedId).isNotEqualTo(id);
        assertThat(CATALOG.calls.get()).isEqualTo(4);
        try (var manager = entityManagerFactory.createEntityManager()) {
            var order = manager.find(Order.class, id);
            assertThat(order.status().name()).isEqualTo("AWAITING_STOCK");
            assertThat(order.currency()).isEqualTo("BRL");
            assertThat(order.total()).isEqualByComparingTo("20.80");
            assertThat(order.items()).hasSize(2).allSatisfy(item -> assertThat(item.productName()).isEqualTo("Café"));
        }
        var row = jdbc.queryForMap("SELECT * FROM outbox_messages WHERE partition_key = ?", id.toString());
        var repeatedRow = jdbc.queryForMap("SELECT * FROM outbox_messages WHERE partition_key = ?", repeatedId.toString());
        assertThat(row.get("id")).isNotEqualTo(repeatedRow.get("id"));
        assertThat(row.get("topic")).isEqualTo("inventory.commands");
        assertThat(row.get("published_at")).isNull();
        var envelope = mapper.readTree((String) row.get("payload"));
        assertThat(envelope.propertyNames()).containsExactlyInAnyOrder("messageId", "type", "version", "orderId", "correlationId", "causationId", "occurredAt", "payload");
        assertThat(envelope.get("messageId").stringValue()).isEqualTo(row.get("id").toString());
        assertThat(envelope.get("orderId").stringValue()).isEqualTo(id.toString());
        assertThat(envelope.get("correlationId").stringValue()).isEqualTo(id.toString());
        assertThat(envelope.get("causationId").isNull()).isTrue();
        assertThat(envelope.get("type").stringValue()).isEqualTo("ReserveStock");
        assertThat(envelope.get("version").intValue()).isEqualTo(1);
        assertThat(Instant.parse(envelope.get("occurredAt").stringValue())).isNotNull();
        assertThat(envelope.get("payload").get("items")).isEqualTo(mapper.readTree(request).get("items"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items WHERE order_id = ?", Integer.class, id)).isEqualTo(2);
    }

    @Test
    void outboxInsertFailureAfterOrderFlushRollsBackRealProductionTransaction() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        CATALOG.product(first, "10.25", "BRL", true);
        CATALOG.product(second, "0.10", "BRL", true);
        UUID existing = create(request(first, second));
        int ordersBefore = count("orders");
        int itemsBefore = count("order_items");
        int outboxBefore = count("outbox_messages");
        // Sequence advancement survives rollback, proving the trigger observed the flushed rows.
        jdbc.execute("CREATE SEQUENCE creation_flush_observed");
        jdbc.execute("CREATE FUNCTION reject_creation_outbox() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NOT EXISTS (SELECT 1 FROM orders WHERE id::text = NEW.partition_key) OR NOT EXISTS (SELECT 1 FROM order_items WHERE order_id::text = NEW.partition_key) THEN RAISE EXCEPTION 'order was not flushed'; END IF; PERFORM nextval('creation_flush_observed'); RAISE EXCEPTION 'forced outbox insert failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_creation_outbox BEFORE INSERT ON outbox_messages FOR EACH ROW EXECUTE FUNCTION reject_creation_outbox()");
        try {
            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(request(first, second)))
                    .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("ORDER_CREATION_FAILED"));
            assertThat(jdbc.queryForObject("SELECT is_called FROM creation_flush_observed", Boolean.class)).isTrue();
        } finally {
            jdbc.execute("DROP TRIGGER reject_creation_outbox ON outbox_messages");
            jdbc.execute("DROP FUNCTION reject_creation_outbox()");
            jdbc.execute("DROP SEQUENCE creation_flush_observed");
        }
        assertThat(count("orders")).isEqualTo(ordersBefore);
        assertThat(count("order_items")).isEqualTo(itemsBefore);
        assertThat(count("outbox_messages")).isEqualTo(outboxBefore);
        assertThat(jdbc.queryForObject("SELECT id FROM orders WHERE id = ?", UUID.class, existing)).isEqualTo(existing);
    }

    @Test
    void serializationAndCatalogFailuresDoNotWriteAndRequestDuplicatesNeverCallCatalog() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        CATALOG.product(first, "10.25", "BRL", true);
        CATALOG.product(second, "0.10", "BRL", true);
        int before = count("orders");
        doThrow(new IllegalStateException("serialization failed")).when(mapper).writeValueAsString(any(OrderService.ReserveStock.class));
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(request(first, second)))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("ORDER_CREATION_FAILED"));
        reset(mapper);
        CATALOG.reset();
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(request(first, first)))
                .andExpect(status().isBadRequest());
        assertThat(CATALOG.calls.get()).isZero();
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(request(first, second)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        assertThat(CATALOG.calls.get()).isEqualTo(1);
        assertThat(count("orders")).isEqualTo(before);
    }

    @Test
    void catalogErrorsAreMappedWithoutAnyPartialCreation() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        CATALOG.product(first, "10.25", "BRL", true);
        int orders = count("orders");
        int items = count("order_items");
        int outbox = count("outbox_messages");
        for (String scenario : new String[]{"inactive", "mixed", "invalid", "unavailable"}) {
            if (scenario.equals("inactive")) CATALOG.product(second, "0.10", "BRL", false);
            if (scenario.equals("mixed")) CATALOG.product(second, "0.10", "USD", true);
            if (scenario.equals("invalid")) CATALOG.reply(second, 200, "{}");
            if (scenario.equals("unavailable")) CATALOG.reply(second, 503, "{}");
            int expected = scenario.equals("invalid") ? 502 : scenario.equals("unavailable") ? 503 : 422;
            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(request(first, second)))
                    .andExpect(status().is(expected));
            assertThat(count("orders")).isEqualTo(orders);
            assertThat(count("order_items")).isEqualTo(items);
            assertThat(count("outbox_messages")).isEqualTo(outbox);
        }
    }

    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
}
