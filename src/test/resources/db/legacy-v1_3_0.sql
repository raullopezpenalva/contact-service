-- Synthetic schema-only fixture captured from the unchanged v1.3.0 entity
-- mappings using a disposable PostgreSQL 15 database; not a production backup.
--
-- PostgreSQL database dump
--


-- Dumped from database version 15.19
-- Dumped by pg_dump version 15.19




--
-- Name: contact_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contact_messages (
    id uuid NOT NULL,
    admin_note text,
    content text NOT NULL,
    content_hash character varying(255),
    created_at timestamp(6) without time zone NOT NULL,
    email character varying(254) NOT NULL,
    last_notified_at timestamp(6) without time zone,
    notification_attempts integer NOT NULL,
    sourceip character varying(255),
    status character varying(255) NOT NULL,
    subject character varying(120) NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    user_agent text,
    CONSTRAINT contact_messages_status_check CHECK (((status)::text = ANY ((ARRAY['NEW'::character varying, 'READ'::character varying, 'ARCHIVED'::character varying, 'SPAM'::character varying, 'NOTIFICATION_FAILED'::character varying])::text[])))
);


--
-- Name: notification_deliveries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_deliveries (
    id uuid NOT NULL,
    attempts integer NOT NULL,
    channel character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    event_id uuid NOT NULL,
    event_type character varying(255) NOT NULL,
    last_error character varying(255),
    payload_snapshot text NOT NULL,
    status character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT notification_deliveries_channel_check CHECK (((channel)::text = ANY ((ARRAY['TELEGRAM'::character varying, 'EMAIL'::character varying])::text[]))),
    CONSTRAINT notification_deliveries_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'SENT'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: contact_messages contact_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contact_messages
    ADD CONSTRAINT contact_messages_pkey PRIMARY KEY (id);


--
-- Name: notification_deliveries notification_deliveries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_deliveries
    ADD CONSTRAINT notification_deliveries_pkey PRIMARY KEY (id);


--
-- PostgreSQL database dump complete
--
