-- Le type `vector`, ses opérateurs de distance et ses index.
--
-- L'image pgvector FOURNIT l'extension ; elle ne l'active sur aucune base. Sans ce
-- CREATE EXTENSION, `'[1,0,0]'::vector` échoue sur « type "vector" does not exist ».
--
-- Aucune table ici : celle des extraits arrive avec l'entité qui la mappe, dans le
-- livrable suivant. Une table sans entité est du poids mort, et `ddl-auto: validate`
-- n'aurait rien à valider.
CREATE EXTENSION IF NOT EXISTS vector;
