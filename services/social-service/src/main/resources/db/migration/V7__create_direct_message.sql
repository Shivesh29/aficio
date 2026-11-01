CREATE TABLE public.direct_message (
    id bigserial NOT NULL,
    content text,
    sent_at timestamp(6) without time zone,
    recipient_id bigint,
    sender_id bigint
);

ALTER TABLE public.direct_message OWNER TO aficio_user;

ALTER TABLE ONLY public.direct_message
    ADD CONSTRAINT direct_message_pkey PRIMARY KEY (id);

