CREATE TABLE IF NOT EXISTS public.activity (
    id bigserial NOT NULL,
    user_id bigint NOT NULL,
    actor_id bigint NOT NULL,
    actor_username varchar(255),
    type varchar(255),
    data varchar(255),
    created_at timestamp(6) without time zone,
    CONSTRAINT activity_pkey PRIMARY KEY (id)
);

