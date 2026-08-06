# Vérification d'un compte par email — design

Date : 2026-08-06 · Contexte borné : `users` · Branche : `feat/confirm-user`

## Contexte

`User.register` crée déjà un compte dans l'état `verified = false`, et la colonne
`verified` existe dans `users_users` depuis `V3`. Rien ne la fait jamais passer à
`true` : c'est ce que ce ticket pose.

Le besoin dépasse l'email. La décision de **notifier** un utilisateur est une notion
métier qui reviendra dans d'autres contextes (rappels, alertes, partage) ; l'email
n'est qu'un canal parmi d'autres. Le design place donc la notification dans le
domaine et l'email dans l'infrastructure.

`SecurityConfig` laisse tout public, il n'y a pas encore de login : la vérification
ne conditionne aujourd'hui aucun accès. `verified` est une donnée que le ticket
« login » exploitera.

## Objectif

Un utilisateur qui vient de créer son compte reçoit une notification contenant un
lien ; en cliquant ce lien, son compte passe à `verified = true`.

**Réussi si :** après une inscription puis un clic sur le lien reçu, la ligne
`users_users` du compte porte `verified = true`, et la base n'a à aucun moment
contenu le jeton en clair.

## Attendus métier

```gherkin
Fonctionnalité: Vérification d'un compte par email

  Scénario: Un compte fraîchement créé reçoit son lien de vérification
    Étant donné qu'aucun compte n'existe pour « alice@exemple.fr »
    Quand je crée un compte avec cette adresse
    Alors une notification de vérification est envoyée à cette adresse
    Et mon compte n'est pas encore vérifié

  Scénario: Le clic sur le lien vérifie le compte
    Étant donné un compte non vérifié ayant reçu son lien de vérification
    Quand je suis ce lien
    Alors mon compte est vérifié
    Et la confirmation m'est affichée

  Scénario: Un lien déjà utilisé est refusé
    Étant donné un compte déjà vérifié par son lien
    Quand je suis à nouveau ce même lien
    Alors la vérification est refusée avec un message indiquant que le lien a déjà servi

  Scénario: Un lien expiré est refusé
    Étant donné un compte non vérifié dont le lien de vérification a plus de 24 heures
    Quand je suis ce lien
    Alors la vérification est refusée avec un message indiquant que le lien a expiré

  Scénario: Un lien falsifié est refusé
    Étant donné un compte non vérifié
    Quand je suis un lien dont le jeton ne correspond pas
    Alors la vérification est refusée avec un message indiquant que le lien n'est pas valide
    Et mon compte reste non vérifié
```

## Décisions de conception

Chaque décision ci-dessous a été arbitrée pendant le brainstorming ; l'alternative
écartée est notée pour que le choix ne soit pas rejoué sans raison.

### 1. La notification est un port du contexte `users`

`NotificationSender` vit dans `users/domain/port/`, aux côtés de `UserRepository` et
`PasswordHasher`. L'adapter email vit dans `users/infrastructure/email/`.

*Écarté :* un bounded context `notifications/` séparé. Il faudrait d'abord trancher
comment deux contextes se parlent, question que ce projet n'a pas encore eu à
résoudre, et pour un seul consommateur. L'extraction se fera au deuxième.

### 2. Le jeton est haché avec un salt par jeton ; le lien porte l'identifiant du compte

Un jeton aléatoire est tiré, envoyé **en clair uniquement dans la notification**, et
stocké **uniquement sous forme de hash salé** (sémantique BCrypt, salt embarqué dans
la chaîne stockée). Le hash n'étant pas déterministe, il ne peut pas servir de clé de
recherche : le lien porte donc `compte=<uuid>&jeton=<clair>`. L'UUID n'est pas un
secret, c'est un pointeur vers la ligne ; seul le jeton fait autorité.

