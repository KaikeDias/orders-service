package com.dias.orders_service.api;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderRequest(@NotEmpty List<@NotNull @Valid Item> items) {
    public record Item(@NotNull UUID productId, @NotNull @Positive Integer quantity) {}
}
