---
status: accepté
date: 2026-08-04
decision-makers: Rémy Theroux
---

# Les entités JPA vivent dans le domaine, sans classe miroir ni mapper

## Contexte et problème

L'architecture est hexagonale : le sens des dépendances est `infrastructure` →
`application` → `domain`, et le domaine n'importe jamais `org.springframework.*` ni
`…infrastructure.*`. La persistance, elle, demande des annotations sur les classes qu'elle
projette. Où les mettre sans faire fuiter l'hexagone plus qu'il ne faut ?

## Facteurs de décision

- Une classe miroir par agrégat, plus son mapper, double le nombre de fichiers et le
  nombre d'endroits où un champ oublié se cache.
- La fuite, si fuite il y a, doit être **bornée et nommable** : un lecteur doit savoir
  exactement jusqu'où elle va.
- Le projet est tenu par une personne. Un coût structurel payé à chaque agrégat pèse plus
  qu'une entorse théorique.

## Options envisagées

- Annoter directement l'entité du domaine avec `jakarta.persistence`
- Une entité de persistance miroir dans `infrastructure/persistence/`, plus un mapper
- Un mapping externe `orm.xml`, qui laisserait le domaine entièrement nu

## Décision

Retenu : **annoter directement l'entité du domaine**, parce que c'est la seule des trois
qui ne fait payer aucun fichier supplémentaire par agrégat.

L'écart est borné aux annotations, et il ne s'étend pas au mapping des value objects :
`EmailAttributeConverter` et `ChecksumAttributeConverter` vivent dans
`infrastructure/persistence/` et sont `autoApply`, donc `User` et `Document` ne les
nomment pas. Le domaine ignore jusqu'à leur existence.

### Conséquences

- Bien : un agrégat, un fichier. Pas de champ oublié dans un mapper, pas de doute sur la
  classe qui fait foi.
- Mal : `domain/entity/` dépend de `jakarta.persistence`. C'est la seule exception actée à
  la règle de dépendance, et elle doit être citée comme telle à chaque fois qu'on la
  croise.
- Mal : la forme relationnelle et la forme métier ne peuvent plus diverger sans douleur.
  Tant qu'elles coïncident, ça ne coûte rien.

### Condition de réouverture

Le jour où le schéma force à déformer l'agrégat pour lui plaire — colonne purement
technique, héritage à mapper, table de jointure imposée par l'existant. C'est ce jour-là
que la classe miroir devient rentable, pas avant.

## Avantages et inconvénients des options

### Annoter l'entité du domaine — *retenu*

- Bien : aucun fichier supplémentaire, aucune synchronisation à tenir.
- Mal : un import d'infrastructure dans le domaine, à assumer explicitement.

### Entité miroir plus mapper

- Bien : le domaine reste absolument pur, testable sans aucune dépendance.
- Mal : deux classes et un mapper par agrégat, et un bug silencieux à chaque champ ajouté
  d'un côté seulement.

### Mapping externe `orm.xml`

- Bien : domaine nu, sans le coût du miroir.
- Mal : le mapping quitte le fichier qu'il décrit. Un champ renommé casse au démarrage, à
  distance, et personne ne pense à ouvrir le XML.

## Pour aller plus loin

- `users/domain/entity/User.java`, `knowledge/domain/entity/Document.java`
- `.claude/rules/backend.md`, section « Placement du code »