*Écarté :* un hash déterministe `HMAC-SHA256(jeton, secret applicatif)`, qui
permettrait un lien plus court sans UUID. Il introduit un secret global à
configurer et à faire tourner (une rotation invalide tous les liens en vol), et
force le domaine à connaître la notion de secret partagé.

### 3. Le jeton est une entité avec sa propre table

Table `users_verification_tokens`, entité `VerificationToken` dans
`users/domain/entity/`, port `VerificationTokenRepository`.

**Durée de validité : 24 h. Usage unique.**

*Écarté :* deux colonnes nullables sur `users_users`. Moins de code, mais les règles
« expiré » et « déjà utilisé » n'auraient pas de foyer et finiraient dans le handler,
auquel les règles backend interdisent toute logique métier. Ces deux refus ne portent
pas le même message : ce sont bien deux règles distinctes.

### 4. L'envoi se fait dans la transaction d'inscription

`RegisterUserHandler` crée le compte, émet le jeton et envoie la notification. Le
`CommandBus` étant `@Transactional`, une panne d'envoi annule l'inscription entière :
l'utilisateur voit une erreur et peut réessayer.

*Écarté :* un événement de domaine `@TransactionalEventListener(AFTER_COMMIT)`. Plus
robuste, mais introduit une machinerie absente du projet. Tant que « renvoyer le
lien » n'existe pas, un compte créé sans mail envoyé serait définitivement
invérifiable : échouer franchement vaut mieux que réussir à moitié. Ce choix se
réévalue au ticket « renvoyer le lien ».

### 5. La notification est typée par l'intention, pas par son texte

`Notification` est une **sealed interface** du domaine, aujourd'hui permise pour le
seul `VerificationNotification(Email destinataire, UUID compte, String jetonEnClair)`.
Le domaine dit *quoi* notifier et à qui. L'adapter email construit l'URL absolue
(depuis une propriété de configuration), rédige le sujet et le corps, et fait un
`switch` exhaustif sur le type scellé — le compilateur exigera qu'il traite tout
nouveau type de notification.

*Écarté :* une `Notification(destinataire, sujet, corps)` générique rédigée par le
domaine. Pour composer le corps il faudrait une URL absolue, donc un second port
détourné, et le domaine se mettrait à faire de la rédaction et de la mise en forme —
le contraire de l'objectif.

### 6. Une classe de contrôleur par route, nommée par l'intention

Règle transverse ajoutée à `.claude/rules/backend.md`, section « Adapters ». Les
routes existantes sont alignées dans le même ticket.

*Écarté :* un nommage par ressource et verbe HTTP (`RegisterPostController`), qui dit
HTTP au lieu de dire métier.

## Architecture

```
users/
├── domain/
│   ├── entity/
│   │   ├── User.java                        ← + verify()
│   │   └── VerificationToken.java           ← NOUVEAU (agrégat)
│   ├── valueobject/
│   │   ├── RawVerificationToken.java        ← NOUVEAU (jeton en clair)
│   │   ├── Notification.java                ← NOUVEAU (sealed interface)
│   │   └── VerificationNotification.java    ← NOUVEAU (record)
│   ├── port/
│   │   ├── TokenHasher.java                 ← NOUVEAU
│   │   ├── VerificationTokenRepository.java ← NOUVEAU
│   │   └── NotificationSender.java          ← NOUVEAU
│   └── exception/
│       ├── InvalidVerificationLinkException.java     ← NOUVEAU
│       ├── ExpiredVerificationLinkException.java     ← NOUVEAU
│       └── AlreadyUsedVerificationLinkException.java ← NOUVEAU
├── application/command/
│   ├── RegisterUserHandler.java             ← émet le jeton et notifie
│   ├── VerifyAccount.java                   ← NOUVEAU
│   └── VerifyAccountHandler.java            ← NOUVEAU
└── infrastructure/
    ├── persistence/
    │   ├── JpaVerificationTokenRepositoryAdapter.java  ← NOUVEAU
    │   └── SpringDataVerificationTokenRepository.java  ← NOUVEAU (package-private)
    ├── security/  BCryptTokenHasher.java    ← NOUVEAU
    ├── email/     EmailNotificationSender.java ← NOUVEAU
    └── web/
        ├── ShowRegistrationFormController.java ← issu de RegistrationController
        ├── RegisterUserController.java         ← issu de RegistrationController
        └── VerifyAccountController.java        ← NOUVEAU

shared/web/ShowHomeController.java           ← renommage de HomeController
config/ClockConfiguration.java               ← NOUVEAU (@Bean Clock)
```

