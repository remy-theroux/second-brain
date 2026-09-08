# Réagir aux notifications push de Google Drive — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un fichier déposé dans un dossier surveillé devient interrogeable en moins d'une minute,
sans attendre le balayage périodique.

**Architecture:** Un accélérateur, **jamais un remplaçant**. Trois pièces, et la première est une
règle plus qu'un mécanisme.

**1. Une notification ne décide de rien.** Drive ne dit pas quel fichier a bougé : il dit
« quelque chose a bougé, viens lire ». Le webhook **publie donc exactement l'événement que la tâche
planifiée de DRIVE-5 publie**, et rien d'autre. Aucun document n'est ingéré sur la seule foi d'une
notification, et l'idempotence face aux notifications répétées et désordonnées est déjà celle de
DRIVE-5 — c'est à dire, pas un dispositif de plus.

**2. Le balayage périodique reste actif.** L'abonnement expire, une notification se perd, un canal
meurt : le push est un chemin rapide, jamais l'unique chemin.

**3. Le webhook est une route publique, et son unique authentification est le jeton de canal.**
Elle ne porte aucune identité d'utilisateur — Google ne s'authentifie pas auprès de nous.
`X-Goog-Channel-Token` est comparé à celui émis à l'abonnement, et c'est tout ce qui sépare une
notification légitime d'un appel anonyme.

**Tech Stack:** Java 25 · Spring Boot 4.0.7 (MVC, Security, `@Scheduled`) · Flyway · PostgreSQL ·
JUnit 5 + AssertJ + Testcontainers. Aucune dépendance nouvelle.

**Ticket:** DRIVE-6 — Réagir aux notifications push de Google Drive. **Prérequis strict : DRIVE-5.**

## Global Constraints

Les mêmes que DRIVE-5, plus :

- **La route du webhook se déclare explicitement dans `SecurityConfig`.** Sous `/api/**`, tout est
  refusé par défaut, et le ticket veut cette route sous `/api`. C'est la première route publique
  ajoutée depuis `/api/token` et `/api/registrations` : la déclaration est le geste qui compte.
- **Rien de ce que Google envoie n'est cru sur parole**, sauf le jeton de canal — et encore, il ne
  prouve que « celui qui appelle connaît le secret que nous avons émis ».
- **Aucun ADR n'est écrit.**

## Fichiers

**Créés**

| Fichier | Responsabilité |
|---|---|
| `…/domain/entity/DriveChannel.java` | Un abonnement : identifiant, jeton, expiration |
| `…/domain/valueobject/DriveChannelToken.java` | Le secret du canal, `toString()` masqué |
| `…/domain/DriveChannelPolicy.java` | Le délai avant échéance qui déclenche le renouvellement |
| `…/domain/port/DriveChannelRepository.java` | |
| `…/domain/port/GoogleDriveChannels.java` | `watch`, `stop` |
| `…/application/command/OpenDriveChannel.java` + handler | |
| `…/application/command/CloseDriveChannel.java` + handler | |
| `…/application/command/NotifyDriveChange.java` + handler | Vérifie le jeton, publie l'événement de DRIVE-5 |
| `…/infrastructure/drive/GoogleDriveChannelsAdapter.java` | `changes.watch`, `channels.stop` |
| `…/infrastructure/scheduling/DriveChannelRenewalScheduler.java` | `@Scheduled`, `@Profile("worker")` |
| `…/infrastructure/web/NotifyDriveChangeController.java` | Le webhook |
| `V19__create_knowledge_drive_channels.sql` | |

**Modifiés**

| Fichier | Modification |
|---|---|
| `config/SecurityConfig.java` | La route publique, **déclarée** |
| `…/application/command/DisconnectDriveHandler.java` | Fermer le canal en partant |
| `src/main/resources/application.yml`, `compose.yaml`, `.env.example` | L'URL publique du webhook |
| `CLAUDE.md` | Le récit |

