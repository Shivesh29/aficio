ALTER TABLE public.app_user ADD COLUMN IF NOT EXISTS auth_provider varchar(50) DEFAULT 'LOCAL';
ALTER TABLE public.app_user ADD COLUMN IF NOT EXISTS google_id varchar(255);
CREATE UNIQUE INDEX IF NOT EXISTS idx_app_user_google_id ON public.app_user(google_id) WHERE google_id IS NOT NULL;

