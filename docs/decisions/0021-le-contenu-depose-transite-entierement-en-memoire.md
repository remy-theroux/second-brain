---
status: accepté
date: 2026-08-19
decision-makers: Rémy Theroux
---

# Le contenu déposé transite entièrement en mémoire

## Contexte et problème

L'identité d'un document est son empreinte SHA-256. Une empreinte ne se calcule pas sur un
extrait : il faut les octets complets. La commande `UploadDocument` transporte donc le
contenu en `byte[]`, de la requête multipart jusqu'à l'écriture sur disque.

## Facteurs de décision

- Le plafond de 20 Mo borne le risque — mais il le borne **par requête**, et rien ne limite
  le nombre de dépôts simultanés.
- L'alternative (écrire le flux sur disque en calculant l'empreinte au fil de l'eau, puis
  relire pour vérifier) complique le chemin nominal pour un problème qui n'existe pas
  encore : le service a un utilisateur.
- Une commande porte l'intention et ses données ; y mettre un flux ouvert la rendrait
  dépendante du cycle de vie de la requête HTTP.

## Options envisagées

- Le contenu en `byte[]` dans la commande
- Écriture en flux vers un fichier temporaire, empreinte calculée au fil de l'eau
- Empreinte calculée côté navigateur, vérifiée côté serveur

## Décision

Retenu : **le contenu en `byte[]`**, parce que c'est la forme qui laisse `Checksum.of` dans
le domaine, sans que le domaine sache d'où viennent les octets.

### Conséquences

- Bien : le calcul de l'empreinte reste une opération du domaine, sur un tableau d'octets,
  testable sans fichier ni requête.
- Mal : la mémoire consommée est proportionnelle au nombre de dépôts simultanés multiplié
  par 20 Mo. Rien ne plafonne ce produit.

### Condition de réouverture

Deux déclencheurs : le plafond de 20 Mo qui monte, ou plusieurs utilisateurs qui déposent
ensemble. Il faudra alors écrire le flux sur disque en calculant l'empreinte au fil de
l'eau, et ne relire que pour vérifier.

## Pour aller plus loin

- `knowledge/domain/valueobject/Checksum.java`,
  `knowledge/application/command/UploadDocument.java`
- ADR-0020 — le disque, lui, ne participe à aucune transaction
