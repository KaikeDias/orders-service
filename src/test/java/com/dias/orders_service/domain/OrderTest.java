package com.dias.orders_service.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.dias.orders_service.domain.entity.Order;
import com.dias.orders_service.domain.entity.OrderItem;
import com.dias.orders_service.domain.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

	private static final String USD = "USD";

	@Test
	void createsAwaitingStockOrderWithImmutableSnapshots() {
		UUID orderId = UUID.randomUUID();
		OrderItem first = item(UUID.randomUUID(), "first", "10.25", 2);
		OrderItem second = item(UUID.randomUUID(), "second", "0.10", 3);
		List<OrderItem> source = new ArrayList<>(List.of(first, second));

		Order order = Order.create(orderId, USD, source);
		source.clear();
		source.add(item(UUID.randomUUID(), "replacement", "99.99", 1));

		assertThat(order.id()).isEqualTo(orderId);
		assertThat(order.currency()).isEqualTo(USD);
		assertThat(order.status()).isEqualTo(OrderStatus.AWAITING_STOCK);
		assertThat(order.items()).containsExactly(first, second);
		assertThat(order.items().getFirst().productName()).isEqualTo("first");
		assertThat(order.items().getFirst().unitPrice()).isEqualTo(new BigDecimal("10.25"));
		assertThat(order.items().getFirst().quantity()).isEqualTo(2);
		assertThatThrownBy(() -> order.items().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void rejectsMissingOrderData() {
		List<OrderItem> validItems = List.of(item(UUID.randomUUID(), "item", "1", 1));
		List<OrderItem> nullItem = Arrays.asList((OrderItem) null);

		assertThatThrownBy(() -> Order.create(null, USD, validItems)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Order.create(UUID.randomUUID(), USD, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Order.create(UUID.randomUUID(), USD, List.of())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Order.create(UUID.randomUUID(), USD, nullItem)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Order.create(UUID.randomUUID(), null, validItems)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Order.create(UUID.randomUUID(), "", validItems)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Order.create(UUID.randomUUID(), "   ", validItems)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Order.create(UUID.randomUUID(), "ZZZ", validItems)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsInvalidItemSnapshots() {
		UUID productId = UUID.randomUUID();
		BigDecimal price = BigDecimal.ONE;

		assertThatThrownBy(() -> new OrderItem(null, "item", price, 1, USD)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new OrderItem(productId, null, price, 1, USD)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new OrderItem(productId, "", price, 1, USD)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new OrderItem(productId, "   ", price, 1, USD)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new OrderItem(productId, "item", null, 1, USD)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new OrderItem(productId, "item", new BigDecimal("-0.01"), 1, USD))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new OrderItem(productId, "item", price, 0, USD)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new OrderItem(productId, "item", price, -1, USD)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new OrderItem(productId, "item", price, 1, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new OrderItem(productId, "item", price, 1, "")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new OrderItem(productId, "item", price, 1, "   ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new OrderItem(productId, "item", price, 1, "ZZZ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsDifferentCurrenciesAndRepeatedProducts() {
		UUID productId = UUID.randomUUID();
		OrderItem usdItem = item(productId, "snapshot A", "1.00", 1);
		OrderItem euroItem = new OrderItem(UUID.randomUUID(), "item", new BigDecimal("1.00"), 1, "EUR");
		OrderItem conflictingSnapshot = item(productId, "snapshot B", "9.99", 3);

		assertThatThrownBy(() -> Order.create(UUID.randomUUID(), USD, List.of(usdItem, euroItem)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Order.create(UUID.randomUUID(), USD, List.of(usdItem, conflictingSnapshot)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void calculatesExactSubtotalsAndTotalWithTwoDecimalPlaces() {
		OrderItem first = item(UUID.randomUUID(), "first", "10.25", 2);
		OrderItem second = item(UUID.randomUUID(), "second", "0.10", 3);
		Order order = Order.create(UUID.randomUUID(), USD, List.of(first, second));

		assertThat(first.subtotal()).isEqualTo(new BigDecimal("20.50"));
		assertThat(second.subtotal()).isEqualTo(new BigDecimal("0.30"));
		assertThat(order.total()).isEqualTo(new BigDecimal("20.80"));
	}

	@Test
	void acceptsZeroAndValuesExactlyRepresentableAtScaleTwo() {
		assertThat(item(UUID.randomUUID(), "zero", "0", 2).subtotal()).isEqualTo(new BigDecimal("0.00"));
		assertThat(item(UUID.randomUUID(), "integer", "1", 1).unitPrice()).isEqualTo(new BigDecimal("1.00"));
		assertThat(item(UUID.randomUUID(), "one decimal", "1.0", 1).unitPrice()).isEqualTo(new BigDecimal("1.00"));
		assertThat(item(UUID.randomUUID(), "trailing zero", "1.000", 1).unitPrice()).isEqualTo(new BigDecimal("1.00"));
		assertThat(item(UUID.randomUUID(), "large", "999999999999999999999999.99", 2).subtotal())
				.isEqualTo(new BigDecimal("1999999999999999999999999.98"));
		assertThatThrownBy(() -> new OrderItem(UUID.randomUUID(), "fraction", new BigDecimal("1.001"), 1, USD))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void recordsAcceptedAndRejectedStockResultsWithoutChangingSnapshots() {
		Order accepted = order();
		List<OrderItem> acceptedItems = accepted.items();
		BigDecimal acceptedTotal = accepted.total();
		accepted.recordStockAccepted();
		assertThat(accepted.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
		assertThat(accepted.items()).isEqualTo(acceptedItems);
		assertThat(accepted.total()).isEqualTo(acceptedTotal);

		Order rejected = order();
		List<OrderItem> rejectedItems = rejected.items();
		BigDecimal rejectedTotal = rejected.total();
		rejected.recordStockRejected();
		assertThat(rejected.status()).isEqualTo(OrderStatus.REJECTED);
		assertThat(rejected.items()).isEqualTo(rejectedItems);
		assertThat(rejected.total()).isEqualTo(rejectedTotal);
	}

	@Test
	void rejectsBothStockOperationsInEitherTerminalStateWithoutMutation() {
		Order awaitingPayment = order();
		awaitingPayment.recordStockAccepted();
		assertInvalidTransitionsPreserveOrder(awaitingPayment);

		Order rejected = order();
		rejected.recordStockRejected();
		assertInvalidTransitionsPreserveOrder(rejected);
	}

	private static void assertInvalidTransitionsPreserveOrder(Order order) {
		OrderStatus status = order.status();
		List<OrderItem> items = order.items();
		BigDecimal total = order.total();

		assertThatThrownBy(order::recordStockAccepted).isInstanceOf(IllegalStateException.class);
		assertThat(order.status()).isEqualTo(status);
		assertThat(order.items()).isEqualTo(items);
		assertThat(order.total()).isEqualTo(total);
		assertThatThrownBy(order::recordStockRejected).isInstanceOf(IllegalStateException.class);
		assertThat(order.status()).isEqualTo(status);
		assertThat(order.items()).isEqualTo(items);
		assertThat(order.total()).isEqualTo(total);
	}

	private static OrderItem item(UUID productId, String name, String price, int quantity) {
		return new OrderItem(productId, name, new BigDecimal(price), quantity, USD);
	}

	private static Order order() {
		return Order.create(UUID.randomUUID(), USD, List.of(item(UUID.randomUUID(), "item", "4.20", 2)));
	}
}
