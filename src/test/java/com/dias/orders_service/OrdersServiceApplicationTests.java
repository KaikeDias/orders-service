package com.dias.orders_service;

import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OrdersServiceApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private Flyway flyway;

	@Autowired
	private DataSource dataSource;

	@Test
	void connectsToPostgresAndRunsOnlyTheTestMigrationOnce() throws SQLException {
		assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
		try (var connection = dataSource.getConnection()) {
			assertThat(connection.getMetaData().getURL()).doesNotContain("should-not-connect", "orders_db");
		}
		assertThat(entityManagerFactory).isNotNull();
		assertThat(flyway.info().applied()).hasSize(1);
		assertThat(flyway.migrate().migrationsExecuted).isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('orders', 'order_items')", Integer.class))
				.isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('outbox_messages', 'processed_messages')", Integer.class))
				.isZero();
	}

}
