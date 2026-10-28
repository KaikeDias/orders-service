CREATE TABLE outbox_messages (
    id uuid PRIMARY KEY,
    topic text NOT NULL,
    partition_key text NOT NULL,
    payload text NOT NULL,
    created_at timestamptz NOT NULL,
    published_at timestamptz
);

CREATE TABLE processed_messages (
    consumer text NOT NULL,
    message_id uuid NOT NULL,
    processed_at timestamptz NOT NULL,
    PRIMARY KEY (consumer, message_id)
);
