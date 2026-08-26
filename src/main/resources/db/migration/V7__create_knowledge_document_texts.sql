-- Le texte extrait d'un document, dans la forme commune aux quatre formats acceptés
-- (voir ADR-0024) : une suite ordonnée de blocs, chacun rattaché au titre de sa section.
--
-- DEUX TABLES ET NON UNE COLONNE JSONB. Le format est versionné par Flyway comme le reste
-- du schéma, la base sait lire ce qu'elle stocke, et RAG-5 pourra référencer un bloc.
--
-- UN AGRÉGAT À PART DE `knowledge_documents`, et non des colonnes de plus sur lui : le
-- texte naît plus tard que le document, et il est remplacé en entier à chaque réextraction.
-- `document_id` est UNIQUE : un document a un texte, jamais deux. C'est cette contrainte qui
-- impose au handler d'effacer avant d'écrire, une redélivrance AMQP étant toujours possible.
--
-- LES DEUX CASCADES SONT LE `ON DELETE CASCADE` promis par `DeleteDocumentHandler`, dont la
-- Javadoc annonce depuis RAG-3 que « le ticket qui la créera posera un ON DELETE CASCADE, et
-- cette méthode n'aura pas à changer ». Elle ne change pas.

CREATE TABLE knowledge_document_texts (
    id           UUID                     NOT NULL DEFAULT gen_random_uuid(),
    document_id  UUID                     NOT NULL,
    extracted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_knowledge_document_texts PRIMARY KEY (id),
    CONSTRAINT uq_knowledge_document_texts_document UNIQUE (document_id),
    CONSTRAINT fk_knowledge_document_texts_document FOREIGN KEY (document_id)
        REFERENCES knowledge_documents (id) ON DELETE CASCADE
);

-- Table d'une @ElementCollection : pas de clé technique, la position dans la liste fait
-- partie de l'identité. `text` est un nom de colonne valide en PostgreSQL — TEXT y est un
-- mot-clé non réservé —, et `block_position` évite d'avoir à se poser la question pour
-- `position`, qui en est un aussi mais que Hibernate écrirait sans guillemets.
CREATE TABLE knowledge_document_blocks (
    document_text_id UUID         NOT NULL,
    block_position   INTEGER      NOT NULL,
    heading          VARCHAR(255) NOT NULL,
    heading_level    INTEGER      NOT NULL,
    text             TEXT         NOT NULL,
    CONSTRAINT pk_knowledge_document_blocks PRIMARY KEY (document_text_id, block_position),
    CONSTRAINT fk_knowledge_document_blocks_text FOREIGN KEY (document_text_id)
        REFERENCES knowledge_document_texts (id) ON DELETE CASCADE
);
