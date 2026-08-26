---
status: accepté
date: 2026-08-17
decision-makers: Rémy Theroux
---

# Pas de CSRF ni de session : l'identité voyage dans un en-tête

## Contexte et problème

`SecurityConfig` désactive CSRF et pose la session en `STATELESS`. C'était une dette tant
qu'aucune authentification n'existait — une protection contre la contrefaçon de requête
sans requête authentifiée à contrefaire. Le ticket « login » devait la lever ; il l'a levée
autrement qu'annoncé.

## Facteurs de décision

- CSRF ne protège que ce que le navigateur envoie **spontanément** : cookies, en-têtes
  d'authentification HTTP. Rien d'autre.
- Le front est *first-party* et sert sur la même origine que l'API — Traefik en
  développement, Coolify en production.
- Une protection posée « au cas où » sans menace correspondante coûte un jeton par
  formulaire et une session côté serveur.

## Options envisagées

- Réactiver CSRF et poser un cookie de session
- Ne poser aucun cookie d'authentification : le jeton voyage dans `Authorization`
- Cookie d'authentification `httpOnly` plus jeton anti-CSRF

## Décision

Retenu : **aucun cookie d'authentification**. L'identité voyage dans un en-tête
`Authorization`, qu'un navigateur n'envoie jamais de lui-même : il n'y a rien à
contrefaire depuis un site tiers, donc rien à protéger par CSRF.

L'origine unique, elle, ne tient plus au proxy du serveur de développement mais au reverse
proxy. La règle « aucune configuration CORS dans ce projet » est donc vraie partout, et
non plus seulement en local.

### Conséquences

- Bien : pas de session côté serveur, donc l'API reste horizontalement réplicable sans
  stockage partagé.
- Bien : aucun jeton anti-CSRF à faire circuler dans les formulaires du front.
- Mal : le jeton doit être stocké par le front, ce qui l'expose à XSS — voir ADR-0011.
- Mal : `csrf().disable()` se lit comme une négligence pour qui arrive sans le contexte.
  Le commentaire dans `SecurityConfig` doit renvoyer ici.

### Condition de réouverture

**Le jour où un cookie d'authentification apparaît, CSRF redevient obligatoire.** C'est
notamment ce que déclencherait la parade à ADR-0011 (cookie `httpOnly` `Secure`
`SameSite` plus jeton de rafraîchissement) : les deux décisions se rouvrent ensemble.

## Avantages et inconvénients des options

### Aucun cookie d'authentification — *retenu*

- Bien : supprime la classe d'attaque au lieu de la contrer.
- Mal : reporte le problème sur le stockage du jeton côté navigateur.

### Cookie de session plus CSRF

- Bien : le jeton n'est jamais lisible par le JavaScript du front.
- Mal : réintroduit un état serveur et un jeton anti-CSRF dans chaque formulaire, pour un
  front qui n'a ni redirection ni consentement à gérer.

## Pour aller plus loin

- `config/SecurityConfig.java`
- ADR-0011 — le jeton d'accès est rangé dans le `localStorage`
