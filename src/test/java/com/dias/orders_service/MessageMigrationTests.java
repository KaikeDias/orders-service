package com.dias.orders_service;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MessageMigrationTests {

	@Autowired
	private DataSource dataSource;

	@Test
	void upgradesV1WithExistingDataWithoutChangingOrdersOrReapplyingMigrations() throws SQLException {
		String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
		Flyway initial = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
				.target("1").load();
		assertThat(initial.migrate().migrationsExecuted).isEqualTo(1);

		try (var connection = dataSource.getConnection()) {
			String originalSchema = connection.getSchema();
			try {
				connection.setSchema(schema);
				JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
				UUID orderId = UUID.randomUUID();
				UUID itemId = UUID.randomUUID();
				UUID productId = UUID.randomUUID();
				Timestamp created = Timestamp.from(Instant.parse("2026-10-27T10:00:00.123456Z"));
				Timestamp updated = Timestamp.from(Instant.parse("2026-10-27T11:00:00.654321Z"));
				jdbc.update("INSERT INTO orders (id, status, currency, created_at, updated_at, version) VALUES (?, 'AWAITING_PAYMENT', 'USD', ?, ?, 7)",
						orderId, created, updated);
				jdbc.update("INSERT INTO order_items (id, order_id, product_id, product_name, unit_price, quantity) VALUES (?, ?, ?, 'histórico café', 123.45, 2)",
						itemId, orderId, productId);
				var originalOrders = jdbc.queryForList("SELECT * FROM orders");
				var originalItems = jdbc.queryForList("SELECT * FROM order_items");
				Integer v1Checksum = jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version = '1'", Integer.class);

				Flyway upgrade = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
				assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
				assertThat(upgrade.info().applied()).filteredOn(info -> info.getVersion() != null).hasSize(2);
				assertThat(upgrade.migrate().migrationsExecuted).isZero();
				assertThat(jdbc.queryForList("SELECT * FROM orders")).isEqualTo(originalOrders);
				assertThat(jdbc.queryForList("SELECT * FROM order_items")).isEqualTo(originalItems);
				assertThat(jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version = '1'", Integer.class)).isEqualTo(v1Checksum);
				assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name IN ('orders', 'order_items', 'outbox_messages', 'processed_messages')", Integer.class, schema))
						.isEqualTo(4);
			} finally {
				connection.setSchema(originalSchema);
			}
		}
	}
}
