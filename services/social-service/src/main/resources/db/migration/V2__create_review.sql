CREATE TABLE public.review (
    id bigserial NOT NULL,
    content text,
    created_at timestamp(6) without time zone,
    rating double precision,
    album_id bigint,
    user_id bigint
);

ALTER TABLE public.review OWNER TO aficio_user;

ALTER TABLE ONLY public.review
    ADD CONSTRAINT review_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.review
    ADD CONSTRAINT ukfhoof0g41p3mkex0eylimxy60 UNIQUE (user_id, album_id);

