---
status: accepté
date: 2026-08-19
decision-makers: Rémy Theroux
---

# Le front recopie ce qui n'est pas une règle du serveur

## Contexte et problème

`DocumentsView` recopie deux choses que le back possède : la liste des extensions acceptées
(`ACCEPTED_EXTENSIONS`, qui ne sert qu'à filtrer le sélecteur de fichiers du navigateur) et
le libellé des statuts (`STATUS_LABELS`, qui traduit l'énumération `DocumentStatus`).

## Facteurs de décision

- Ni l'une ni l'autre n'est une règle : la règle sur les formats est au serveur, dont le
  `415` énonce la liste qui fait foi ; le libellé d'un statut est une affaire d'écran.
- Exposer ces deux listes par une route de métadonnées ajoute un appel au chargement de
  l'écran, et une route à maintenir, pour un filtre de sélecteur de fichiers.
- La divergence ne casse rien de visible : un format ajouté à `DocumentFormat` reste
  déposable en changeant le filtre du sélecteur, un statut ajouté à `DocumentStatus`
  s'affiche par son code.

## Options envisagées

- Recopier les deux listes côté front
- Une route de métadonnées que le front interroge au chargement
- Générer les constantes du front depuis les énumérations du back, au build

## Décision

Retenu : **recopier**, parce qu'aucune des deux copies ne porte de règle — la règle qui fait
foi reste au serveur, et le front ne fait qu'agrémenter.

### Conséquences

- Bien : l'écran se charge en un appel, et aucune route n'existe pour servir deux listes de
  cinq entrées.
- Mal : deux endroits peuvent diverger, et **aucun test ne surveille la divergence**. Le
  symptôme est discret par construction : rien ne casse, l'écran est seulement moins bon.

### Condition de réouverture

Un troisième consommateur de ces listes, ou une divergence constatée qui gêne réellement.
La route de métadonnées devient alors la bonne réponse — la génération au build
introduirait une étape de build entre back et front, ce que le découpage actuel évite
soigneusement.

## Pour aller plus loin

- `frontend/src/views/DocumentsView.vue`, `knowledge/domain/valueobject/DocumentFormat.java`
- ADR-0017 — la même nature de duplication, sur les libellés de vérification
