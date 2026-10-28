package com.dias.orders_service.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.dias.orders_service.application.OrderCreationException;
import com.dias.orders_service.application.OrderService;
import com.dias.orders_service.config.OrderConfiguration;
import com.dias.orders_service.domain.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(OrderConfiguration.class)
class OrderControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private OrderService service;
    private static final UUID ID = UUID.randomUUID();
    private static final String VALID = "{\"items\":[{\"productId\":\"" + ID + "\",\"quantity\":2}]}";

    @Test
    void returnsOnlyPublicCreationFieldsAfterServiceCompletes() throws Exception {
        when(service.create(any())).thenReturn(new OrderResponse(ID, OrderStatus.AWAITING_STOCK, new BigDecimal("20.80"), "BRL", Instant.parse("2026-10-28T12:00:00Z")));
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.status").value("AWAITING_STOCK"))
                .andExpect(jsonPath("$.total").value(20.80)).andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-28T12:00:00Z"))
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void rejectsMalformedOrCoercedInputsBeforeCallingService() throws Exception {
        for (String body : new String[]{"", "null", "{", "{}", "{\"items\":null}", "{\"items\":[]}",
                "{\"items\":[null]}", "{\"items\":[{}]}", VALID.replace(ID.toString(), "invalid"),
                VALID.replace("\"" + ID + "\"", "null"), VALID.replace("2", "0"),
                VALID.replace("\"quantity\":2", "\"quantity\":-1"), VALID.replace("\"quantity\":2", "\"quantity\":null"),
                VALID.replace("\"quantity\":2", "\"quantity\":1.5"), VALID.replace("\"quantity\":2", "\"quantity\":2.0"),
                VALID.replace("\"quantity\":2", "\"quantity\":\"2\""), VALID.replace("\"quantity\":2", "\"quantity\":2147483648"),
                VALID.replace("\"quantity\":2", "\"quantity\":2,\"price\":1"),
                VALID.replace("\"items\":", "\"currency\":\"BRL\",\"items\":"),
                VALID.replace("\"items\":", "\"status\":\"AWAITING_PAYMENT\",\"items\":")}) {
            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ORDER_REQUEST"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void preservesFrameworkStatusesForMissingRoutesAndUnsupportedMethods() throws Exception {
        mvc.perform(get("/orders")).andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "POST"));
        mvc.perform(get("/missing-route")).andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    @Test
    void mapsControlledErrorsAndSanitizesUnexpectedFailures() throws Exception {
        when(service.create(any())).thenThrow(new OrderCreationException(HttpStatus.UNPROCESSABLE_ENTITY, "PRODUCT_INACTIVE", "Product inactive"));
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("PRODUCT_INACTIVE"));
        doThrow(new IllegalStateException("secret database credentials")).when(service).create(any());
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isInternalServerError()).andExpect(content().json("{\"code\":\"ORDER_CREATION_FAILED\",\"message\":\"Order creation failed\"}"));
        mvc.perform(post("/orders").contentType(MediaType.TEXT_PLAIN).content(VALID))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }
}
