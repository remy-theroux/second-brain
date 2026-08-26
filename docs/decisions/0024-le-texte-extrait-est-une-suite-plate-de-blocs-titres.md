---
status: accepté
date: 2026-08-26
decision-makers: Rémy Theroux
---

# Le texte extrait est une suite plate de blocs titrés

## Contexte et problème

Quatre formats entrent dans la base de connaissance — PDF, DOCX, Markdown, texte brut — et
chacun dit la structure à sa façon, quand il la dit. RAG-5 découpe, RAG-6 enchaîne, RAG-7
remplace : les trois consomment ce que l'extraction produit.

Il faut donc **une** forme, et elle doit être matérialisée dans le domaine plutôt que dans
une convention de chaîne de caractères. Reste à décider laquelle, et surtout ce qu'est un
« bloc ».

## Facteurs de décision

- Le second scénario du ticket tranche seul la question : un document sans titre rend « un
  unique bloc contenant tout le texte », or un texte sans titre compte bien plusieurs
  paragraphes. Un bloc ne peut donc pas être un paragraphe.
- RAG-5 réclame des sections qu'il redécoupe lui-même en paragraphes puis en phrases, et
  préfixe chaque extrait de `Document: {filename} — Section: {heading}`. Il veut un titre par
  bloc, pas une hiérarchie.
- Ce qui n'est pas conservé à l'extraction est perdu pour de bon : réextraire toute la base
  coûte un traitement complet de chaque document.

## Options envisagées

- Suite plate de blocs titrés : `ExtractedText` = liste ordonnée de
  `TextBlock(heading, headingLevel, text)`
- Arbre de sections imbriquées, chaque section portant ses sous-sections
- Markdown canonique : une seule chaîne, le document réécrit en Markdown normalisé

## Décision

Retenu : **la suite plate de blocs titrés**, parce que le second scénario du ticket exclut
qu'un bloc soit un paragraphe, et que l'aplatissement est le seul usage que les tickets aval
prévoient.

`headingLevel` est conservé alors qu'aucun consommateur ne le demande aujourd'hui. C'est
délibéré : le niveau est une information que **seule l'extraction connaît**, tandis qu'un
chemin de section (« Chapitre 1 > Introduction ») se recalcule à tout moment à partir de lui.
Garder le niveau coûte une colonne ; le jeter coûterait une réextraction de toute la base.

### Conséquences

- Bien : une seule forme pour quatre formats, et RAG-5 reçoit exactement ce qu'il demande
  sans parcours en profondeur.
- Bien : `ExtractedText` refuse de se construire vide, donc le refus exigé par le troisième
  scénario du ticket est porté par le type et non par un contrôle qu'un extracteur pourrait
  oublier.
- Mal : **un titre immédiatement suivi d'un autre titre est perdu.** `# A` puis `## B` ne
  rend que « B », parce qu'une section sans corps est écartée. Le remède serait un chemin de
  section ; l'inventer maintenant figerait une convention d'affichage dans le domaine.
- Mal : la hiérarchie réelle d'un DOCX profond est aplatie. `headingLevel` en garde la trace,
  personne ne la lit encore.

### Condition de réouverture

Le jour où un consommateur réclame la hiérarchie complète — RAG-8 remontant un extrait avec
son chemin de section, par exemple. La sortie sera alors **un chemin recalculé depuis
`headingLevel`**, pas un arbre en base : la donnée est déjà là, seule la projection manque.

## Avantages et inconvénients des options

### Arbre de sections imbriquées

- Bien : fidèle à ce qu'un DOCX ou un Markdown profond exprime réellement.
- Mal : RAG-5 devrait l'aplatir, et l'aplatissement est son unique usage prévu. On paierait
  un parcours en profondeur et un type récursif pour une information que personne ne lit.
- Mal : une structure récursive en base demande soit une table auto-référencée, soit un
  blob — les deux plus coûteux que deux tables plates.

### Markdown canonique

- Bien : un seul type, lisible à l'œil dans une console psql.
- Mal : le domaine ne matérialise plus rien. Le « format » redevient une convention de
  chaîne de caractères, exactement ce que le porteur du ticket voulait éviter.
- Mal : RAG-5 devrait re-parser ce que RAG-4 vient de sérialiser, avec le risque que les
  deux conventions divergent.

## Vérification

`ExtractedTextTest`, `ExtractedTextBuilderTest` et `TextBlockTest` — sans Spring. La perte
d'un titre suivi d'un titre est fixée par
`ExtractedTextBuilderTest.ecarte_sans_bruit_une_section_dont_le_corps_est_vide`.

## Pour aller plus loin

- `docs/superpowers/specs/2026-08-26-extraction-texte-documents-design.md`, décision 1
- `knowledge/domain/valueobject/ExtractedText.java`,
  `knowledge/domain/valueobject/ExtractedTextBuilder.java`
- ADR-0025, qui décide de ce qu'est un document vide
