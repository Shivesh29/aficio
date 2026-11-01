CREATE TABLE public.music_list_item (
    id bigserial NOT NULL,
    "position" integer,
    album_id bigint,
    list_id bigint
);

ALTER TABLE public.music_list_item OWNER TO aficio_user;

ALTER TABLE ONLY public.music_list_item
    ADD CONSTRAINT music_list_item_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.music_list_item
    ADD CONSTRAINT ukncogle8e9j98v6pwsf2ardna1 UNIQUE (list_id, album_id);

ALTER TABLE ONLY public.music_list_item
    ADD CONSTRAINT fkhu797c6coqrfxgnxkeulrnnti FOREIGN KEY (list_id) REFERENCES public.music_list(id);