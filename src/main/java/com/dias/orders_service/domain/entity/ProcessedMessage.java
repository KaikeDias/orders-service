package com.dias.orders_service.domain.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "processed_messages")
@Access(AccessType.FIELD)
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedMessage {

	@EmbeddedId
	private ProcessedMessageId id;

	@Column(name = "processed_at", nullable = false, updatable = false)
	private Instant processedAt;

	private ProcessedMessage(String consumer, UUID messageId) {
		this.id = new ProcessedMessageId(consumer, messageId);
	}

	public static ProcessedMessage create(String consumer, UUID messageId) {
		return new ProcessedMessage(consumer, messageId);
	}

	@PrePersist
	private void beforeInsert() {
		processedAt = Instant.now();
	}
}
