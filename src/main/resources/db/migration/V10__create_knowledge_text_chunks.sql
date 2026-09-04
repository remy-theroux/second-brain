-- Les extraits vectorisés d'un document : ce que la recherche de RAG-8 interrogera.
--
-- UNE TABLE, LE VECTEUR EN COLONNE. C'est du un-pour-un : un extrait naît avec son vecteur
-- et disparaît avec lui. Deux tables imposeraient une jointure sur le chemin chaud de la
-- recherche. Une table de vecteurs à part, nommée par le modèle qui les a produits, aurait
-- permis d'en comparer deux — c'est de la souplesse payée maintenant pour un besoin qui
-- n'existe pas, et deux modèles se comparent aussi bien sur deux bases.
--
-- LE NOM PORTE LA TYPOLOGIE, pas le mot « document » : `knowledge_text_chunks`, comme
-- `knowledge_text_extractions`. Une typologie sonore découpera en segments datés, une
-- visuelle en régions — ADR-0030.
--
-- LA DIMENSION EST FIGÉE DANS LE TYPE DE LA COLONNE, et elle doit rester égale à
-- EmbeddingPolicy.DIMENSIONS. Passer à un modèle de 768 dimensions demandera une migration
-- et une réindexation complète : c'est déjà vrai de toute façon, les vecteurs de deux
-- modèles ne se comparent pas.
--
-- LA CASCADE, pour la deuxième fois, fait que `DeleteDocumentHandler` ne bouge pas.

CREATE TABLE knowledge_text_chunks (
    id             UUID                     NOT NULL DEFAULT gen_random_uuid(),
    document_id    UUID                     NOT NULL,
    chunk_position INTEGER                  NOT NULL,
    heading        VARCHAR(255)             NOT NULL,
    text           TEXT                     NOT NULL,
    embedding      VECTOR(1024)             NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_knowledge_text_chunks PRIMARY KEY (id),
    -- Le filet de l'effacement-puis-écriture du handler : AMQP livre au moins une fois, et
    -- deux livraisons ne doivent pas doubler les extraits. L'index qui la porte sert aussi
    -- la lecture par document, `document_id` étant sa colonne de tête.
    CONSTRAINT uq_knowledge_text_chunks_position UNIQUE (document_id, chunk_position),
    CONSTRAINT fk_knowledge_text_chunks_document FOREIGN KEY (document_id)
        REFERENCES knowledge_documents (id) ON DELETE CASCADE
);

-- HNSW en cosinus : bge-m3 produit des vecteurs normalisés et s'évalue au cosinus. L'index
-- est écrit ici mais interrogé par personne — c'est RAG-8 qui écrira la requête.
CREATE INDEX idx_knowledge_text_chunks_embedding
    ON knowledge_text_chunks USING hnsw (embedding vector_cosine_ops);
