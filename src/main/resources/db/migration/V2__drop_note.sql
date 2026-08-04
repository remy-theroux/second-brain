-- Retire la table de démonstration `note` : la feature applicative a été supprimée
-- au profit de l'architecture hexagonale du bounded context `users`.
-- V1__init.sql est conservé tel quel — une migration déjà appliquée ne se supprime pas.

DROP TABLE IF EXISTS note;
