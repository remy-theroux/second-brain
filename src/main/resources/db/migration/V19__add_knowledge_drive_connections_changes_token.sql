-- Le jeton de page du flux de changements du Drive, d'où repart la synchronisation suivante.
--
-- NULL TANT QU'AUCUN TOUR N'A ABOUTI, et c'est la valeur qui compte : elle dit « je ne sais pas
-- où j'en suis », donc « demande à Google un point de départ ». Une connexion créée avant cette
-- migration est exactement dans ce cas, et n'a rien à rattraper.
--
-- IL NE S'ÉCRIT QU'APRÈS UN TOUR COMPLET RÉUSSI. Conservé au fil de l'eau, il ferait perdre les
-- changements d'une page dont le traitement a échoué : le tour suivant repartirait après elle
-- sans que rien ne le signale.
--
-- TEXT ET NON VARCHAR : Google ne documente aucune longueur pour ce jeton, et un `varchar` trop
-- court se traduirait par une troncature silencieuse, donc par un jeton que Google refuse — et
-- un jeton refusé est un balayage complet de plus.

ALTER TABLE knowledge_drive_connections
    ADD COLUMN changes_page_token TEXT;
