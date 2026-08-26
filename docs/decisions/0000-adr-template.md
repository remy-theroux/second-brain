---
status: accepté
date: 2026-08-26
decision-makers: Rémy Theroux
---

# Gabarit d'ADR

Gabarit MADR du projet. **Ce fichier ne se remplit pas** : copier le bloc ci-dessous dans
`docs/decisions/<numéro>-<titre-en-kebab-sans-accent>.md`, puis remplacer les `<…>`.

Le front matter garde les clés de MADR en anglais — ce sont des clés, pas de la prose —
et prend ses valeurs en français. `status` vaut `proposé`, `accepté`, `rejeté`,
`déprécié` ou `remplacé par ADR-XXXX`. `date` est celle de la **dernière mise à jour**,
pas de la création. `decision-makers` suffit tant que le projet est à un seul décideur :
`consulted` et `informed` sont optionnels et restent omis.

Les sections marquées optionnelles se suppriment si elles n'apprennent rien. Les autres
se remplissent — une section vide dit que la décision n'est pas mûre.

Les règles de rédaction, de numérotation et de remplacement vivent dans
`.claude/rules/decisions.md` ; le pourquoi du dispositif, dans ADR-0001.

```markdown
---
status: proposé
date: AAAA-MM-JJ
decision-makers: Rémy Theroux
---

# <La décision, à l'affirmative : « Le système de fichiers ne participe à aucune transaction »>

## Contexte et problème

<2 à 5 lignes. L'état des lieux et la question posée. Le « pourquoi maintenant », pas le
« comment ».>

## Facteurs de décision

- <ce qui pèse : une contrainte, un risque, un coût>
- <…>

## Options envisagées

- <option 1>
- <option 2>
- <option 3>

## Décision

Retenu : **<option>**, parce que <la raison qui a tranché, en une phrase>.

### Conséquences

- Bien : <ce que ça rend possible ou simple>
- Mal : <ce que ça coûte, et qui le paiera>

### Condition de réouverture

<Le fait observable qui rend cette décision caduque. « Jamais » est une réponse valable,
à condition d'être écrite.>

## Avantages et inconvénients des options

<!-- Optionnel, mais fortement recommandé : c'est la seule section qui empêche une
     alternative déjà rejetée de revenir sur la table. -->

### <option 1>

- Bien : <…>
- Mal : <…>

## Vérification

<!-- Optionnel. Comment on constate que le code respecte encore cette décision : un test,
     un grep, une revue. Écrire « rien ne le vérifie » est une information en soi. -->

## Pour aller plus loin

<!-- Optionnel. Liens : spec, plan d'implémentation, ADR liés, ticket. -->
```
