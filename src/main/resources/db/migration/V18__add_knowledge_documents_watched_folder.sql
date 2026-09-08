-- Le dossier surveillé par lequel un document est entré dans la base.
--
-- POURQUOI UNE COLONNE ET PAS UN COMPTAGE DÉRIVÉ : l'écran d'un dossier surveillé annonce
-- combien de documents il a apportés. Sans cette colonne, ce nombre ne se calcule qu'en
-- rebalayant le Drive pour savoir quels fichiers sont sous ce dossier — un appel réseau pour
-- afficher une liste.
--
-- AUCUNE CLÉ ÉTRANGÈRE, délibérément. Cesser de surveiller un dossier ne retire pas les
-- documents qu'il a apportés (c'est la promesse de DRIVE-2), et déconnecter un compte Google
-- emporte ses dossiers surveillés par le CASCADE de V15 : une clé étrangère obligerait à
-- choisir entre supprimer ces documents et effacer la trace de leur origine. C'est la même
-- réponse que pour `knowledge_agent_run_sources.document_id`, qui désigne un extrait sans
-- exiger qu'il existe encore (ADR-0006, poussé d'un cran).
--
-- NULL POUR TOUT DÉPÔT MANUEL, et pour les documents importés avant cette migration : rien ne
-- pourrait dire rétroactivement par quel dossier ils sont passés.

ALTER TABLE knowledge_documents
    ADD COLUMN watched_folder_id UUID;

-- Le comptage se fait par propriétaire, groupé par dossier, et ignore les dépôts manuels.
CREATE INDEX idx_knowledge_documents_watched_folder
    ON knowledge_documents (owner_id, watched_folder_id)
    WHERE watched_folder_id IS NOT NULL;
