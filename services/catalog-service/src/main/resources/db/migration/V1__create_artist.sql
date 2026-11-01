CREATE TABLE public.artist (
    id bigserial NOT NULL,
    name varchar(255),
    music_brainz_id varchar(255)
);

ALTER TABLE public.artist OWNER TO aficio_user;

ALTER TABLE ONLY public.artist
    ADD CONSTRAINT artist_pkey PRIMARY KEY (id);