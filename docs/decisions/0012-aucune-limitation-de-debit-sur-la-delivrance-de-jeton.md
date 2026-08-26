---
status: accepté
date: 2026-08-17
decision-makers: Rémy Theroux
---

# Aucune limitation de débit sur `POST /api/token`

## Contexte et problème

`POST /api/token` accepte autant de tentatives que le client en envoie : rien n'empêche une
recherche exhaustive de mot de passe. Dans la même veine, un email inconnu revient sans
calcul BCrypt, donc plus vite qu'un email connu — le temps de réponse trahit l'existence
d'un compte.

## Facteurs de décision

- Les deux failles sont de la même famille et se referment avec le même outillage : compter
  les tentatives suppose de les journaliser, et journaliser suppose de décider quoi
  conserver et pendant combien de temps.
- Hacher un leurre systématiquement corrigerait l'oracle temporel à peu de frais.
- Mais boucher l'oracle temporel en laissant la porte d'à côté grande ouverte revient à se
  raconter une histoire : celui qui peut essayer un million de mots de passe n'a pas besoin
  de chronométrer les réponses pour savoir qui existe.

## Options envisagées

- Ne rien faire pour l'instant, et traiter les deux ensemble plus tard
- Hacher un leurre pour égaliser les temps de réponse, sans limitation de débit
- Limitation de débit par IP et par compte, plus journalisation des tentatives

## Décision

Retenu : **ne rien faire pour l'instant**, et traiter les deux failles ensemble, avec la
journalisation des tentatives — parce qu'une demi-parade donnerait le sentiment que le
sujet est traité.

L'ordre des contrôles à la connexion reste, lui, un vrai choix de sécurité déjà fait : mot
de passe d'abord, vérification d'adresse ensuite. Seul celui qui connaît déjà le mot de
passe apprend qu'un compte existe mais n'est pas vérifié.

### Conséquences

- Bien : aucune infrastructure de comptage, aucun stockage de tentatives, aucune
  question de rétention de données personnelles ouverte prématurément.
- Mal : la route est vulnérable à une recherche exhaustive, et son temps de réponse est un
  oracle d'existence de compte. Les deux sont exploitables aujourd'hui.

### Condition de réouverture

Avant toute exposition publique réelle du service. C'est un ticket unique — limitation de
débit, journalisation des tentatives, égalisation des temps de réponse — et il est dû dès
que l'inscription est ouverte à autre chose qu'à son auteur.

## Pour aller plus loin

- `users/application/query/AuthenticateUser*`, `users/infrastructure/web/`
