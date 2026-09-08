-- D'où vient un document : déposé à la main, ou importé d'un dossier Drive surveillé.
--
-- `DEFAULT 'MANUAL'` VAUT POUR LES LIGNES DÉJÀ EN BASE : tout ce qui est entré avant l'import
-- Drive a été déposé à la main, et rien d'autre ne pourrait le dire rétroactivement.
--
-- L'UNIQUE (owner_id, drive_file_id) NE GÊNE PAS LES DÉPÔTS MANUELS : les NULL de PostgreSQL
-- sont distincts entre eux, donc autant de documents à la main que l'on veut cohabitent sous
-- un `drive_file_id` absent. Elle porte le propriétaire pour la même raison que
-- `(owner_id, checksum)` : deux comptes qui surveillent le même Drive partagé importent chacun
-- leur document.
--
-- `drive_modified_time` NE SERT À RIEN AVANT DRIVE-5, et c'est délibéré : c'est par elle qu'un
-- Google Doc natif se comparera, jamais par son empreinte, et l'ajouter plus tard obligerait à
-- rebalayer tout le Drive pour les documents déjà importés.

ALTER TABLE knowledge_documents
    ADD COLUMN source              VARCHAR(16)              NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN drive_file_id       VARCHAR(255),
    ADD COLUMN drive_web_view_link TEXT,
    ADD COLUMN drive_modified_time TIMESTAMP WITH TIME ZONE;

ALTER TABLE knowledge_documents
    ADD CONSTRAINT uq_knowledge_documents_owner_drive_file UNIQUE (owner_id, drive_file_id);
