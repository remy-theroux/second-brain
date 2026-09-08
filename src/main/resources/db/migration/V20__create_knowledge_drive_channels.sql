-- L'abonnement aux notifications push de Google Drive, un par connexion.
--
-- `channel_id` ET `resource_id` : les DEUX sont nécessaires pour fermer un canal. `channels.stop`
-- les exige tous les deux, et un canal dont on aurait perdu l'un des deux ne se ferme plus — il
-- notifierait jusqu'à sa propre échéance, en doublon du canal qui l'a remplacé. Le premier est
-- notre UUID, celui que Google renvoie en en-tête `X-Goog-Channel-ID` ; le second est le sien,
-- dont il ne documente aucune longueur, d'où le `text` (même raison qu'au jeton de page, V19).
--
-- `token` est CHIFFRÉ AU REPOS, par le même dispositif que le jeton de rafraîchissement
-- (AES-256-GCM, IV aléatoire préfixé, le tout en Base64). L'arbitrage est écrit : ce jeton ne
-- donne accès à rien — il permet seulement de nous faire relire les changements d'un Drive,
-- c'est-à-dire de déclencher un travail que le balayage périodique fait de toute façon. Le
-- stocker en clair serait défendable ; le chiffrer ne coûte rien puisque le dispositif existe,
-- et « c'est un secret qui ne sert à rien » est le genre de phrase qui vieillit mal.
--
-- `expires_at` est LU CHEZ GOOGLE, jamais supposé : sept jours est un maximum, et Google peut
-- rendre une échéance plus courte sans prévenir.
--
-- CASCADE SUR LA CONNEXION : une connexion révoquée emporte son canal. Ce qu'elle n'emporte pas,
-- c'est l'abonnement côté Google, qu'aucun jeton d'accès ne permet plus d'arrêter — il expire
-- seul, et les notifications qu'il envoie entretemps reçoivent le 404 qui fait désabonner Google.

CREATE TABLE knowledge_drive_channels (
    id            UUID                     NOT NULL DEFAULT gen_random_uuid(),
    connection_id UUID                     NOT NULL,
    channel_id    VARCHAR(64)              NOT NULL,
    resource_id   TEXT,
    token         TEXT                     NOT NULL,
    opened_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_knowledge_drive_channels PRIMARY KEY (id),
    CONSTRAINT uq_knowledge_drive_channels_connection UNIQUE (connection_id),
    CONSTRAINT uq_knowledge_drive_channels_channel UNIQUE (channel_id),
    CONSTRAINT fk_knowledge_drive_channels_connection FOREIGN KEY (connection_id)
        REFERENCES knowledge_drive_connections (id) ON DELETE CASCADE
);
