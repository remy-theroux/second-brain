-- Documents de la base de connaissance. Premier contexte borné après `users`, d'où le
-- préfixe `knowledge_`.
--
-- L'identité d'un document est son empreinte SHA-256, pas son nom : `filename` n'est qu'un
-- libellé d'affichage, et c'est UNIQUE (owner_id, checksum) qui interdit le doublon. La
-- contrainte porte le propriétaire parce que deux comptes qui déposent le même PDF
-- déposent deux documents — chacun a sa base de connaissance.
--
-- Le fichier d'origine ne vit pas ici mais sur disque, sous le nom de `id`. Rien dans le
-- schéma ne le dit : c'est l'affaire de l'adapter de stockage.
--
-- La clé étrangère traverse deux contextes bornés. Choix assumé : la base est unique, et
-- un document orphelin de son propriétaire coûterait plus cher que ce couplage.

CREATE TABLE knowledge_documents (
    id         UUID                     NOT NULL DEFAULT gen_random_uuid(),
    owner_id   UUID                     NOT NULL,
    filename   VARCHAR(255)             NOT NULL,
    format     VARCHAR(16)              NOT NULL,
    checksum   VARCHAR(64)              NOT NULL,
    size_bytes BIGINT                   NOT NULL,
    status     VARCHAR(16)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_knowledge_documents PRIMARY KEY (id),
    CONSTRAINT uq_knowledge_documents_owner_checksum UNIQUE (owner_id, checksum),
    CONSTRAINT fk_knowledge_documents_owner FOREIGN KEY (owner_id)
        REFERENCES users_users (id) ON DELETE CASCADE
);

-- La liste se lit toujours par propriétaire, du plus récent au plus ancien.
CREATE INDEX idx_knowledge_documents_owner_created
    ON knowledge_documents (owner_id, created_at DESC);
