---
status: accepté
date: 2026-08-26
decision-makers: Rémy Theroux
---

# Les titres d'un PDF sans signets sont devinés à la taille de police

## Contexte et problème

Un PDF ne porte **aucune** sémantique de titre. Là où un DOCX nomme le style d'un paragraphe
et où un Markdown écrit `##`, un PDF n'a que des glyphes posés à des coordonnées : ce qu'un
lecteur humain voit comme un titre n'est, dans le fichier, qu'une suite de caractères plus
grands que les autres.

Le ticket demande pourtant « un texte lisible découpé en sections » pour **chacun** des
formats acceptés, et le PDF est le format le plus fréquent d'une base de connaissance
personnelle.

## Facteurs de décision

- Un PDF peut porter un **sommaire** (outline, les signets du volet latéral). Quand il
  existe, il est écrit par l'auteur : c'est de l'information, pas une déduction.
- La plupart des PDF personnels — un export de traitement de texte, une facture, un article
  téléchargé — n'en ont pas.
- Toute déduction à partir de la mise en page est un pari. Le pari peut être perdu **en
  silence** : un titre fantaisiste ne lève aucune exception.
- PDFBox ne sait extraire qu'en **plages de pages**. Aucune stratégie ne peut découper plus
  finement sans réécrire l'analyse de mise en page.

## Options envisagées

- Les signets d'abord, la taille de police en repli
- Les signets seuls ; un PDF sans sommaire rend un unique bloc
- L'heuristique de police seule, sommaire ignoré

## Décision

Retenu : **les signets d'abord, la taille de police en repli**, parce qu'un sommaire écrit
par l'auteur vaut mieux que toute mesure, et qu'un unique bloc pour la majorité des PDF
personnels ne tiendrait pas la promesse du ticket.

L'heuristique est explicite et tient en trois conditions, qu'aucune ne suffit à remplir
seule. Une ligne est un titre si :

1. elle n'est pas vide ;
2. elle fait au plus **120 caractères** — au-delà, c'est une phrase mise en avant ;
3. elle est écrite au moins **15 % plus grand** que le corps du document.

Le corps n'est pas la taille la plus fréquente ligne à ligne, mais **celle qui porte le plus
de caractères** : un document de trente titres et de quarante lignes de corps ferait mentir
le décompte par lignes, jamais celui par caractères. Les tailles de titre distinctes, rangées
de la plus grande à la plus petite, donnent les niveaux 1, 2, 3…

### Conséquences

- Bien : un PDF sans sommaire rend tout de même des sections, et la promesse du ticket tient
  pour les quatre formats.
- Bien : quand le sommaire existe, on ne devine rien.
- Mal : **les seuils sont arbitraires.** 15 % et 120 caractères se défendent par l'usage, pas
  par la théorie, et un document exotique produira des titres fantaisistes **en silence**.
- Mal : deux chemins de code, deux jeux de tests.
- Mal : **granularité page** sur le chemin par signets. Deux signets tombant sur la même page
  sont fusionnés sous le titre du premier — sans quoi le texte de cette page serait rendu
  deux fois, et pour un RAG un texte dupliqué est bien pire qu'un titre manquant.
- Mal : **les frontières de paragraphe sont perdues.** `PDFTextStripper` sépare les lignes,
  pas les paragraphes, et sa détection de paragraphes est un pari de plus sur la mise en
  page. Une section de PDF arrive donc à RAG-5 comme un unique paragraphe, qu'il découpera à
  la phrase. Dégradé, pas faux.

### Condition de réouverture

La mesure de qualité de RAG-14, si elle montre que les extraits issus de PDF répondent moins
bien que les autres. La sortie sera alors une **bibliothèque d'analyse de mise en page**
(détection de blocs, de colonnes, de paragraphes), pas un réglage des deux seuils : les
retoucher au doigt mouillé ne ferait que déplacer le problème d'un corpus à l'autre.

## Avantages et inconvénients des options

### Les signets seuls

- Bien : zéro heuristique. Le comportement se prédit à la lecture du fichier.
- Mal : la majorité des PDF personnels n'ont pas de sommaire, et rendraient un unique bloc de
  trente pages. Le ticket demande un texte « découpé en sections » pour chaque format ; ce
  serait ne pas le tenir, tout en ayant l'air de le tenir.

### L'heuristique seule

- Bien : un seul chemin de code, un seul jeu de tests.
- Mal : ignorer un sommaire écrit par l'auteur pour lui préférer une mesure de police revient
  à préférer la déduction à l'information. Sur un livre correctement produit, le résultat
  serait strictement moins bon.

## Vérification

`HeadingHeuristicTest` — sur des lignes fabriquées à la main, sans PDF ni Spring — et
`PdfBoxTextExtractorTest`, sur les trois fixtures `signets.pdf`, `sans-signets.pdf` et
`numerise.pdf`. La non-duplication du texte d'une page est fixée par
`ne_rend_jamais_deux_fois_le_texte_d_une_meme_page`.

## Pour aller plus loin

- `docs/superpowers/specs/2026-08-26-extraction-texte-documents-design.md`, décision 4
- `knowledge/infrastructure/extraction/PdfBoxTextExtractor.java`,
  `HeadingHeuristic.java`, `HeadingFontStripper.java`
- ADR-0026, qui explique pourquoi le PDF a droit à sa propre stratégie
- ADR-0025, qui décide du sort d'un PDF numérisé : il échoue, il ne rend pas du vide