### Domaine

**`RawVerificationToken`** — value object. Fabrique statique `generate()` : 32 octets
de `SecureRandom` encodés en base64url sans padding (43 caractères sûrs dans une URL).
Aucune dépendance, testable sans Spring.

**`VerificationToken`** — agrégat. Constructeur privé, fabrique
`issue(UUID utilisateur, String jetonHashe, Instant maintenant)` qui pose
`expiresAt = maintenant + 24 h`. Méthodes métier : `isExpired(Instant maintenant)`,
`isConsumed()`, `consume(Instant maintenant)`. Il référence l'utilisateur **par son
UUID**, pas par un `@ManyToOne` : deux agrégats distincts ne se tiennent pas par une
relation JPA.

**Le temps entre par paramètre**, jamais par un `Instant.now()` interne — c'est ce
qui rend l'expiration testable sans attendre 24 h. Les handlers reçoivent un `Clock`
(`java.time`, déclaré en `@Bean`), pas le domaine.

**`User.verify()`** — bascule `verified` à `true`. Rien d'autre : la garde contre le
double usage est portée par le jeton.

**`TokenHasher`** — port jumeau de `PasswordHasher` (`hash` / `matches`). Le jeton
fait 43 caractères, loin de la troncature à 72 octets de BCrypt.

### Flux d'inscription

`RegisterUserHandler` conserve son comportement actuel, puis :

1. `RawVerificationToken.generate()`
2. `verificationTokens.save(VerificationToken.issue(user.getId(), tokenHasher.hash(jeton), clock.instant()))`
3. `notificationSender.send(new VerificationNotification(email, user.getId(), jeton))`

Le clair n'est jamais persisté ni journalisé : `VerificationNotification` redéfinit
`toString()` pour le masquer, comme `RegisterUser` le fait pour le mot de passe.

### Flux de vérification

`GET /verification?compte=<uuid>&jeton=<clair>` → `VerifyAccountController` →
`commandBus.dispatch(new VerifyAccount(compte, jeton))`.

`VerifyAccountHandler` :

1. parser l'UUID — échec → `InvalidVerificationLinkException`
2. charger le jeton du compte — absent → `InvalidVerificationLinkException`
3. `tokenHasher.matches(jetonRecu, hashStocke)` — faux → `InvalidVerificationLinkException`
4. `isConsumed()` → `AlreadyUsedVerificationLinkException`
5. `isExpired(clock.instant())` → `ExpiredVerificationLinkException`
6. sinon `token.consume(maintenant)` et `user.verify()`

**Les trois premiers cas renvoient volontairement le même message.** Un compte
inconnu, un jeton qui ne correspond pas et un UUID malformé sont indistinguables du
dehors : distinguer leurs messages transformerait la route en oracle d'existence de
compte. Expiré et déjà utilisé, eux, portent leur propre message — l'utilisateur
légitime a besoin de savoir laquelle des deux situations le concerne.

### Persistance

Migration `V4__create_users_verification_tokens.sql` :

