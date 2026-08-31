---
status: accepté
date: 2026-08-26
decision-makers: Rémy Theroux
---

# L'échec d'extraction s'écrit hors de la transaction annulée, et le message est acquitté

## Contexte et problème

`SpringCommandBus.dispatch` est `@Transactional` : tout ce qu'un handler déclenche vit dans
une transaction, et la moindre `RuntimeException` l'annule entièrement.

Le ticket exige qu'un document inexploitable « échoue explicitement ». Le geste naturel —
faire poser `FAILED` par `ExtractDocumentTextHandler` avant de relever l'exception — ne
marche pas : l'écriture du statut serait annulée avec le reste, et le document resterait
`PENDING` pour toujours. RAG-6 signale ce piège dans son ticket ; il mord dès celui-ci.

Reste aussi à décider du sort du **message** : le rejeter, ou l'acquitter.

## Facteurs de décision

- `default-requeue-rejected=false` : un message rejeté n'est pas remis en file, il est perdu.
  Rejeter ne rejoue donc rien, cela renonce seulement à l'acquitter.
- Une fois l'échec écrit en base, l'issue du traitement est connue et durable. Le message
  n'a plus rien à porter.
- **La règle backend interdit `@Transactional` sur un handler** : annoter le fait proxifier
  en JDK proxy, ce qui casse la résolution de son type générique au démarrage. Un
  `REQUIRES_NEW` sur le handler d'extraction n'est donc pas une option disponible.
- Le message d'échec est affiché à l'utilisateur. Celui d'un refus métier est rédigé pour ça ;
  celui d'une `NullPointerException` ne l'est pas.

## Options envisagées

- Seconde commande dans sa propre transaction, puis message acquitté
- Seconde commande, puis exception relevée pour rejeter le message
- Dead-letter queue et rejeu
- `@Transactional(REQUIRES_NEW)` sur un handler

## Décision

Retenu : **une seconde commande, puis le message est acquitté.**

`KnowledgeEventListener.on(DocumentUploaded)` dispatche `ExtractDocumentText` dans un `try`.
Le `catch` journalise en `ERROR`, puis dispatche `MarkDocumentExtractionFailed` — une commande
à part, donc une **seconde transaction**, ouverte après que la première a été annulée. Puis il
rend la main sans relever : rejeter le message n'apporterait rien, l'issue étant déjà en base,
et ne produirait qu'une pile de plus dans le journal.

Le motif montré vient de l'exception quand elle est métier — `DocumentExtractionException` et
ses deux filles portent des messages affichables tels quels — et d'une phrase générique
sinon.

### Conséquences

- Bien : un document ne reste jamais en attente sans explication, et l'explication est lisible.
- Bien : aucune trace technique n'atteint l'utilisateur.
- Mal : **si la seconde commande échoue à son tour**, elle remonte, le message est rejeté sans
  remise en file, et le document reste `PENDING`. C'est le seul trou ; il est journalisé en
  `ERROR`.
- Mal : **un traitement n'est jamais rejoué.** Une panne passagère — le disque indisponible
  une seconde — se solde par un `FAILED` définitif, jusqu'à la réextraction qu'apportera
  RAG-7.
- Mal : le `catch (RuntimeException)` du listener attrape aussi les erreurs de programmation.
  Elles finissent en `FAILED` avec le message générique, et dans le journal avec leur pile.

### Condition de réouverture

Le jour où les pannes passagères deviennent assez fréquentes pour que les `FAILED` à tort se
comptent. La sortie sera un **rejeu borné côté worker** — deux ou trois tentatives espacées,
décidées dans le listener —, pas une dead-letter queue : ADR-0023 tient toujours, on fait
confiance au broker et on ne construit pas de filet au filet.

## Avantages et inconvénients des options

### Seconde commande, puis exception relevée

- Bien : le message est visiblement rejeté, ce qui se voit dans la console du broker.
- Mal : avec `default-requeue-rejected=false`, rejeter ne rejoue rien. On paie une pile
  d'exception dans le journal pour un geste sans effet, alors que l'issue est déjà en base.

### Dead-letter queue et rejeu

- Bien : un message toxique est isolé plutôt que perdu, et une panne passagère est rattrapée.
- Mal : c'est le sur-engineering qu'ADR-0023 refuse explicitement. Et un document en `FAILED`
  porte déjà son motif : la file de rebut ne dirait rien que la base ne dise mieux.

### `@Transactional(REQUIRES_NEW)` sur un handler

- Bien : l'écriture de l'échec resterait dans le contexte d'extraction, sans seconde commande.
- Mal : **interdit par la règle backend.** Annoter un handler le fait proxifier en JDK proxy,
  et la résolution de son type générique échoue au démarrage : l'application ne démarre plus.

## Vérification

`KnowledgeEventListenerTest.marque_le_document_en_echec_quand_l_extraction_refuse` — la classe
n'est pas `@Transactional`, elle observe les commits du worker. Et
`n_expose_pas_le_message_d_une_panne_technique`, qui vérifie qu'un original absent du disque
ne remonte pas une trace technique à l'écran.

## Pour aller plus loin

- `docs/superpowers/specs/2026-08-26-extraction-texte-documents-design.md`, décision 5
- `knowledge/infrastructure/messaging/KnowledgeEventListener.java`,
  `knowledge/application/command/MarkDocumentExtractionFailedHandler.java`
- ADR-0023, dont cette décision suit l'arbitrage : pas de filet au filet
- `.claude/rules/backend.md`, section « Bus, commandes et queries » — l'interdiction du
  `@Transactional` sur un handler
