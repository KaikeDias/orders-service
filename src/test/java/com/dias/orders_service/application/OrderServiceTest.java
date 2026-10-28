package com.dias.orders_service.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import com.dias.orders_service.api.CreateOrderRequest;
import com.dias.orders_service.catalog.CatalogClient;
import com.dias.orders_service.catalog.CatalogClient.ProductSnapshot;
import com.dias.orders_service.domain.entity.Order;
import com.dias.orders_service.domain.entity.OutboxMessage;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {
    private final CatalogClient catalog = mock(CatalogClient.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final JsonMapper mapper = spy(JsonMapper.builder().build());
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final OrderService service = new OrderService(catalog, entityManager, mapper, transactions);
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final CreateOrderRequest request = new CreateOrderRequest(List.of(new CreateOrderRequest.Item(first, 2), new CreateOrderRequest.Item(second, 3)));

    private void products() {
        when(catalog.getProduct(first)).thenReturn(new ProductSnapshot(first, "A", new BigDecimal("10.25"), "BRL", true));
        when(catalog.getProduct(second)).thenReturn(new ProductSnapshot(second, "B", new BigDecimal("0.10"), "BRL", true));
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void readsBeforeTransactionAndCommitsExactlyOneCompleteEnvelope() {
        products();
        var response = service.create(request);
        assertThat(response.total()).isEqualByComparingTo("20.80");
        var outbox = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(entityManager).persist(outbox.capture());
        var envelope = mapper.readTree(outbox.getValue().payload());
        assertThat(envelope.get("messageId").stringValue()).isEqualTo(outbox.getValue().id().toString());
        assertThat(envelope.get("causationId").isNull()).isTrue();
        var order = inOrder(catalog, transactions, entityManager);
        order.verify(catalog).getProduct(first);
        order.verify(catalog).getProduct(second);
        order.verify(transactions).getTransaction(any());
        order.verify(entityManager).persist(any(Order.class));
        order.verify(entityManager).flush();
        order.verify(entityManager).persist(any(OutboxMessage.class));
        order.verify(entityManager).flush();
        order.verify(transactions).commit(any());
    }

    @Test
    void rejectsDuplicatesBeforeCatalogOrWriting() {
        var duplicate = new CreateOrderRequest(List.of(new CreateOrderRequest.Item(first, 1), new CreateOrderRequest.Item(first, 2)));
        assertThatThrownBy(() -> service.create(duplicate)).isInstanceOfSatisfying(OrderCreationException.class,
                exception -> assertThat(exception.code()).isEqualTo("INVALID_ORDER_REQUEST"));
        verifyNoInteractions(catalog, entityManager, transactions);
    }

    @Test
    void stopsAtInactiveOrMixedCurrencyWithoutTransaction() {
        products();
        when(catalog.getProduct(first)).thenReturn(new ProductSnapshot(first, "A", BigDecimal.ZERO, "BRL", false));
        assertThatThrownBy(() -> service.create(request)).isInstanceOfSatisfying(OrderCreationException.class,
                exception -> assertThat(exception.code()).isEqualTo("PRODUCT_INACTIVE"));
        verify(catalog, never()).getProduct(second);
        when(catalog.getProduct(first)).thenReturn(new ProductSnapshot(first, "A", BigDecimal.ZERO, "BRL", true));
        when(catalog.getProduct(second)).thenReturn(new ProductSnapshot(second, "B", BigDecimal.ZERO, "USD", true));
        assertThatThrownBy(() -> service.create(request)).isInstanceOfSatisfying(OrderCreationException.class,
                exception -> assertThat(exception.code()).isEqualTo("MIXED_CURRENCIES"));
        verifyNoInteractions(entityManager);
        verify(transactions, never()).getTransaction(any());
    }

    @Test
    void serializationFailureOccursBeforeAnyWrite() {
        products();
        doThrow(new IllegalStateException("serialization failure")).when(mapper).writeValueAsString(any(OrderService.ReserveStock.class));
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(entityManager);
        verify(transactions, never()).getTransaction(any());
    }

    @Test
    void commitFailureCannotReturnSuccessAndInsertionFailureRollsBack() {
        products();
        doThrow(new IllegalStateException("commit failure")).when(transactions).commit(any());
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(IllegalStateException.class);
        reset(transactions);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        doThrow(new IllegalStateException("insert failure")).when(entityManager).persist(any(OutboxMessage.class));
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(IllegalStateException.class);
        verify(transactions).rollback(any());
    }
}
