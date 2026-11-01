CREATE TABLE public.album (
    id bigserial NOT NULL,
    music_brainz_id varchar(255),
    title varchar(255),
    artist_id bigint,
    cover_art_url varchar(255)
);

ALTER TABLE public.album OWNER TO aficio_user;

ALTER TABLE ONLY public.album
    ADD CONSTRAINT album_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.album
    ADD CONSTRAINT fkmwc4fyyxb6tfi0qba26gcf8s1 FOREIGN KEY (artist_id) REFERENCES public.artist(id);