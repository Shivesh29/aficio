CREATE TABLE public.review_like (
    id bigserial NOT NULL,
    created_at timestamp(6) without time zone,
    review_id bigint,
    user_id bigint
);

ALTER TABLE public.review_like OWNER TO aficio_user;

ALTER TABLE ONLY public.review_like
    ADD CONSTRAINT review_like_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.review_like
    ADD CONSTRAINT ukkv2edtmavhobw9aeu18khjer5 UNIQUE (user_id, review_id);

ALTER TABLE ONLY public.review_like
    ADD CONSTRAINT fk68am9vk1s1e8n1v873meqkk0k FOREIGN KEY (review_id) REFERENCES public.review(id);

