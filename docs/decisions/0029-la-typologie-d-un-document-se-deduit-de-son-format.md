---
status: accepté
date: 2026-08-26
decision-makers: Rémy Theroux
---

# La typologie d'un document se déduit de son format, elle n'est pas stockée

## Contexte et problème

La base de connaissance accueille quatre formats — `.pdf`, `.docx`, `.md`, `.txt` — qui se
découpent tous de la même façon : une suite de blocs titrés (ADR-0024). D'autres viendront
qui ne s'y ramènent pas : un enregistrement sonore se découpe en segments datés, une image en
régions.

Il faut donc nommer cette différence quelque part, et c'est `DocumentType`. Reste une
question qui n'a rien d'évident : `knowledge_documents` doit-elle porter une colonne `type` ?

## Facteurs de décision

- Une valeur dérivée **et** stockée peut diverger de sa source. Une ligne dont `format` dit
  `DOCX` et `type` dit `AUDIO` n'aurait aucun sens, et rien en base ne l'empêcherait.
- Le format **détermine** la typologie : un `.docx` ne peut pas cesser d'être textuel. Ce
  n'est pas une propriété du document, c'est une propriété de son format.
- Aucune lecture ne filtre par typologie aujourd'hui. Le seul besoin est de choisir un
  traitement et une projection, à partir d'un document déjà chargé.
- Une colonne coûte une migration, une valeur à écrire au dépôt, et un rattrapage sur les
  lignes existantes.

## Options envisagées

- Colonne `type` sur `knowledge_documents`, écrite au dépôt à partir du format
- Typologie dérivée, portée par le constructeur de `DocumentFormat`
- Rien du tout : un `switch` sur le format là où le besoin se pose

## Décision

Retenu : **la typologie dérivée du format**, parce que le format la détermine entièrement et
qu'une colonne ne ferait que rendre ce lien falsifiable.

`DocumentFormat` prend un second paramètre de constructeur, et `DocumentFormat.of(DocumentType)`
rend les formats d'une typologie. Le lien est déclaré une seule fois, à l'endroit où un
format s'ajoute — donc impossible à oublier, le constructeur l'exigeant.

### Conséquences

- Bien : aucune migration, aucune donnée à rattraper, et un seul endroit qui déclare le lien.
- Bien : ajouter un format sans dire sa typologie ne compile pas.
- Bien : la typologie ne peut jamais contredire le format, puisqu'elle en sort.
- Mal : « tous mes documents sonores » se lira par un `IN (…)` sur `format`, construit depuis
  `DocumentFormat.of(…)`, pas par un prédicat direct.
- Mal : une requête SQL écrite à la main, hors du code Java — une exploration en psql, un
  tableau de bord — devra recopier la liste des formats d'une typologie. C'est une copie de
  plus, du même genre que celles qu'assume ADR-0022.

### Condition de réouverture

Le jour où une lecture doit filtrer par typologie sur un volume qui rend l'`IN (…)` coûteux,
ou le jour où deux documents du même format doivent pouvoir porter deux typologies
différentes — ce qui voudrait dire que le format n'est plus ce qui la détermine, et que cette
décision a perdu sa prémisse.

## Avantages et inconvénients des options

### Colonne `type` sur `knowledge_documents`

- Bien : filtrer par typologie devient un prédicat direct, indexable.
- Bien : une requête SQL écrite à la main n'a rien à recopier.
- Mal : la colonne peut mentir. Rien n'empêche un `UPDATE` de la désaccorder du format, et
  le code ferait alors confiance à une valeur fausse.
- Mal : une migration et un rattrapage des lignes existantes, pour une valeur que le code
  sait déjà calculer.

### Un `switch` sur le format, sans énumération

- Bien : rien à créer du tout.
- Mal : la typologie n'existerait nulle part comme concept. Chaque endroit qui en a besoin la
  redécouperait à sa façon, et le jour où un format sonore arrive, il faudrait retrouver tous
  ces `switch` — dont aucun ne se signale.

## Vérification

`DocumentFormatTest.chaque_format_annonce_sa_typologie` empêche d'ajouter un format sans
typologie, et `les_quatre_formats_acceptes_se_decoupent_tous_en_texte` fixe l'état
d'aujourd'hui. L'absence de colonne, elle, n'est vérifiée par rien d'autre que
`ddl-auto: validate`, qui refuserait un attribut d'entité sans colonne — mais qui ne dirait
rien d'une colonne ajoutée sans attribut.

## Pour aller plus loin

- `docs/superpowers/plans/2026-08-26-typologie-de-document-et-visualisation.md`, tâche 1
- `knowledge/domain/valueobject/DocumentType.java`,
  `knowledge/domain/valueobject/DocumentFormat.java`
- ADR-0030, qui décide de ce que la typologie change au schéma
- ADR-0024, qui décrit le découpage propre à la typologie textuelle
