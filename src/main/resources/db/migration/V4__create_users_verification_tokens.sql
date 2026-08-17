-- Jetons de vérification d'adresse email. Seule l'empreinte salée du jeton est stockée :
-- le clair n'existe que dans la notification envoyée à l'utilisateur.
--
-- UNIQUE (user_id) documente l'invariant courant « un jeton par compte ». Le renvoi d'un
-- lien de vérification, hors périmètre ici, décidera de lever cette contrainte ou de
-- réécrire la ligne existante.

CREATE TABLE users_verification_tokens (
    id          UUID                     NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID                     NOT NULL,
    token_hash  VARCHAR(255)             NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_users_verification_tokens PRIMARY KEY (id),
    CONSTRAINT uq_users_verification_tokens_user UNIQUE (user_id),
    CONSTRAINT fk_users_verification_tokens_user FOREIGN KEY (user_id)
        REFERENCES users_users (id) ON DELETE CASCADE
);
