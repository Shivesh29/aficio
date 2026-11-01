CREATE TABLE public.notification (
    id bigserial NOT NULL,
    user_id bigint NOT NULL,
    type varchar(255),
    message varchar(255),
    data varchar(255),
    read boolean NOT NULL DEFAULT FALSE,
    created_at timestamp(6) without time zone
);

ALTER TABLE public.notification OWNER TO aficio_user;

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);

