-- Le motif d'un traitement qui a échoué, affichable tel quel à l'utilisateur.
--
-- Nullable, à l'inverse de tout le reste de la table : un document qui n'a pas échoué n'a
-- pas de motif, et une chaîne vide voudrait dire « échoué sans raison ». `status` seul dit
-- qu'il y a eu échec ; cette colonne dit lequel.
--
-- 500 caractères : de quoi porter le plus long des messages de refus métier avec de la
-- marge. Ce qui dépasse est tronqué côté entité — un motif est une explication, pas une
-- trace d'exécution.

ALTER TABLE knowledge_documents
    ADD COLUMN error_message VARCHAR(500);
