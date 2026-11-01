CREATE TABLE public.app_user (
    id bigserial NOT NULL,
    email varchar(255),
    password_hash varchar(255),
    username varchar(255),
    secret2fa varchar(255),
    verification_token varchar(255)
);

ALTER TABLE public.app_user OWNER TO aficio_user;

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT uk1j9d9a06i600gd43uu3km82jw UNIQUE (email);

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT uk3k4cplvh82srueuttfkwnylq0 UNIQUE (username);