---

### Task 1: Le canal, son jeton et son abonnement

**Files:** `DriveChannel`, `DriveChannelToken`, `DriveChannelPolicy`, `DriveChannelRepository` +
adapters, `GoogleDriveChannels`, `GoogleDriveChannelsAdapter`, `V19`, `OpenDriveChannel` + handler,
`CloseDriveChannel` + handler, `DriveChannelTest`

- [ ] **Step 1: Écrire les tests qui échouent**

`DriveChannelTest`, unitaire pur :

| Test | Ce qu'il vérifie |
|---|---|
| `draws_a_token_that_differs_from_one_channel_to_the_next` | |
| `accepts_the_token_it_was_opened_with` | |
| `refuses_a_token_that_does_not_match` | |
| `refuses_any_token_once_it_has_expired` | Un canal périmé n'authentifie plus rien |
| `needs_a_renewal_before_its_deadline` | La marge de `DriveChannelPolicy` |

- [ ] **Step 2: La table**

Un canal par connexion (`connection_id` `UNIQUE`), avec `channel_id`, `resource_id` — **les deux
sont nécessaires pour arrêter un canal**, `channels.stop` les exige tous les deux —, le jeton et
l'expiration. Cascade sur la connexion.

**Le jeton de canal est un secret**, au même titre que le jeton de rafraîchissement : le chiffrer
au repos par le même dispositif serait cohérent. **Mais** — et c'est un arbitrage à écrire —
il ne donne accès à rien : il permet seulement de nous faire relire les changements d'un Drive,
c'est-à-dire de déclencher un travail que nous faisons de toute façon toutes les N minutes. Le
stocker en clair est défendable ; le chiffrer coûte trois lignes puisque le converter existe.
**Retenir le chiffrement**, parce que le coût est nul et que « c'est un secret qui ne sert à rien »
est le genre de phrase qui vieillit mal.

- [ ] **Step 3: L'adapter**

