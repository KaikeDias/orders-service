package com.dias.orders_service.domain.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Embeddable
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class ProcessedMessageId implements Serializable {

	@Column(nullable = false, updatable = false, columnDefinition = "text")
	private String consumer;

	@Column(name = "message_id", nullable = false, updatable = false)
	private UUID messageId;

	public ProcessedMessageId(String consumer, UUID messageId) {
		if (consumer == null || messageId == null) {
			throw new IllegalArgumentException("consumer and messageId are required");
		}
		this.consumer = consumer;
		this.messageId = messageId;
	}
}
