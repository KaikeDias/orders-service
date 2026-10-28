package com.dias.orders_service.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.dias.orders_service.domain.enums.OrderStatus;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "orders")
@Access(AccessType.FIELD)
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "text")
	private OrderStatus status = OrderStatus.AWAITING_STOCK;

	@Column(name = "currency", nullable = false, updatable = false, columnDefinition = "text")
	private String currency;

	@OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
	@Getter(AccessLevel.NONE)
	private List<OrderItem> items = new ArrayList<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	private Order(UUID id, String currency) {
		this.id = id;
		this.currency = currency;
	}

	public static Order create(UUID id, String currency, List<OrderItem> items) {
		if (id == null) {
			throw new IllegalArgumentException("id is required");
		}
		String validatedCurrency = validateCurrency(currency);
		if (items == null || items.isEmpty()) {
			throw new IllegalArgumentException("an order must contain at least one item");
		}

		Order order = new Order(id, validatedCurrency);
		Set<UUID> productIds = new HashSet<>();
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
			order.items.add(item.copyFor(order));
		}
		return order;
	}

	public List<OrderItem> items() {
		return Collections.unmodifiableList(items);
	}

	public BigDecimal total() {
		return items.stream()
				.map(OrderItem::subtotal)
				.reduce(new BigDecimal("0.00"), BigDecimal::add);
	}

	public void recordStockAccepted() {
		ensureAwaitingStock();
		status = OrderStatus.AWAITING_PAYMENT;
	}

	public void recordStockRejected() {
		ensureAwaitingStock();
		status = OrderStatus.REJECTED;
	}

	@PrePersist
	private void beforeInsert() {
		validateAggregate();
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	private void beforeUpdate() {
		validateAggregate();
		updatedAt = Instant.now();
	}

	@PostLoad
	private void afterLoad() {
		if (createdAt == null || updatedAt == null) {
			throw new IllegalStateException("persisted order is missing required timestamps");
		}
		validateAggregate();
	}

	private void validateAggregate() {
		if (id == null || status == null) {
			throw new IllegalStateException("persisted order is missing required data");
		}
		try {
			if (!Currency.getInstance(currency).getCurrencyCode().equals(currency)) {
				throw new IllegalArgumentException("currency must be a recognized ISO 4217 code");
			}
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("persisted order has an invalid currency", exception);
		}
		if (items == null || items.isEmpty()) {
			throw new IllegalStateException("persisted order must contain at least one item");
		}
		Set<UUID> productIds = new HashSet<>();
		for (OrderItem item : items) {
			if (!item.belongsTo(this) || !productIds.add(item.productId())) {
				throw new IllegalStateException("persisted order items violate aggregate invariants");
			}
			item.restoreCurrencyFromOrder(currency);
		}
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
