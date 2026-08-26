---
status: accepté
date: 2026-08-17
decision-makers: Rémy Theroux
---

# Pas de jeton de rafraîchissement, pas de révocation

## Contexte et problème

`POST /api/token` délivre un JWT HS256 valable une heure, validé par le resource server
sans aucun aller-retour vers la base. Un JWT vaut donc jusqu'à son `exp`, quoi qu'il arrive
entre-temps : « se déconnecter » efface le jeton du navigateur et rien de plus.

## Facteurs de décision

- Un jeton révocable suppose un état consulté à chaque requête — liste de révocation ou
  jeton opaque —, c'est-à-dire exactement ce qu'un JWT sert à éviter.
- Un jeton de rafraîchissement est un second secret à stocker, à faire tourner et à
  révoquer : il déplace le problème sans le supprimer.
- La fenêtre d'exposition d'un jeton volé est bornée par sa durée de vie, et rien d'autre.

## Options envisagées

- JWT court, sans rafraîchissement ni révocation
- JWT court plus jeton de rafraîchissement en base, révocable
- Jeton opaque vérifié en base à chaque requête

## Décision

Retenu : **JWT court, sans rafraîchissement ni révocation**, parce que la révocation n'a de
valeur que face à une menace qu'on ne sait pas encore détecter — et que la durée de vie
courte couvre le même risque à un coût nul.

La conséquence est que **la durée de vie courte n'est pas négociable** : c'est la seule
chose qui borne un jeton volé. Elle est donc une règle du domaine
(`AccessTokenPolicy.LIFETIME`), pas une propriété de configuration qu'un exploitant
pourrait porter à trente jours.

### Conséquences

- Bien : aucune lecture en base sur le chemin d'authentification ; l'API reste sans état.
- Mal : un jeton volé reste valable jusqu'à une heure, et rien ne peut l'arrêter.
- Mal : une session expire brutalement au bout d'une heure, sans prolongation silencieuse.
  C'est un choix d'ergonomie autant que de sécurité.

### Condition de réouverture

Deux déclencheurs, et ils vont ensemble : un besoin de session longue (« rester connecté »)
ou un besoin de déconnexion effective à distance. Les deux appellent la même parade que
ADR-0011 — cookie `httpOnly` plus jeton de rafraîchissement —, donc les trois décisions se
rouvrent d'un bloc, avec ADR-0003.

## Pour aller plus loin

- `users/domain/AccessTokenPolicy.java`, `users/infrastructure/security/`
- ADR-0003, ADR-0011
