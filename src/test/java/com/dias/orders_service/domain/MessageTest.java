package com.dias.orders_service.domain;

import java.util.UUID;

import com.dias.orders_service.domain.entity.OutboxMessage;
import com.dias.orders_service.domain.entity.ProcessedMessage;
import com.dias.orders_service.domain.entity.ProcessedMessageId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageTest {

	@Test
	void createsOpaqueOutboxWithIndependentIdentityAndNoPublication() {
		String payload = "texto opaco\nestoque: café ☕";
		OutboxMessage message = OutboxMessage.create("topic", "key", payload);
		OutboxMessage other = OutboxMessage.create("topic", "key", payload);

		assertThat(message.id()).isNotNull().isNotEqualTo(other.id());
		assertThat(message.topic()).isEqualTo("topic");
		assertThat(message.partitionKey()).isEqualTo("key");
		assertThat(message.payload()).isEqualTo(payload);
		assertThat(message.createdAt()).isNull();
		assertThat(message.publishedAt()).isNull();
		assertThat(OutboxMessage.create("", "", "").payload()).isEmpty();
	}

	@Test
	void rejectsNullOutboxArguments() {
		assertThatThrownBy(() -> OutboxMessage.create(null, "key", "payload")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OutboxMessage.create("topic", null, "payload")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OutboxMessage.create("topic", "key", null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void preservesOriginalMessageIdentityAndScopesItByConsumer() {
		UUID messageId = UUID.randomUUID();
		UUID differentMessageId = UUID.randomUUID();
		ProcessedMessage message = ProcessedMessage.create("consumer", messageId);
		ProcessedMessageId key = new ProcessedMessageId("consumer", messageId);

		assertThat(message.id()).isEqualTo(key).hasSameHashCodeAs(key);
		assertThat(message.id().consumer()).isEqualTo("consumer");
		assertThat(message.id().messageId()).isEqualTo(messageId);
		assertThat(message.processedAt()).isNull();
		assertThat(key).isNotEqualTo(new ProcessedMessageId("other", messageId));
		assertThat(key).isNotEqualTo(new ProcessedMessageId("consumer", differentMessageId));
		assertThat(ProcessedMessage.create("", messageId).id().consumer()).isEmpty();
	}

	@Test
	void rejectsNullProcessingArguments() {
		UUID messageId = UUID.randomUUID();
		assertThatThrownBy(() -> ProcessedMessage.create(null, messageId)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ProcessedMessage.create("consumer", null)).isInstanceOf(IllegalArgumentException.class);
	}
}
