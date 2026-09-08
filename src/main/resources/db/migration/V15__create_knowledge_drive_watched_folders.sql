-- Les dossiers Drive dont le contenu alimente la base de connaissance.
--
-- UN DOSSIER SE DÉSIGNE PAR SON IDENTIFIANT DRIVE, jamais par son chemin : le renommer ou le
-- déplacer ne casse pas la surveillance, exactement comme l'identité d'un document est son
-- contenu et non son nom. `name` n'est qu'une recopie d'affichage, prise au moment de la mise
-- sous surveillance : elle peut vieillir, elle ne sert à rien d'autre qu'à l'écran.
--
-- ON DELETE CASCADE SUR LA CONNEXION : déconnecter un compte Google emporte ses dossiers
-- surveillés, et PAS les documents qu'ils ont apportés — c'est la promesse de DRIVE-1. Le
-- rattachement se fait donc à la connexion et non au propriétaire.
--
-- L'UNIQUE est le filet sous le contrôle de couverture applicatif, comme
-- `(owner_id, checksum)` l'est pour un document : il ne se referme que sur deux mises sous
-- surveillance simultanées du même dossier.

CREATE TABLE knowledge_drive_watched_folders (
    id              UUID                     NOT NULL DEFAULT gen_random_uuid(),
    connection_id   UUID                     NOT NULL,
    drive_folder_id VARCHAR(255)             NOT NULL,
    name            VARCHAR(255)             NOT NULL,
    watched_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_knowledge_drive_watched_folders PRIMARY KEY (id),
    CONSTRAINT uq_knowledge_drive_watched_folders_folder UNIQUE (connection_id, drive_folder_id),
    CONSTRAINT fk_knowledge_drive_watched_folders_connection FOREIGN KEY (connection_id)
        REFERENCES knowledge_drive_connections (id) ON DELETE CASCADE
);
