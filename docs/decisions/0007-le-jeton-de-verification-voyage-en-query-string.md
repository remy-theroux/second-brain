---
status: accepté
date: 2026-08-06
decision-makers: Rémy Theroux
---

# Le jeton de vérification voyage en query string

## Contexte et problème

Le lien de vérification part par email et doit fonctionner dans n'importe quel client mail,
sans JavaScript et sans que le front soit en ligne. `GET /verification?compte=&jeton=`
transporte donc le jeton en clair dans l'URL.

## Facteurs de décision

- Un lien cliquable dans un mail ne peut pas porter de corps de requête : c'est un `GET`,
  ou rien.
- Une query string est journalisée par défaut à peu près partout : historique du
  navigateur, logs d'accès de nginx et de Traefik, et logs applicatifs dès que
  `org.springframework.web` passe en `DEBUG` — ce qui est le cas du profil `dev`.
- Le masquage soigné des `toString()` des commandes ne couvre pas ce chemin-là.
- Le jeton est à usage unique et expire en 24 h : sa fenêtre d'exploitation est étroite.

## Options envisagées

- Le jeton en query string sur un `GET`
- Une page intermédiaire qui reposte le jeton en `POST`
- Le jeton dans le fragment d'URL (`#`), lu par le front et posté par lui

## Décision

Retenu : **le jeton en query string**, parce que c'est la seule des trois qui marche sans
JavaScript et sans que le front soit joignable — ce que le mail ne peut pas garantir.

### Conséquences

- Bien : le lien fonctionne dans tous les clients mail, y compris ceux qui pré-visitent les
  liens ou désactivent les scripts.
- Mal : le jeton apparaît dans l'historique du navigateur et dans les logs de tout proxy en
  amont. Un accès à ces logs pendant la fenêtre de validité vaut vérification du compte.

### Condition de réouverture

**Le jour où un jeton du même modèle sert à autre chose que vérifier une adresse** — une
réinitialisation de mot de passe, typiquement. Vérifier une adresse deux fois est sans
conséquence ; changer un mot de passe ne l'est pas, et la fuite par les logs cesse alors
d'être acceptable.

## Pour aller plus loin

- `users/infrastructure/web/` — la route `GET /verification`
- ADR-0008 — l'usage unique ne tient qu'à un lire-puis-écrire
