package com.dias.orders_service.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class Order {

	private final UUID id;
	private final String currency;
	private final List<OrderItem> items;
	private final BigDecimal total;
	private OrderStatus status;

	private Order(UUID id, String currency, List<OrderItem> items, BigDecimal total) {
		this.id = id;
		this.currency = currency;
		this.items = items;
		this.total = total;
		this.status = OrderStatus.AWAITING_STOCK;
	}

	public static Order create(UUID id, String currency, List<OrderItem> items) {
		if (id == null) {
			throw new IllegalArgumentException("id is required");
		}
		String validatedCurrency = validateCurrency(currency);
		if (items == null || items.isEmpty()) {
			throw new IllegalArgumentException("an order must contain at least one item");
		}

		List<OrderItem> snapshot = new ArrayList<>(items.size());
		Set<UUID> productIds = new HashSet<>();
		BigDecimal total = new BigDecimal("0.00");
		for (OrderItem item : items) {
			if (item == null) {
				throw new IllegalArgumentException("order items must not contain null");
			}
			if (!validatedCurrency.equals(item.currency())) {
				throw new IllegalArgumentException("all items must use the order currency");
			}
			if (!productIds.add(item.productId())) {
				throw new IllegalArgumentException("an order must not contain duplicate products");
			}
			snapshot.add(item);
			total = total.add(item.subtotal());
		}
		return new Order(id, validatedCurrency, List.copyOf(snapshot), total);
	}

	public UUID id() {
		return id;
	}

	public String currency() {
		return currency;
	}

	public List<OrderItem> items() {
		return items;
	}

	public BigDecimal total() {
		return total;
	}

	public OrderStatus status() {
		return status;
	}

	public void recordStockAccepted() {
		ensureAwaitingStock();
		status = OrderStatus.AWAITING_PAYMENT;
	}

	public void recordStockRejected() {
		ensureAwaitingStock();
		status = OrderStatus.REJECTED;
	}

	private void ensureAwaitingStock() {
		if (status != OrderStatus.AWAITING_STOCK) {
			throw new IllegalStateException("stock result can only be recorded while awaiting stock");
		}
	}

	private static String validateCurrency(String currency) {
		if (currency == null || currency.isBlank()) {
			throw new IllegalArgumentException("currency is required");
		}
		try {
			return Currency.getInstance(currency).getCurrencyCode();
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("currency must be a recognized ISO 4217 code", exception);
		}
	}
}
