-- La trace d'une conversation avec l'agent : ce que RAG-14 interrogera pour évaluer la
-- qualité des réponses.
--
-- CE N'EST PAS DE L'HISTORIQUE. Rien ne relit ces lignes dans un prompt : elles sont lues
-- par un humain ou par une mesure. L'historique de conversation est hors périmètre, et le
-- rester suppose que personne ne « réutilise » cette table pour l'implémenter.
--
-- AUCUNE CLÉ ÉTRANGÈRE VERS LES EXTRAITS. Le texte cité est RECOPIÉ. Sans quoi la
-- suppression d'un document ferait disparaître les sources d'une réponse déjà donnée, ou
-- bloquerait la suppression. `document_id` est conservé sans contrainte, parce qu'il sert au
-- diagnostic et qu'il a le droit de ne plus désigner personne.
--
-- LA VERSION DE L'AGENT EST UNE COLONNE. Sans elle, RAG-14 comparerait des réponses produites
-- par deux consignes différentes en croyant mesurer autre chose.

CREATE TABLE knowledge_agent_runs (
    id              UUID                     NOT NULL DEFAULT gen_random_uuid(),
    owner_id        UUID                     NOT NULL,
    agent_name      VARCHAR(64)              NOT NULL,
    agent_version   VARCHAR(16)              NOT NULL,
    question        TEXT                     NOT NULL,
    answer          TEXT                     NOT NULL,
    verdict         VARCHAR(32)              NOT NULL,
    turns           INTEGER                  NOT NULL,
    duration_millis BIGINT                   NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_knowledge_agent_runs PRIMARY KEY (id),
    CONSTRAINT fk_knowledge_agent_runs_owner FOREIGN KEY (owner_id)
        REFERENCES users_users (id) ON DELETE CASCADE
);

CREATE INDEX idx_knowledge_agent_runs_owner
    ON knowledge_agent_runs (owner_id, created_at DESC);

-- Les requêtes que l'agent a RÉELLEMENT émises, dans l'ordre : c'est ce qui distingue une
-- mauvaise réponse d'une mauvaise reformulation.
CREATE TABLE knowledge_agent_run_searches (
    agent_run_id    UUID    NOT NULL,
    search_position INTEGER NOT NULL,
    search_query    TEXT    NOT NULL,
    CONSTRAINT pk_knowledge_agent_run_searches PRIMARY KEY (agent_run_id, search_position),
    CONSTRAINT fk_knowledge_agent_run_searches_run FOREIGN KEY (agent_run_id)
        REFERENCES knowledge_agent_runs (id) ON DELETE CASCADE
);

CREATE TABLE knowledge_agent_run_sources (
    agent_run_id    UUID         NOT NULL,
    source_position INTEGER      NOT NULL,
    source_number   INTEGER      NOT NULL,
    document_id     UUID         NOT NULL,
    filename        VARCHAR(255) NOT NULL,
    heading         VARCHAR(255) NOT NULL,
    text            TEXT         NOT NULL,
    CONSTRAINT pk_knowledge_agent_run_sources PRIMARY KEY (agent_run_id, source_position),
    CONSTRAINT fk_knowledge_agent_run_sources_run FOREIGN KEY (agent_run_id)
        REFERENCES knowledge_agent_runs (id) ON DELETE CASCADE
);
