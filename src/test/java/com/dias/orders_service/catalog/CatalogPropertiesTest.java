package com.dias.orders_service.catalog;

import java.net.URI;
import java.time.Duration;
import com.dias.orders_service.config.OrderConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class CatalogPropertiesTest {
    @Test
    void requiresHttpUrlAndPositiveTimeoutsAtStartup() {
        var runner = new ApplicationContextRunner().withUserConfiguration(OrderConfiguration.class);
        runner.run(context -> assertThat(context).hasFailed());
        for (String url : new String[]{"ftp://localhost", "relative", "http://"}) {
            runner.withPropertyValues("catalog.base-url=" + url).run(context -> assertThat(context).hasFailed());
        }
        for (String property : new String[]{"catalog.connect-timeout=0s", "catalog.read-timeout=-1s"}) {
            runner.withPropertyValues("catalog.base-url=http://localhost", property).run(context -> assertThat(context).hasFailed());
        }
        runner.withPropertyValues("catalog.base-url=https://localhost").run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(CatalogProperties.class);
            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
            assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(2));
        });
    }

    @Test
    void rejectsNullTimeoutsAndBothNonPositiveTimeouts() {
        URI url = URI.create("http://localhost");
        Duration positive = Duration.ofSeconds(1);
        assertThatThrownBy(() -> new CatalogProperties(url, null, positive)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CatalogProperties(url, positive, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CatalogProperties(url, Duration.ZERO, positive)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CatalogProperties(url, positive, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
