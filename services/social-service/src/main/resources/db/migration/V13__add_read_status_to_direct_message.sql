ALTER TABLE public.direct_message
    ADD COLUMN IF NOT EXISTS is_read boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS read_at timestamp(6) without time zone;

CREATE INDEX IF NOT EXISTS idx_direct_message_recipient_unread
    ON public.direct_message (recipient_id, is_read);

CREATE INDEX IF NOT EXISTS idx_direct_message_thread_read
    ON public.direct_message (recipient_id, sender_id, is_read);

