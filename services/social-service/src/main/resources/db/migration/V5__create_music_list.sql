CREATE TABLE public.music_list (
    id bigserial NOT NULL,
    created_at timestamp(6) without time zone,
    description varchar(255),
    is_public boolean NOT NULL,
    title varchar(255),
    owner_id bigint
);

ALTER TABLE public.music_list OWNER TO aficio_user;

ALTER TABLE ONLY public.music_list
    ADD CONSTRAINT music_list_pkey PRIMARY KEY (id);

