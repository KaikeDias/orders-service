package com.dias.orders_service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import com.dias.orders_service.domain.entity.Order;
import com.dias.orders_service.domain.entity.OrderItem;
import com.dias.orders_service.domain.enums.OrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderPersistenceTests {

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void persistsAndReloadsOrdersAndItemsWithIndependentItemIds() {
		OrderItem firstInput = item(UUID.randomUUID(), "first", "10.25", 2);
		OrderItem secondInput = item(UUID.randomUUID(), "second", "0.10", 3);
		Order firstOrder = Order.create(UUID.randomUUID(), "USD", List.of(firstInput, secondInput));
		Order secondOrder = Order.create(UUID.randomUUID(), "USD", List.of(firstInput));
		Map<UUID, UUID> itemIds = new HashMap<>();
		firstOrder.items().forEach(orderItem -> itemIds.put(orderItem.productId(), orderItem.id()));

		assertThat(firstOrder.items()).extracting(OrderItem::id).doesNotContain(firstInput.id(), secondInput.id());
		assertThat(secondOrder.items().getFirst()).isNotSameAs(firstOrder.items().getFirst());
		assertThat(secondOrder.items().getFirst().id()).isNotEqualTo(firstOrder.items().getFirst().id());

		inTransaction(entityManager -> {
			entityManager.persist(firstOrder);
			entityManager.persist(secondOrder);
			entityManager.flush();
			entityManager.clear();

			Order reloaded = entityManager.find(Order.class, firstOrder.id());
			assertThat(reloaded.status()).isEqualTo(OrderStatus.AWAITING_STOCK);
			assertThat(reloaded.currency()).isEqualTo("USD");
			assertThat(reloaded.total()).isEqualTo(new BigDecimal("20.80"));
			assertThat(reloaded.items()).hasSize(2);
			assertThat(reloaded.items()).containsExactlyInAnyOrderElementsOf(firstOrder.items());
			assertThat(reloaded.items()).extracting(OrderItem::productId).containsExactlyInAnyOrder(firstInput.productId(), secondInput.productId());
			assertThat(reloaded.items()).extracting(OrderItem::id).containsExactlyInAnyOrderElementsOf(itemIds.values());
			for (OrderItem orderItem : reloaded.items()) {
				assertThat(orderItem.currency()).isEqualTo("USD");
				assertThat(orderItem.subtotal().scale()).isEqualTo(2);
			}
			assertThat(reloaded.items()).filteredOn(orderItem -> orderItem.productId().equals(firstInput.productId()))
					.singleElement().satisfies(orderItem -> {
						assertThat(orderItem.productName()).isEqualTo("first");
						assertThat(orderItem.unitPrice()).isEqualTo(new BigDecimal("10.25"));
						assertThat(orderItem.quantity()).isEqualTo(2);
						assertThat(orderItem.hashCode()).isEqualTo(firstOrder.items().getFirst().hashCode());
						assertThat(orderItem.toString()).contains("currency=USD").doesNotContain("order=");
					});
			return null;
		});

		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'order_items' AND column_name IN ('currency', 'total', 'subtotal', 'position')", Integer.class))
				.isZero();
	}

	@Test
	void loadsOrderItemDirectlyBeforeItsLazyParent() {
		Order order = Order.create(UUID.randomUUID(), "USD", List.of(item(UUID.randomUUID(), "direct item", "2.35", 4)));
		UUID itemId = order.items().getFirst().id();
		UUID productId = order.items().getFirst().productId();

		inTransaction(entityManager -> {
			entityManager.persist(order);
			entityManager.flush();
			entityManager.clear();

			OrderItem loadedItem = entityManager.find(OrderItem.class, itemId);
			assertThat(loadedItem.currency()).isEqualTo("USD");
			assertThat(loadedItem.subtotal()).isEqualTo(new BigDecimal("9.40"));

			Order loadedOrder = entityManager.find(Order.class, order.id());
			assertThat(loadedOrder.currency()).isEqualTo("USD");
			assertThat(loadedOrder.total()).isEqualTo(new BigDecimal("9.40"));
			assertThat(loadedOrder.items()).singleElement().satisfies(aggregateItem -> {
				assertThat(aggregateItem.id()).isEqualTo(itemId);
				assertThat(aggregateItem.productId()).isEqualTo(productId);
				assertThat(aggregateItem.currency()).isEqualTo(loadedItem.currency());
				assertThat(aggregateItem.subtotal()).isEqualTo(loadedItem.subtotal());
			});
			return null;
		});
	}

	@Test
	void reloadedTerminalStatesKeepTheirValuesAndRejectIncompatibleTransitions() {
		Order awaitingPayment = Order.create(UUID.randomUUID(), "USD", List.of(item(UUID.randomUUID(), "paid next", "2.50", 2)));
		awaitingPayment.recordStockAccepted();
		Order rejected = Order.create(UUID.randomUUID(), "USD", List.of(item(UUID.randomUUID(), "rejected", "3.00", 1)));
		rejected.recordStockRejected();

		inTransaction(entityManager -> {
			entityManager.persist(awaitingPayment);
			entityManager.persist(rejected);
			entityManager.flush();
			entityManager.clear();

			Order loadedPayment = entityManager.find(Order.class, awaitingPayment.id());
			assertThat(loadedPayment.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
			assertThat(loadedPayment.total()).isEqualTo(new BigDecimal("5.00"));
			assertThatThrownBy(loadedPayment::recordStockRejected).isInstanceOf(IllegalStateException.class);
			assertThat(loadedPayment.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);

			Order loadedRejected = entityManager.find(Order.class, rejected.id());
			assertThat(loadedRejected.status()).isEqualTo(OrderStatus.REJECTED);
			assertThat(loadedRejected.total()).isEqualTo(new BigDecimal("3.00"));
			assertThatThrownBy(loadedRejected::recordStockAccepted).isInstanceOf(IllegalStateException.class);
			assertThat(loadedRejected.status()).isEqualTo(OrderStatus.REJECTED);
			return null;
		});
	}

	@Test
	void fillsTimestampsAndUsesOptimisticVersionForUpdates() throws InterruptedException {
		Order order = Order.create(UUID.randomUUID(), "USD", List.of(item(UUID.randomUUID(), "item", "1.00", 1)));
		long persistedVersion = inTransaction(entityManager -> {
			entityManager.persist(order);
			entityManager.flush();
			return order.version();
		});
		Instant createdAt = inTransaction(entityManager -> entityManager.find(Order.class, order.id()).createdAt());

		EntityManager firstManager = entityManagerFactory.createEntityManager();
		EntityManager staleManager = entityManagerFactory.createEntityManager();
		EntityTransaction firstTransaction = firstManager.getTransaction();
		EntityTransaction staleTransaction = staleManager.getTransaction();
		try {
			firstTransaction.begin();
			Order first = firstManager.find(Order.class, order.id());
			staleTransaction.begin();
			Order stale = staleManager.find(Order.class, order.id());
			assertThat(first.version()).isEqualTo(persistedVersion);
			assertThat(stale.version()).isEqualTo(persistedVersion);

			Thread.sleep(5);
			first.recordStockAccepted();
			firstManager.flush();
			firstTransaction.commit();

			stale.recordStockRejected();
			assertThatThrownBy(staleManager::flush).isInstanceOf(OptimisticLockException.class);
			staleTransaction.rollback();
		} finally {
			if (firstTransaction.isActive()) {
				firstTransaction.rollback();
			}
			if (staleTransaction.isActive()) {
				staleTransaction.rollback();
			}
			firstManager.close();
			staleManager.close();
		}

		inTransaction(entityManager -> {
			Order persisted = entityManager.find(Order.class, order.id());
			assertThat(persisted.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
			assertThat(persisted.createdAt()).isEqualTo(createdAt);
			assertThat(persisted.updatedAt()).isAfter(createdAt);
			assertThat(persisted.version()).isEqualTo(persistedVersion + 1);
			return null;
		});
	}

	@Test
	void rejectsDirectSqlConstraintViolationsAndAllowsExactPrices() {
		UUID orderId = insertOrder("AWAITING_STOCK", "USD");
		UUID productId = UUID.randomUUID();
		insertItem(orderId, productId, "zero", new BigDecimal("0"), 1);
		insertItem(orderId, UUID.randomUUID(), "trailing scale", new BigDecimal("1.000"), 1);
		insertItem(orderId, UUID.randomUUID(), "large", new BigDecimal("999999999999999999999999.99"), 2);

		assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO orders (id, status, currency, created_at, updated_at, version) VALUES (?, 'UNKNOWN', 'USD', ?, ?, 0)",
				UUID.randomUUID(), Timestamp.from(Instant.now()), Timestamp.from(Instant.now())))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO order_items (id, order_id, product_id, product_name, unit_price, quantity) VALUES (?, ?, ?, NULL, 1, 1)",
				UUID.randomUUID(), orderId, UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO order_items (id, order_id, product_id, product_name, unit_price, quantity) VALUES (?, ?, ?, 'invalid quantity', 1, 0)",
				UUID.randomUUID(), orderId, UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO order_items (id, order_id, product_id, product_name, unit_price, quantity) VALUES (?, ?, ?, 'negative quantity', 1, -1)",
				UUID.randomUUID(), orderId, UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO order_items (id, order_id, product_id, product_name, unit_price, quantity) VALUES (?, ?, ?, 'negative price', -1, 1)",
				UUID.randomUUID(), orderId, UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO order_items (id, order_id, product_id, product_name, unit_price, quantity) VALUES (?, ?, ?, 'excess scale', 1.001, 1)",
				UUID.randomUUID(), orderId, UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO order_items (id, order_id, product_id, product_name, unit_price, quantity) VALUES (?, ?, ?, 'NaN', 'NaN'::numeric, 1)",
				UUID.randomUUID(), orderId, UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO order_items (id, order_id, product_id, product_name, unit_price, quantity) VALUES (?, ?, ?, 'infinity', 'Infinity'::numeric, 1)",
				UUID.randomUUID(), orderId, UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO order_items (id, order_id, product_id, product_name, unit_price, quantity) VALUES (?, ?, ?, 'invalid parent', 1, 1)",
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);

		UUID repeatedProduct = UUID.randomUUID();
		insertItem(orderId, repeatedProduct, "first product row", BigDecimal.ONE, 1);
		assertThatThrownBy(() -> insertItem(orderId, repeatedProduct, "duplicate product row", BigDecimal.TEN, 2))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsNullInEveryRequiredColumnAndDuplicatePrimaryKeys() {
		List<String> orderColumns = List.of("id", "status", "currency", "created_at", "updated_at", "version");
		for (String nullColumn : orderColumns) {
			Object[] values = {UUID.randomUUID(), "AWAITING_STOCK", "USD", Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), 0L};
			values[orderColumns.indexOf(nullColumn)] = null;
			assertDirectSqlViolation("INSERT INTO orders (id, status, currency, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, ?)", values);
		}

		UUID orderId = insertOrder("AWAITING_STOCK", "USD");
		List<String> itemColumns = List.of("id", "order_id", "product_id", "product_name", "unit_price", "quantity");
		for (String nullColumn : itemColumns) {
			Object[] values = {UUID.randomUUID(), orderId, UUID.randomUUID(), "item", BigDecimal.ONE, 1};
			values[itemColumns.indexOf(nullColumn)] = null;
			assertDirectSqlViolation("INSERT INTO order_items (id, order_id, product_id, product_name, unit_price, quantity) VALUES (?, ?, ?, ?, ?, ?)", values);
		}

		UUID duplicateOrderId = insertOrder("AWAITING_STOCK", "USD");
		assertDirectSqlViolation("INSERT INTO orders (id, status, currency, created_at, updated_at, version) VALUES (?, 'AWAITING_STOCK', 'USD', ?, ?, 0)",
				duplicateOrderId, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
		UUID duplicateItemId = insertItem(orderId, UUID.randomUUID(), "primary key", BigDecimal.ONE, 1);
		assertDirectSqlViolation("INSERT INTO order_items (id, order_id, product_id, product_name, unit_price, quantity) VALUES (?, ?, ?, ?, ?, ?)",
				duplicateItemId, orderId, UUID.randomUUID(), "duplicate primary key", BigDecimal.ONE, 1);
	}

	@Test
	void rejectsRehydratingEmptyOrdersAndInvalidCurrencyOrItemSnapshots() {
		UUID emptyOrderId = insertOrder("AWAITING_STOCK", "USD");
		assertThatThrownBy(() -> inTransaction(entityManager -> entityManager.find(Order.class, emptyOrderId)))
				.isInstanceOf(IllegalStateException.class);

		UUID invalidCurrencyOrderId = insertOrder("AWAITING_STOCK", "ZZZ");
		insertItem(invalidCurrencyOrderId, UUID.randomUUID(), "item", BigDecimal.ONE, 1);
		assertThatThrownBy(() -> inTransaction(entityManager -> entityManager.find(Order.class, invalidCurrencyOrderId)))
				.isInstanceOf(IllegalStateException.class);

		UUID invalidItemOrderId = insertOrder("AWAITING_STOCK", "USD");
		insertItem(invalidItemOrderId, UUID.randomUUID(), "   ", BigDecimal.ONE, 1);
		assertThatThrownBy(() -> inTransaction(entityManager -> entityManager.find(Order.class, invalidItemOrderId)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void foreignKeyPreventsDeletingAnOrderWithItems() {
		Order order = Order.create(UUID.randomUUID(), "USD", List.of(item(UUID.randomUUID(), "item", "1.00", 1)));
		inTransaction(entityManager -> {
			entityManager.persist(order);
			return null;
		});

		assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM orders WHERE id = ?", order.id()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private UUID insertOrder(String status, String currency) {
		UUID id = UUID.randomUUID();
		Instant now = Instant.now();
		jdbcTemplate.update("INSERT INTO orders (id, status, currency, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, 0)",
				id, status, currency, Timestamp.from(now), Timestamp.from(now));
		return id;
	}

	private UUID insertItem(UUID orderId, UUID productId, String name, BigDecimal unitPrice, int quantity) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("INSERT INTO order_items (id, order_id, product_id, product_name, unit_price, quantity) VALUES (?, ?, ?, ?, ?, ?)",
				id, orderId, productId, name, unitPrice, quantity);
		return id;
	}

	private void assertDirectSqlViolation(String sql, Object... values) {
		assertThatThrownBy(() -> jdbcTemplate.update(sql, values)).isInstanceOf(DataIntegrityViolationException.class);
	}

	private <T> T inTransaction(Function<EntityManager, T> work) {
		try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
			EntityTransaction transaction = entityManager.getTransaction();
			transaction.begin();
			try {
				T result = work.apply(entityManager);
				transaction.commit();
				return result;
			} catch (RuntimeException | Error exception) {
				if (transaction.isActive()) {
					transaction.rollback();
				}
				throw exception;
			}
		}
	}

	private static OrderItem item(UUID productId, String name, String price, int quantity) {
		return new OrderItem(productId, name, new BigDecimal(price), quantity, "USD");
	}
}
