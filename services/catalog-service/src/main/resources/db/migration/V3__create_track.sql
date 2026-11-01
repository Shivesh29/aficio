CREATE TABLE public.track (
    id bigserial NOT NULL,
    length_milliseconds integer,
    music_brainz_id varchar(255),
    "position" integer,
    title varchar(255),
    album_id bigint
);

ALTER TABLE public.track OWNER TO aficio_user;

ALTER TABLE ONLY public.track
    ADD CONSTRAINT track_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.track
    ADD CONSTRAINT fkaxm9pbgk7ptorfyk3o6911q05 FOREIGN KEY (album_id) REFERENCES public.album(id);