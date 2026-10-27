package com.dias.orders_service;

import java.net.ConnectException;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceFailureTests {

	@Test
	void failsWhenPostgresConnectionIsInvalid() {
		var application = new SpringApplicationBuilder(OrdersServiceApplication.class)
				.web(WebApplicationType.NONE)
				.properties(
						"spring.datasource.url=jdbc:postgresql://127.0.0.1:1/isolated_test",
						"spring.datasource.username=test",
						"spring.datasource.password=test");

		assertThatThrownBy(application::run)
				.hasRootCauseInstanceOf(ConnectException.class);
	}

	@Test
	void failsWhenTestMigrationIsInvalid() {
		new ApplicationContextRunner()
				.withUserConfiguration(OrdersServiceApplication.class, TestcontainersConfiguration.class)
				.withPropertyValues("spring.flyway.locations=classpath:db/invalid-migration")
				.run(context -> assertThat(context.getStartupFailure())
						.hasMessageContaining("flywayInitializer")
						.hasRootCauseInstanceOf(PSQLException.class));
	}

}
