CREATE TABLE public.activity (
    id bigserial NOT NULL,
    user_id bigint NOT NULL,
    actor_id bigint NOT NULL,
    type varchar(255),
    data varchar(255),
    created_at timestamp(6) without time zone
);

ALTER TABLE public.activity OWNER TO aficio_user;

ALTER TABLE ONLY public.activity
    ADD CONSTRAINT activity_pkey PRIMARY KEY (id);

