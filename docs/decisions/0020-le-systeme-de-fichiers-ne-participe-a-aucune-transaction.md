---
status: accepté
date: 2026-08-19
decision-makers: Rémy Theroux
---

# Le système de fichiers ne participe à aucune transaction

## Contexte et problème

Les originaux des documents vivent sur disque, un fichier par document, sous
`secondbrain.storage.originals-path` — pas en base. La transaction ouverte par le
`CommandBus` couvre donc la ligne, jamais le fichier.

`UploadDocumentHandler` écrit l'original **après** le `saveAndFlush` : une panne entre les
deux annule la ligne et laisse un fichier que plus rien ne désigne. Symétriquement,
`DeleteDocumentHandler` efface la ligne avant le fichier, et un échec entre les deux laisse
un original orphelin.

## Facteurs de décision

- L'ordre inverse est pire : un fichier écrit avant la ligne survivrait à un rollback en
  désignant une ligne qui n'existe pas, et un original effacé avant la ligne rendrait un
  document listé mais illisible.
- Aucune des deux fuites n'est visible de l'utilisateur : elles remplissent un disque, elles
  ne cassent rien.
- Une parade correcte — journal des fichiers à effacer, balayage périodique — est un ticket
  à elle seule.

## Options envisagées

- Ordre choisi (ligne puis fichier), fuites assumées
- Stocker les originaux en base, dans une colonne binaire
- Journal transactionnel des fichiers en attente, plus un balayage périodique

## Décision

Retenu : **l'ordre ligne puis fichier, fuites assumées**. C'est l'ordre qui garantit que
tout ce que la base désigne existe, quitte à ce que le disque contienne davantage.

Le même raisonnement fixe la place de la publication de `DocumentUploaded` : le fichier
avant l'annonce, parce qu'écrit après le commit il manquerait au consommateur qui relit.

### Conséquences

- Bien : aucun document listé ne pointe vers un fichier absent — l'invariant qui compte est
  tenu.
- Mal : deux fuites possibles, dans les deux sens, qui remplissent un disque en silence.
- Mal : le répertoire des originaux est un état à part entière. **Il ne se restaure pas avec
  un dump PostgreSQL**, et rien ne l'annule avec une transaction — y compris dans les tests,
  où `@Transactional` annule la base et jamais le disque.

### Condition de réouverture

Le volume des dépôts qui rend la place perdue mesurable, ou l'arrivée du remplacement d'un
document — qui multiplierait les occasions de fuite au lieu de les laisser exceptionnelles.
La parade retenue ce jour-là sera le journal plus balayage, pas le stockage en base.

## Pour aller plus loin

- `knowledge/application/command/UploadDocumentHandler.java`,
  `knowledge/infrastructure/storage/S3DocumentStorage.java`
- `.claude/rules/backend.md`, section « Tests » — la conséquence sur le nettoyage
