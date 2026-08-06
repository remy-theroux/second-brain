-- Comptes utilisateurs. Le ticket impose le minimum d'information : ni nom, ni profil.
-- `verified` reste à false tant que l'email n'est pas confirmé — la validation de
-- compte fera l'objet d'un ticket dédié.
--
-- L'email est normalisé (trim + minuscules) par le value object Email avant d'atteindre
-- la base : une contrainte UNIQUE simple suffit donc à garantir l'unicité fonctionnelle.

CREATE TABLE users_users (
    id            UUID                     NOT NULL DEFAULT gen_random_uuid(),
    email         VARCHAR(320)             NOT NULL,
    password_hash VARCHAR(255)             NOT NULL,
    verified      BOOLEAN                  NOT NULL DEFAULT false,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_users_users PRIMARY KEY (id),
    CONSTRAINT uq_users_users_email UNIQUE (email)
);
