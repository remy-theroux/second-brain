-- Les deux tables du texte extrait portent désormais leur TYPOLOGIE, pas le mot « document ».
--
-- `knowledge_document_texts` supposait qu'un document produit du texte en blocs titrés.
-- C'est vrai des quatre formats acceptés — tous de typologie TEXTUAL — et faux du prochain :
-- un enregistrement sonore se découpe en segments datés, une image en régions. Nommées par
-- leur typologie, ces tables laissent la place aux suivantes sans qu'aucune ne mente sur ce
-- qu'elle contient — ADR-0030.
--
-- « extraction » et non « document » : la ligne n'est pas un document, c'est le produit d'un
-- traitement. Elle naît plus tard que lui et se remplace en entier à chaque réextraction.
--
-- RENAME et non DROP + CREATE : les données déjà extraites survivent, et les deux
-- ON DELETE CASCADE de V7 sont conservés tels quels — PostgreSQL renomme la contrainte,
-- pas son comportement.

ALTER TABLE knowledge_document_texts RENAME TO knowledge_text_extractions;
ALTER TABLE knowledge_document_blocks RENAME TO knowledge_text_blocks;

ALTER TABLE knowledge_text_blocks RENAME COLUMN document_text_id TO text_extraction_id;

-- Les contraintes gardent sinon le nom de l'ancienne table, et un message d'erreur de
-- production désignerait un objet qui n'existe plus. `RENAME CONSTRAINT` renomme aussi
-- l'index qui porte une clé primaire ou une contrainte d'unicité.
ALTER TABLE knowledge_text_extractions
    RENAME CONSTRAINT pk_knowledge_document_texts TO pk_knowledge_text_extractions;
ALTER TABLE knowledge_text_extractions
    RENAME CONSTRAINT uq_knowledge_document_texts_document TO uq_knowledge_text_extractions_document;
ALTER TABLE knowledge_text_extractions
    RENAME CONSTRAINT fk_knowledge_document_texts_document TO fk_knowledge_text_extractions_document;

ALTER TABLE knowledge_text_blocks
    RENAME CONSTRAINT pk_knowledge_document_blocks TO pk_knowledge_text_blocks;
ALTER TABLE knowledge_text_blocks
    RENAME CONSTRAINT fk_knowledge_document_blocks_text TO fk_knowledge_text_blocks_extraction;
