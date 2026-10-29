-- Fresh-install counterpart of the legacy Hibernate schema. Existing databases
-- require the guarded, explicit operator baseline; never baseline-on-migrate.
CREATE TABLE contact_messages (
    id uuid PRIMARY KEY,
    admin_note text,
    content text NOT NULL,
    content_hash varchar(255),
    created_at timestamp(6) without time zone NOT NULL,
    email varchar(254) NOT NULL,
    last_notified_at timestamp(6) without time zone,
    notification_attempts integer NOT NULL,
    sourceip varchar(255),
    status varchar(255) NOT NULL,
    subject varchar(120) NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    user_agent text,
    CONSTRAINT contact_messages_status_check CHECK (status IN ('NEW','READ','ARCHIVED','SPAM','NOTIFICATION_FAILED'))
);
CREATE TABLE notification_deliveries (
    id uuid PRIMARY KEY,
    attempts integer NOT NULL,
    channel varchar(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    event_id uuid NOT NULL,
    event_type varchar(255) NOT NULL,
    last_error varchar(255),
    payload_snapshot text NOT NULL,
    status varchar(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT notification_deliveries_channel_check CHECK (channel IN ('TELEGRAM','EMAIL')),
    CONSTRAINT notification_deliveries_status_check CHECK (status IN ('PENDING','SENT','FAILED'))
);
