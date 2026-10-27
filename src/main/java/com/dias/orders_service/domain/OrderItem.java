package com.dias.orders_service.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.UUID;

public record OrderItem(UUID productId, String productName, BigDecimal unitPrice, int quantity, String currency) {

	public OrderItem {
		if (productId == null) {
			throw new IllegalArgumentException("productId is required");
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
		currency = validateCurrency(currency);
	}

	public BigDecimal subtotal() {
		return unitPrice.multiply(BigDecimal.valueOf(quantity));
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