| Colonne | Type | Contrainte |
|---|---|---|
| `id` | `UUID` | PK, `DEFAULT gen_random_uuid()` |
| `user_id` | `UUID` | `NOT NULL`, `UNIQUE`, FK → `users_users(id)` `ON DELETE CASCADE` |
| `token_hash` | `VARCHAR(255)` | `NOT NULL` |
| `expires_at` | `TIMESTAMPTZ` | `NOT NULL` |
| `consumed_at` | `TIMESTAMPTZ` | nullable |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL`, `DEFAULT now()` |

`UNIQUE (user_id)` documente l'invariant courant « un jeton par compte ». Le ticket
« renvoyer le lien » décidera de le lever ou de réécrire la ligne.

### Configuration et environnement de développement

- `secondbrain.base-url` — défaut `http://localhost:8080`, lue par l'adapter email
  pour construire l'URL absolue du lien.
- `secondbrain.notification.from` — adresse d'expédition.
- `spring.mail.*` — pointe sur Mailpit en dev (`localhost:1025`), configurable par
  variables d'environnement ailleurs.
- Service `mailpit` dans `docker-compose.yml` : SMTP `1025`, interface web `8025`,
  à côté d'Adminer. Le parcours complet est jouable à la main dans le navigateur.

### Interface

- Le message de succès de `/register?success` devient explicite : le compte est créé,
  un mail est parti, il faut cliquer le lien.
- `verification.html` — un seul template, qui affiche soit la confirmation, soit le
  motif du refus. Pas de redirection ni de message flash : la session est `STATELESS`
  et lever cette dette est hors sujet ici.
- Un compte déjà vérifié qui reclique son lien tombe sur « lien déjà utilisé » — le
  jeton étant consommé, c'est le même chemin, sans cas particulier.

## Tests

**Unitaires purs (sans Spring)**

- `RawVerificationToken` : longueur attendue, alphabet URL-safe, deux appels donnent
  des jetons différents.
- `VerificationToken` : non expiré avant 24 h, expiré après, consommation, refus de
  double consommation.
- `VerifyAccount` : `toString()` ne divulgue pas le jeton.
- `VerificationNotification` : `toString()` ne divulgue pas le jeton.
- `User.verify()`.

**Intégration (`@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`)**

Un enregistreur en mémoire (`@Primary`) remplace `NotificationSender`, conformément à
la règle « tester le port, pas l'adapter ». Il permet de lire le jeton en clair côté
test et d'enchaîner sur la route réelle.

- Une inscription envoie une notification et persiste un jeton.
- `token_hash` ne contient jamais le clair (lecture SQL directe, comme
  `JpaUserRepositoryAdapterTest.projette_l_email_sur_une_colonne_texte`).
- `VerifyAccountHandler` : nominal, jeton falsifié, compte inconnu, expiré, déjà
  utilisé.
- `VerifyAccountController` : les quatre issues observables via MockMvc.
- Bout en bout : inscription → jeton lu dans l'enregistreur → `GET /verification` →
  `verified = true`.

Les tests de `RegistrationControllerTest` se répartissent sur
`ShowRegistrationFormControllerTest` et `RegisterUserControllerTest`.

## Hors-périmètre

- **Renvoyer un lien de vérification** — seconde intention, donc second ticket. C'est
  lui qui rendra pertinent le passage aux événements de domaine (décision 4).
- **Restreindre quoi que ce soit à un compte non vérifié** — il n'y a pas encore de
  login, donc rien à conditionner.
- **Purger les jetons expirés** — une ligne morte par compte ne justifie pas un batch.
- **CSRF et session** — la route de vérification est un GET ; la dette
  `SecurityConfig` reste celle du ticket « login ».
- **Un service de domaine `VerificationTokenIssuer`** — `RegisterUserHandler` passe à
  cinq dépendances, ce qui reste de l'orchestration et non de la logique. L'extraction
  se fera avec le ticket « renvoyer le lien », qui lui donnera un second appelant.

## Pointeurs

- `src/main/java/xyz/sterenn/secondbrain/users/` — le contexte à étendre
- `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/security/BCryptPasswordHasher.java`
  — gabarit de l'adapter `TokenHasher`
- `src/main/resources/db/migration/V3__create_users_users.sql` — style des migrations
- `.claude/rules/backend.md` — à modifier (règle « une classe de contrôleur par route »)
