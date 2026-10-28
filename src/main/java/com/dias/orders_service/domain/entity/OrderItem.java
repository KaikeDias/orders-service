package com.dias.orders_service.domain.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

@Entity
@Table(name = "order_items", uniqueConstraints = @UniqueConstraint(name = "uk_order_items_order_product", columnNames = {"order_id", "product_id"}))
@Access(AccessType.FIELD)
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class OrderItem {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "product_id", nullable = false, updatable = false, columnDefinition = "uuid")
	@EqualsAndHashCode.Include
	@ToString.Include
	private UUID productId;

	@Column(name = "product_name", nullable = false, updatable = false, columnDefinition = "text")
	@EqualsAndHashCode.Include
	@ToString.Include
	private String productName;

	@Column(name = "unit_price", nullable = false, updatable = false, columnDefinition = "numeric")
	@EqualsAndHashCode.Include
	@ToString.Include
	private BigDecimal unitPrice;

	@Column(name = "quantity", nullable = false, updatable = false)
	@EqualsAndHashCode.Include
	@ToString.Include
	private int quantity;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false, updatable = false)
	@Getter(AccessLevel.NONE)
	private Order order;

	@Transient
	@Getter(AccessLevel.NONE)
	private String snapshotCurrency;

	public OrderItem(UUID productId, String productName, BigDecimal unitPrice, int quantity, String currency) {
		this.id = UUID.randomUUID();
		this.productId = productId;
		this.productName = productName;
		this.unitPrice = unitPrice;
		this.quantity = quantity;
		this.snapshotCurrency = currency;
		validateSnapshot();
	}

	@EqualsAndHashCode.Include
	@ToString.Include
	public String currency() {
		return snapshotCurrency == null && order != null ? order.currency() : snapshotCurrency;
	}

	public BigDecimal subtotal() {
		return unitPrice.multiply(BigDecimal.valueOf(quantity));
	}

	void attachTo(Order order) {
		this.order = Objects.requireNonNull(order);
	}

	boolean belongsTo(Order order) {
		return this.order != null && order != null && Objects.equals(this.order.id(), order.id());
	}

	void restoreCurrencyFromOrder(String currency) {
		if (snapshotCurrency != null && !snapshotCurrency.equals(currency)) {
			throw new IllegalStateException("order item currency differs from its order");
		}
		snapshotCurrency = currency;
		try {
			validateSnapshot();
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("persisted order item is inconsistent", exception);
		}
	}

	OrderItem copyFor(Order order) {
		OrderItem copy = new OrderItem(productId, productName, unitPrice, quantity, currency());
		copy.attachTo(order);
		return copy;
	}

	@PrePersist
	private void beforeInsert() {
		validateSnapshot();
		if (order == null) {
			throw new IllegalStateException("order item must belong to an order");
		}
	}

	@PostLoad
	private void afterLoad() {
		if (order == null) {
			throw new IllegalStateException("persisted order item has no order");
		}
		try {
			validateSnapshot(false);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("persisted order item is inconsistent", exception);
		}
	}

	private void validateSnapshot() {
		validateSnapshot(true);
	}

	private void validateSnapshot(boolean validateCurrency) {
		if (id == null || productId == null) {
			throw new IllegalArgumentException("item identifiers are required");
		}
		if (productName == null || productName.isBlank()) {
			throw new IllegalArgumentException("productName must not be blank");
		}
		if (unitPrice == null || unitPrice.signum() < 0) {
			throw new IllegalArgumentException("unitPrice must be zero or greater");
		}
		try {
			unitPrice = unitPrice.setScale(2, RoundingMode.UNNECESSARY);
		} catch (ArithmeticException exception) {
			throw new IllegalArgumentException("unitPrice must be exactly representable with two decimal places", exception);
		}
		if (quantity <= 0) {
			throw new IllegalArgumentException("quantity must be greater than zero");
		}
		if (!validateCurrency) {
			return;
		}
		String currency = currency();
		if (currency == null || currency.isBlank()) {
			throw new IllegalArgumentException("currency is required");
		}
		try {
			if (!Currency.getInstance(currency).getCurrencyCode().equals(currency)) {
				throw new IllegalArgumentException("currency must be a recognized ISO 4217 code");
			}
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("currency must be a recognized ISO 4217 code", exception);
		}
	}
}
