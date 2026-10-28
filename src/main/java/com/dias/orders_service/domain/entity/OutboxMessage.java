package com.dias.orders_service.domain.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "outbox_messages")
@Access(AccessType.FIELD)
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxMessage {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false, updatable = false, columnDefinition = "text")
	private String topic;

	@Column(name = "partition_key", nullable = false, updatable = false, columnDefinition = "text")
	private String partitionKey;

	@Column(nullable = false, updatable = false, columnDefinition = "text")
	private String payload;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	private OutboxMessage(String topic, String partitionKey, String payload) {
		if (topic == null || partitionKey == null || payload == null) {
			throw new IllegalArgumentException("topic, partitionKey and payload are required");
		}
		this.id = UUID.randomUUID();
		this.topic = topic;
		this.partitionKey = partitionKey;
		this.payload = payload;
	}

	public static OutboxMessage create(String topic, String partitionKey, String payload) {
		return new OutboxMessage(topic, partitionKey, payload);
	}

	@PrePersist
	private void beforeInsert() {
		createdAt = Instant.now();
	}
}
