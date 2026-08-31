---
status: accepté
date: 2026-08-26
decision-makers: Rémy Theroux
---

# Un extracteur par format, plutôt qu'Apache Tika

## Contexte et problème

Quatre formats sont acceptés au dépôt : `.pdf`, `.docx`, `.md`, `.txt`. RAG-4 doit en tirer
du texte **et les titres de sections quand le format les porte**.

Le réflexe est Apache Tika : une dépendance qui lit tout, aujourd'hui et demain, derrière une
seule API. Reste à savoir si l'uniformité qu'elle offre est un gain ou une perte quand la
structure est précisément ce qu'on cherche.

## Facteurs de décision

- La structure n'est pas un sous-produit ici, c'est le livrable. Les styles `Heading1..9`
  d'un DOCX et les `#` d'un Markdown sont l'information de premier ordre.
- Tika normalise tout en un XHTML unifié. Ce qui uniformise aplatit : le style d'un
  paragraphe Word y devient un `<h2>` ou disparaît, selon le parseur et sa configuration.
- `tika-parsers-standard-package` tire des dizaines de mégaoctets de transitives, dont la
  quasi-totalité sert des formats que le projet n'accepte pas.
- Un format accepté au dépôt et un format lisible ne doivent pas pouvoir diverger, quelle
  que soit l'option retenue.

## Options envisagées

- Un extracteur par format derrière le port `DocumentTextExtractor` : PDFBox, POI (XWPF),
  commonmark-java, et le JDK seul pour le `.txt`
- Apache Tika, un seul adapter
- Tika pour le texte brut, bibliothèques dédiées pour les formats structurés

## Décision

Retenu : **un extracteur par format**, parce que chaque adapter peut alors exploiter ce que
son format sait dire au lieu de traverser un balisage qui a déjà perdu l'information.

**La contrepartie est un garde-fou obligatoire.** `ExtractDocumentTextHandler` indexe les
extracteurs par format à la construction et **échoue au démarrage** si une constante de
`DocumentFormat` n'a pas le sien — même dispositif que la table de routage des bus. Sans lui,
ajouter un format à l'énumération sans écrire son adapter donnerait un document accepté puis
irrémédiablement en échec, et le défaut ne se verrait qu'en production.

### Conséquences

- Bien : le DOCX lit les styles Word, le Markdown lit son arbre CommonMark, et le PDF a droit
  à sa propre stratégie (ADR-0027). Aucun n'est bridé par le plus pauvre des trois.
- Bien : un format nouveau est un adapter nouveau, sans toucher aux autres.
- Bien : trois dépendances ciblées plutôt qu'un paquet qui lit tout.
- Mal : trois cycles de mise à jour au lieu d'un, et trois familles d'exceptions à traduire
  en `UnreadableDocumentException`.
- Mal : un cinquième format demandera du travail, là où Tika l'aurait donné gratuitement.

### Condition de réouverture

Le jour où la liste des formats acceptés dépasse la demi-douzaine, ou qu'un format demandé
(`.epub`, `.pptx`, `.odt`) n'a pas de bibliothèque Java dédiée raisonnable. La sortie sera
alors **Tika en repli derrière les extracteurs dédiés**, pas à leur place : les formats dont
la structure compte gardent leur adapter, les autres tombent dans le lecteur générique.

## Avantages et inconvénients des options

### Apache Tika

- Bien : une seule dépendance, une seule API, et tous les formats du monde sans effort.
- Bien : la détection de type par le contenu, que le projet fait aujourd'hui à l'extension.
- Mal : l'XHTML unifié aplatit la sémantique. Ce qui est un gain quand on veut « du texte »
  est une perte quand on veut « du texte **et ses sections** ».
- Mal : des dizaines de mégaoctets de transitives pour quatre formats.

### Tika pour le texte brut, bibliothèques dédiées pour le reste

- Bien : Tika ne servirait qu'à ce qu'elle fait bien.
- Mal : Tika pour lire un `.txt` est une dépendance entière pour un `new String(octets)`.
  L'option n'a aucun cas d'usage réel dans ce projet.

## Vérification

Le démarrage de l'application : `ExtractDocumentTextHandler` lève une `IllegalStateException`
nommant le format sans extracteur. Et `gtest test --tests
"xyz.sterenn.secondbrain.knowledge.infrastructure.extraction.*"`, un test par format.

## Pour aller plus loin

- `docs/superpowers/specs/2026-08-26-extraction-texte-documents-design.md`, décision 3
- `knowledge/domain/port/DocumentTextExtractor.java`,
  `knowledge/infrastructure/extraction/`
- ADR-0027, la stratégie propre au PDF, qui n'aurait pas eu de place dans un lecteur unique
