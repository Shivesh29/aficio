CREATE TABLE public.follow (
    id bigserial NOT NULL,
    created_at timestamp(6) without time zone,
    follower_id bigint,
    following_id bigint
);

ALTER TABLE public.follow OWNER TO aficio_user;

ALTER TABLE ONLY public.follow
    ADD CONSTRAINT follow_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.follow
    ADD CONSTRAINT ukfb7ln73htigy8q3cx7ebyho3c UNIQUE (follower_id, following_id);