`POST /drive/v3/changes/watch?pageToken=…` avec un corps portant `id` (un UUID à nous), `type: web_hook`,
`address` (l'URL publique) et `token` (notre secret). Google rend `resourceId` et `expiration`.

**L'abonnement expire au plus tard à sept jours**, et Google peut rendre une échéance plus courte
sans prévenir : **lire l'`expiration` qu'il rend**, ne jamais supposer sept jours.

`channels.stop` prend `id` et `resourceId`.

- [ ] **Step 4: Vérifier, formater, committer**

Message : `feat: un canal de notifications Drive s'ouvre et se ferme`

---

### Task 2: Le webhook

**Files:** `NotifyDriveChange` + handler, `NotifyDriveChangeController`, `SecurityConfig`,
`DriveWebhookTest`

- [ ] **Step 1: Écrire les tests qui échouent**

| Méthode de test | Scénario du ticket |
|---|---|
| `reads_the_changes_of_the_drive_when_a_notification_arrives` | Et **aucun document ingéré sur la seule foi de la notification** |
| `refuses_a_notification_that_does_not_carry_the_channel_token` | Aucun changement lu |
| `refuses_a_notification_whose_token_belongs_to_another_channel` | |
| `ignores_a_notification_for_a_connection_that_was_revoked_and_closes_the_channel` | |
| `ingests_nothing_twice_when_two_notifications_arrive_for_the_same_change` | L'idempotence est celle de DRIVE-5 |

- [ ] **Step 2: La route**

`POST /api/drive/notifications`. Elle rend **`200` quoi qu'il arrive de côté métier**, sauf sur un
jeton faux où elle rend `404`. La raison est que Google **désabonne un canal qui répond en erreur**
de façon répétée : rendre un `500` parce que la base est momentanément indisponible ferait perdre
le canal, alors que le balayage périodique aurait rattrapé le coup.

Un `404` et non un `401` sur un jeton faux : `401` invite à s'authentifier, ce qui n'a aucun sens
ici, et confirme au passage que l'URL est un webhook actif.

**La déclarer dans `SecurityConfig`** — sans quoi elle répond `401` et Google ferme le canal au
bout de quelques essais, sans que rien ne le dise.

- [ ] **Step 3: Le corps de la notification est ignoré**

Google envoie ses informations en **en-têtes** (`X-Goog-Channel-ID`, `X-Goog-Channel-Token`,
`X-Goog-Resource-State`), le corps étant vide sur un `sync` comme sur un changement. Le lire ne
sert à rien ; ne pas s'en servir est le point du ticket.

**L'état `sync`** est envoyé une fois à l'ouverture du canal, pour le valider : il ne signale aucun
changement. Le traiter comme un changement fait un balayage inutile à chaque renouvellement — le
reconnaître et ne rien faire.

- [ ] **Step 4: Vérifier, formater, committer**

Message : `feat: une notification de Drive déclenche une lecture des changements`

---

### Task 3: Le renouvellement, et la fermeture

**Files:** `DriveChannelRenewalScheduler`, `DisconnectDriveHandler`, `DriveChannelRenewalTest`

- [ ] **Step 1: Écrire les tests qui échouent**

| Méthode de test | Scénario du ticket |
|---|---|
| `opens_a_new_channel_before_the_current_one_expires` | Sans interruption |
| `closes_the_channel_it_replaces` | Sinon Google notifie deux fois |
| `opens_a_channel_for_a_connection_that_has_none` | Premier tour après une connexion |
| `catches_up_through_the_periodic_scan_when_the_channel_has_expired` | **Aucun changement perdu** |
| `closes_the_channel_when_the_drive_is_disconnected` | |

- [ ] **Step 2: L'horloge**

`@Scheduled`, `@Profile("worker")`, `fixedDelay`. Le renouvellement ouvre le nouveau canal **avant**
de fermer l'ancien : l'inverse laisse une fenêtre sans abonnement. Les deux canaux coexistent le
temps d'un appel, donc une notification peut arriver en double — sans conséquence, puisqu'une
notification ne fait que déclencher une relecture.

- [ ] **Step 3: Vérifier, formater, committer**

Message : `feat: l'abonnement aux notifications se renouvelle avant son échéance`

---

### Task 4: La configuration, la suite, et la documentation

- [ ] **Step 1: L'URL publique**

`secondbrain.drive.webhook-url`, **sans défaut utilisable** : Google exige une URL HTTPS publique
et vérifiée, et `http://localhost:8080` n'en est pas une. Une valeur vide **désactive l'ouverture
de canaux** plutôt que de faire échouer le démarrage — c'est le cas normal en développement, où
Google ne peut pas nous joindre.

Le dire : **en développement, le push ne fonctionne pas**, et les tests passent par un appel HTTP
direct sur la route plutôt que par un vrai canal.

- [ ] **Step 2:** `docker compose down` puis `gtest build`, **une seule fois**.

- [ ] **Step 3:** `CLAUDE.md` : une sous-section « Le flux des notifications push de Drive » qui
  dit : la notification qui ne décide de rien et déclenche le mécanisme de DRIVE-5 ; le balayage
  périodique qui **reste actif**, et pourquoi ; la route publique déclarée dans `SecurityConfig`,
  et ce qui arrive si on l'oublie ; le jeton de canal comme unique authentification, et ce qu'il
  prouve exactement ; le `200` rendu quoi qu'il arrive et **pourquoi** (Google désabonne un canal
  qui répond en erreur) ; l'état `sync` reconnu et ignoré ; l'expiration **lue chez Google** et
  jamais supposée ; le renouvellement qui ouvre avant de fermer ; et le fait qu'en développement
  le push ne fonctionne pas.

- [ ] **Step 4:** Message : `docs: documente les notifications push de Google Drive`
