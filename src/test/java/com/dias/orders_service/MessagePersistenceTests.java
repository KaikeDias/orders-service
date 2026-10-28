package com.dias.orders_service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Function;

import com.dias.orders_service.domain.entity.OutboxMessage;
import com.dias.orders_service.domain.entity.ProcessedMessage;
import com.dias.orders_service.domain.entity.ProcessedMessageId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MessagePersistenceTests {

	private static final String INSERT_OUTBOX = "INSERT INTO outbox_messages (id, topic, partition_key, payload, created_at) VALUES (?, ?, ?, ?, ?)";
	private static final String INSERT_PROCESSED = "INSERT INTO processed_messages (consumer, message_id, processed_at) VALUES (?, ?, ?)";

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void roundTripsOpaqueOutboxAndReloadsPublicationTimestampWithoutChangingCreation() {
		String payload = "não é JSON\nproduto café ☕";
		OutboxMessage message = OutboxMessage.create("inventory.commands", "order-key", payload);
		Instant creation = inTransaction(manager -> {
			manager.persist(message);
			manager.flush();
			manager.clear();
			OutboxMessage loaded = manager.find(OutboxMessage.class, message.id());
			assertThat(loaded.id()).isEqualTo(message.id());
			assertThat(loaded.topic()).isEqualTo(message.topic());
			assertThat(loaded.partitionKey()).isEqualTo(message.partitionKey());
			assertThat(loaded.payload()).isEqualTo(payload);
			assertThat(loaded.createdAt()).isCloseTo(message.createdAt(), within(1, ChronoUnit.MICROS));
			assertThat(loaded.publishedAt()).isNull();
			return loaded.createdAt();
		});

		Instant publication = creation.plusSeconds(10).truncatedTo(ChronoUnit.MICROS);
		jdbcTemplate.update("UPDATE outbox_messages SET published_at = ? WHERE id = ?", Timestamp.from(publication), message.id());
		inTransaction(manager -> {
			OutboxMessage loaded = manager.find(OutboxMessage.class, message.id());
			assertThat(loaded.createdAt()).isEqualTo(creation);
			assertThat(loaded.publishedAt()).isEqualTo(publication);
			assertThat(loaded.payload()).isEqualTo(payload);
			return null;
		});
	}

	@Test
	void roundTripsCompositeIdentityAndAllowsSameMessageForDifferentConsumers() {
		UUID messageId = UUID.randomUUID();
		ProcessedMessage first = ProcessedMessage.create("first", messageId);
		ProcessedMessage second = ProcessedMessage.create("second", messageId);
		Instant processed = inTransaction(manager -> {
			manager.persist(first);
			manager.persist(second);
			manager.flush();
			manager.clear();
			ProcessedMessage loaded = manager.find(ProcessedMessage.class, new ProcessedMessageId("first", messageId));
			assertThat(loaded.id()).isEqualTo(first.id()).hasSameHashCodeAs(first.id());
			assertThat(loaded.id().messageId()).isEqualTo(messageId);
			assertThat(loaded.processedAt()).isCloseTo(first.processedAt(), within(1, ChronoUnit.MICROS));
			assertThat(manager.find(ProcessedMessage.class, second.id()).id().consumer()).isEqualTo("second");
			return loaded.processedAt();
		});
		inTransaction(manager -> {
			assertThat(manager.find(ProcessedMessage.class, first.id()).processedAt()).isEqualTo(processed);
			return null;
		});
	}

	@Test
	void rejectsNullInEveryRequiredMessageColumnInIsolatedTransactions() {
		for (int index = 0; index < 5; index++) {
			Object[] values = {UUID.randomUUID(), "topic", "key", "payload", Timestamp.from(Instant.now())};
			values[index] = null;
			assertThatThrownBy(() -> jdbcTemplate.update(INSERT_OUTBOX, values)).isInstanceOf(DataIntegrityViolationException.class);
		}
		for (int index = 0; index < 3; index++) {
			Object[] values = {"consumer", UUID.randomUUID(), Timestamp.from(Instant.now())};
			values[index] = null;
			assertThatThrownBy(() -> jdbcTemplate.update(INSERT_PROCESSED, values)).isInstanceOf(DataIntegrityViolationException.class);
		}
	}

	@Test
	void duplicateKeysRollbackAllWorkAndPreserveOriginalRecords() {
		OutboxMessage original = OutboxMessage.create("topic", "key", "original");
		UUID processedId = UUID.randomUUID();
		ProcessedMessage processed = ProcessedMessage.create("duplicate-test", processedId);
		inTransaction(manager -> {
			manager.persist(original);
			manager.persist(processed);
			return null;
		});

		OutboxMessage rolledBack = OutboxMessage.create("topic", "key", "must rollback");
		assertThatThrownBy(() -> inTransaction(manager -> {
			manager.persist(rolledBack);
			manager.flush();
			manager.createNativeQuery(INSERT_OUTBOX)
					.setParameter(1, original.id()).setParameter(2, "other")
					.setParameter(3, "other").setParameter(4, "overwrite")
					.setParameter(5, Instant.now()).executeUpdate();
			return null;
		})).isInstanceOf(jakarta.persistence.PersistenceException.class);

		OutboxMessage alsoRolledBack = OutboxMessage.create("topic", "key", "must rollback too");
		assertThatThrownBy(() -> inTransaction(manager -> {
			manager.persist(alsoRolledBack);
			manager.flush();
			manager.createNativeQuery(INSERT_PROCESSED)
					.setParameter(1, processed.id().consumer()).setParameter(2, processedId)
					.setParameter(3, Instant.now()).executeUpdate();
			return null;
		})).isInstanceOf(jakarta.persistence.PersistenceException.class);

		inTransaction(manager -> {
			assertThat(manager.find(OutboxMessage.class, rolledBack.id())).isNull();
			assertThat(manager.find(OutboxMessage.class, alsoRolledBack.id())).isNull();
			assertThat(manager.find(OutboxMessage.class, original.id()).payload()).isEqualTo("original");
			assertThat(manager.find(ProcessedMessage.class, processed.id()).processedAt())
					.isCloseTo(processed.processedAt(), within(1, ChronoUnit.MICROS));
			return null;
		});
	}

	private <T> T inTransaction(Function<EntityManager, T> work) {
		try (EntityManager manager = entityManagerFactory.createEntityManager()) {
			EntityTransaction transaction = manager.getTransaction();
			transaction.begin();
			try {
				T result = work.apply(manager);
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
}
