CREATE TABLE public.review_comment (
    id bigserial NOT NULL,
    content text,
    created_at timestamp(6) without time zone,
    review_id bigint,
    user_id bigint
);

ALTER TABLE public.review_comment OWNER TO aficio_user;

ALTER TABLE ONLY public.review_comment
    ADD CONSTRAINT review_comment_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.review_comment
    ADD CONSTRAINT fk9j7pkcpuestrwjre1a1902biu FOREIGN KEY (review_id) REFERENCES public.review(id);

