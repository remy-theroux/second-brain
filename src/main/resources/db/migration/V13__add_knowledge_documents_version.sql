-- Le compteur de version optimiste d'un document.
--
-- Depuis RAG-7, `PUT /api/documents/{id}` mute une ligne que le worker peut être en train de
-- traiter : son `save` final réécrirait alors le nom, le format, l'empreinte et la taille de
-- la version précédente, et poserait READY sur un contenu qui n'est plus celui du stockage.
-- Hibernate compare cette colonne à l'écriture et refuse la mise à jour périmée.
--
-- NOT NULL DEFAULT 0 : les lignes déjà en base partent de la première version, et
-- `ddl-auto: validate` exige une colonne non nulle en face d'un `long` primitif.

ALTER TABLE knowledge_documents
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
