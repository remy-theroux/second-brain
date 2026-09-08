-- La connexion à un compte Google Drive et la demande d'autorisation qui la précède.
--
-- UNE CONNEXION PAR COMPTE : c'est `owner_id UNIQUE` qui porte la règle, là où elle ne peut
-- pas être contournée. Se reconnecter remplace la ligne, ça n'en ajoute pas une seconde.
--
-- `refresh_token` est CHIFFRÉ AU REPOS — AES-256-GCM, IV aléatoire préfixé, le tout en
-- Base64 — par RefreshTokenAttributeConverter. Le schéma n'en dit rien : c'est un `text`
-- parce qu'un chiffré déborde d'un varchar(255), et c'est l'affaire du converter.
--
-- `state` est en CLAIR, et c'est voulu : ce n'est pas un secret comme le jeton de
-- vérification, le connaître ne donne rien. C'est un nonce anti-CSRF, à usage unique
-- (`consumed_at`) et périmable (`created_at` + la durée de DriveAuthorizationPolicy), seule
-- protection d'un flux dont le retour n'est pas authentifié (ADR-0003).
--
-- Les deux clés étrangères traversent deux contextes bornés, comme celle des documents :
-- la base est unique, et une connexion orpheline de son propriétaire coûterait plus cher.

CREATE TABLE knowledge_drive_connections (
    id            UUID                     NOT NULL DEFAULT gen_random_uuid(),
    owner_id      UUID                     NOT NULL,
    google_email  VARCHAR(320)             NOT NULL,
    refresh_token TEXT                     NOT NULL,
    status        VARCHAR(32)              NOT NULL,
    connected_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_knowledge_drive_connections PRIMARY KEY (id),
    CONSTRAINT uq_knowledge_drive_connections_owner UNIQUE (owner_id),
    CONSTRAINT fk_knowledge_drive_connections_owner FOREIGN KEY (owner_id)
        REFERENCES users_users (id) ON DELETE CASCADE
);

CREATE TABLE knowledge_drive_authorization_requests (
    id          UUID                     NOT NULL DEFAULT gen_random_uuid(),
    owner_id    UUID                     NOT NULL,
    state       VARCHAR(64)              NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_knowledge_drive_authorization_requests PRIMARY KEY (id),
    CONSTRAINT uq_knowledge_drive_authorization_requests_state UNIQUE (state),
    CONSTRAINT fk_knowledge_drive_authorization_requests_owner FOREIGN KEY (owner_id)
        REFERENCES users_users (id) ON DELETE CASCADE
);
