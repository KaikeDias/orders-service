package com.dias.orders_service.catalog;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("catalog")
public record CatalogProperties(URI baseUrl, @DefaultValue("1s") Duration connectTimeout,
                                @DefaultValue("2s") Duration readTimeout) {
    public CatalogProperties {
        if (baseUrl == null || baseUrl.getHost() == null
                || !("http".equalsIgnoreCase(baseUrl.getScheme()) || "https".equalsIgnoreCase(baseUrl.getScheme()))) {
            throw new IllegalArgumentException("catalog.base-url must be an HTTP(S) URL");
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("catalog timeouts must be positive");
        }
    }
}
