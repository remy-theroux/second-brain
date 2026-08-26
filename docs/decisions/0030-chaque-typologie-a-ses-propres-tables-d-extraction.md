---
status: accepté
date: 2026-08-26
decision-makers: Rémy Theroux
---

# Chaque typologie de document a ses propres tables d'extraction

## Contexte et problème

Le texte extrait d'un document vit dans deux tables nommées d'après lui :
`knowledge_document_texts` et `knowledge_document_blocks`. Ces noms supposent qu'un document
produit du texte en blocs titrés — vrai des quatre formats acceptés, qui sont tous de
typologie textuelle (ADR-0029), et faux du prochain.

Un enregistrement sonore se découpe en segments datés, une image en régions. Ni l'un ni
l'autre ne rentre dans `knowledge_document_blocks`, ni dans sa forme, ni dans son nom. La
question se pose maintenant parce que le schéma est encore petit et qu'un renommage plus tard
coûterait davantage.

## Facteurs de décision

- `ddl-auto: validate` interdit un schéma flou : ce que la base contient doit correspondre
  exactement à ce que les entités déclarent.
- ADR-0024 a déjà refusé le JSONB, au motif que la base doit savoir lire ce qu'elle stocke et
  que RAG-5 devra référencer un bloc.
- Chaque typologie a des colonnes qui n'ont de sens que pour elle : un titre et un niveau pour
  du texte, un couple début/fin pour du son, un rectangle pour une image.
- Un `ON DELETE CASCADE` par typologie coûte une ligne de migration, et rien d'autre :
  `DeleteDocumentHandler` n'a jamais eu à connaître ces tables.

## Options envisagées

- Une table d'extraction générique, avec une colonne `payload` JSONB par typologie
- Une table par typologie, nommée par elle
- Une table unique, avec des colonnes nullables couvrant toutes les typologies

## Décision

Retenu : **une table par typologie, nommée par elle**, parce que c'est la seule qui garde à la
base la connaissance de ce qu'elle contient, et parce qu'elle découle directement d'ADR-0024.

Les deux tables du texte deviennent `knowledge_text_extractions` et `knowledge_text_blocks`,
et l'agrégat `DocumentText` devient `TextExtraction`. Le mot **extraction** plutôt que
**document** est délibéré : la ligne n'est pas un document, c'est le produit d'un traitement.
Elle naît plus tard que lui et se remplace en entier à chaque réextraction.

### Conséquences

- Bien : chaque typologie a le schéma exact de son découpage, contraintes comprises, sans
  colonne nullable qui ne servirait qu'aux autres.
- Bien : le nom d'une table dit ce qu'elle contient, et deux typologies ne peuvent pas se
  marcher dessus.
- Bien : ajouter une typologie n'impose de migrer aucune donnée existante.
- Mal : une lecture « l'extraction de ce document, quelle que soit sa typologie » doit
  interroger la typologie d'abord, puis le bon dépôt. C'est ce que fait `FindDocumentHandler`,
  et c'est un `if` qui grandira avec le nombre de typologies.
- Mal : chaque typologie paie sa migration, son agrégat et son adapter. Rien n'est mutualisé.
- Mal : le renommage a coûté une migration `V8` sur un schéma dont `V7` avait trois jours.

### Condition de réouverture

Le jour où une lecture transverse aux typologies devient courante — un écran qui liste toutes
les extractions d'un compte, tous découpages confondus. La sortie sera alors une **vue SQL**
ou une projection de lecture qui fait l'union, pas une table unique : revenir à une table
unique demanderait d'abord de rouvrir ADR-0024, qui a décidé que la base sait lire ce qu'elle
stocke.

## Avantages et inconvénients des options

### Table générique avec `payload` JSONB

- Bien : une seule table, une seule migration, et n'importe quelle typologie y entre sans
  toucher au schéma.
- Mal : c'est exactement ce qu'ADR-0024 a écarté. La base ne sait plus lire ce qu'elle
  contient, aucune contrainte ne porte sur la forme du découpage, et un bloc ne peut plus être
  référencé par une clé étrangère.
- Mal : la validation de la forme repasserait entièrement côté Java, sans filet au démarrage.

### Table unique à colonnes nullables

- Bien : une seule table, et les colonnes restent typées.
- Mal : chaque typologie ajoutée rend nullables des colonnes qui étaient obligatoires pour
  les autres. `heading` finirait `NULL` pour tout ce qui n'est pas du texte, et plus rien
  n'empêcherait une ligne de texte sans titre d'être écrite par erreur.
- Mal : la table grossirait d'une colonne à chaque typologie, sans qu'aucune ligne n'en
  utilise plus d'une poignée.

## Vérification

`ddl-auto: validate` au démarrage : une entité qui désigne une table absente fait échouer le
contexte sur `Schema validation: missing table`. Les deux cascades sont fixées par
`DeleteDocumentCascadeTest.la_suppression_d_un_document_emporte_son_texte_extrait`, dont la
seconde assertion compte les lignes de `knowledge_text_blocks` en SQL brut — le port ne montre
pas cette cascade-là.

## Pour aller plus loin

- `docs/superpowers/plans/2026-08-26-typologie-de-document-et-visualisation.md`, tâche 3
- `src/main/resources/db/migration/V8__rename_document_texts_to_text_extractions.sql`
- `knowledge/domain/entity/TextExtraction.java`,
  `knowledge/domain/port/TextExtractionRepository.java`
- ADR-0029, qui décide d'où vient la typologie
- ADR-0024, qui décrit le découpage propre à la typologie textuelle
