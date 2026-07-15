-- Migration initiale : table de démonstration `note`.
-- gen_random_uuid() est natif à partir de PostgreSQL 13.

CREATE TABLE note (
    id         UUID                     NOT NULL DEFAULT gen_random_uuid(),
    title      VARCHAR(255)             NOT NULL,
    content    TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_note PRIMARY KEY (id)
);
