-- Le bilan du dernier import d'un dossier surveillé, et les fichiers qu'il a laissés dehors.
--
-- LES REJETS SONT REMPLACÉS À CHAQUE IMPORT, JAMAIS CUMULÉS : un fichier réparé doit quitter
-- la liste, et une liste qui grossit à chaque passage ne se lit plus au bout de trois. C'est
-- une collection du dossier surveillé, comme les sources d'une trace de conversation le sont
-- de leur exécution — d'où la clé primaire composite et l'absence d'identifiant propre.
--
-- UN FORMAT NON PRIS EN CHARGE N'EST PAS UN REJET. Un Drive est plein d'images et de vidéos :
-- les faire figurer ici noierait les fichiers réellement écartés, ceux dont le propriétaire
-- peut faire quelque chose. Seul le plafond de taille produit une ligne.
--
-- `last_import_status` EST NULL TANT QU'AUCUN IMPORT N'A EU LIEU : un dossier mis sous
-- surveillance et jamais importé n'a ni réussi ni échoué, et c'est ce que dit son absence.

ALTER TABLE knowledge_drive_watched_folders
    ADD COLUMN last_import_at     TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_import_status VARCHAR(16),
    ADD COLUMN last_import_error  TEXT;

CREATE TABLE knowledge_drive_import_rejections (
    watched_folder_id  UUID         NOT NULL,
    rejection_position INTEGER      NOT NULL,
    drive_file_id      VARCHAR(255) NOT NULL,
    filename           VARCHAR(255) NOT NULL,
    reason             VARCHAR(255) NOT NULL,
    CONSTRAINT pk_knowledge_drive_import_rejections PRIMARY KEY (watched_folder_id, rejection_position),
    CONSTRAINT fk_knowledge_drive_import_rejections_folder FOREIGN KEY (watched_folder_id)
        REFERENCES knowledge_drive_watched_folders (id) ON DELETE CASCADE
);
