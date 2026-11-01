CREATE TABLE public.message_thread (
    id bigserial NOT NULL,
    created_at timestamp(6) without time zone,
    first_user_id bigint,
    second_user_id bigint
);

ALTER TABLE public.message_thread OWNER TO aficio_user;

ALTER TABLE ONLY public.message_thread
    ADD CONSTRAINT message_thread_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.message_thread
    ADD CONSTRAINT ukauefelyv3h6hysqfckis8vunf UNIQUE (first_user_id, second_user_id);

