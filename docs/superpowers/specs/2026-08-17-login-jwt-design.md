# Connexion par jeton d'accès JWT — design

Date : 2026-08-17 · Contexte borné : `users` · Branche : `feat/login`

Ticket Notion : « En tant qu'utilisateur je me connecte »
(<https://app.notion.com/p/3bf215c5e46e80a9b2dcfa3c47950762>)

## Contexte

Trois choses attendent ce ticket depuis la vérification par email :

1. `users_users.verified` passe à `true` depuis le lien reçu par mail, mais **rien ne
   conditionne encore quoi que ce soit à cette colonne** (écart assumé n° 3 du design
   précédent : « il n'y a pas encore de login, donc rien à conditionner »).
2. `SecurityConfig` laisse tout public (`anyRequest().permitAll()`), CSRF désactivé et
   session `STATELESS`, avec un `TODO` nommant explicitement le ticket « login »
   (écart assumé n° 2 de `CLAUDE.md`).
3. `.claude/rules/frontend.md` ne contient que « _À remplir._ » et la phrase « ce fichier
   attend le ticket qui posera les conventions d'interface ».

Le ticket demande trois choses : un jeton d'accès JWT obtenu par un échange de type
`password`, une route API authentifiée `/api/profile`, et un début d'application Vue.js
qui redirige vers `/login` quand le jeton n'est plus valable.

Aucun front autre que des templates Thymeleaf n'existe aujourd'hui. Aucune dépendance
Node, aucun `package.json`, et **ni JDK ni Node sur la machine hôte** : tout passe par
Docker.

## Objectif

Un utilisateur dont le compte est vérifié saisit son email et son mot de passe dans
l'application Vue, obtient un jeton d'accès JWT, et voit son espace connecté. Quand le
jeton expire ou disparaît, il retombe sur `/login`.

**Réussi si :** avec un compte créé puis vérifié, `POST /api/token` renvoie un JWT
décodable, `GET /api/profile` renvoie l'email de ce compte avec ce jeton et `401` sans
lui, et l'application Vue sur `/home` affiche l'email ou redirige vers `/login`.

## Attendus métier

Les deux scénarios du ticket, plus le refus du compte non vérifié — décision arbitrée
avec le porteur du ticket (voir décision 5) — et l'expiration côté front, qui est le
comportement explicitement demandé dans la section « Problème / Objectif ».

```gherkin
Fonctionnalité: Connexion

  Scénario: je me connecte avec succès
    Étant donné que je suis déconnecté sur la page de login
    Et que mon compte est vérifié
    Quand je remplis l'email et le mot de passe avec succès
    Alors je suis connecté
    Et mon espace connecté affiche mon email

  Scénario: je me connecte sans succès
    Étant donné que je suis déconnecté sur la page de login
    Quand je remplis l'email ou le mot de passe avec erreur
    Alors je reste sur la page de login avec un message d'erreur

  Scénario: mon compte n'est pas encore vérifié
    Étant donné un compte créé dont l'adresse email n'a pas été vérifiée
    Quand je remplis l'email et le mot de passe avec succès
    Alors la connexion est refusée avec un message m'invitant à vérifier mon adresse

  Scénario: une route protégée refuse un visiteur sans jeton
    Étant donné que je n'ai pas de jeton d'accès
    Quand j'appelle la route de profil
    Alors l'accès est refusé

  Scénario: mon jeton n'est plus valable
    Étant donné que mon jeton d'accès a expiré
    Quand j'ouvre mon espace connecté
    Alors je suis redirigé vers la page de login
```

## Décisions de conception

Chaque décision a été arbitrée avant l'écriture du plan ; l'alternative écartée est
notée pour que le choix ne soit pas rejoué sans raison.

### 1. Un endpoint maison à la forme du `password grant`, pas un serveur d'autorisation

`POST /api/token`, `application/x-www-form-urlencoded`, corps
`grant_type=password&username=…&password=…`. Réponse `200` :

```json
{"access_token": "eyJ…", "token_type": "Bearer", "expires_in": 3600}
```

Refus `400`, forme RFC 6749 §5.2 : `{"error": "invalid_grant", "error_description": "…"}`.
La signature et la validation utilisent `spring-boot-starter-security-oauth2-resource-server`
(qui apporte `spring-security-oauth2-jose` : `NimbusJwtEncoder` **et** `NimbusJwtDecoder`).

*Écarté :* Spring Authorization Server. **OAuth 2.1 a supprimé le `password` grant**, et
le serveur d'autorisation Spring ne l'implémente pas : il faudrait écrire un
`AuthenticationConverter` et un `AuthenticationProvider` sur mesure, déclarer un
`RegisteredClient` et accepter `/oauth2/token`. Beaucoup de machinerie pour un unique
client *first-party* qui n'a ni redirection ni consentement à gérer. On garde la
**forme** du protocole (paramètres, codes d'erreur, `token_type`, `expires_in`,
`Cache-Control: no-store`) sans son appareillage.

### 2. Signature symétrique HS256, secret sans valeur par défaut

Un seul processus signe et vérifie : une paire de clés asymétrique n'apporterait rien
tant qu'aucun tiers ne valide de jeton. `secondbrain.jwt.secret`, lu une seule fois
dans `config/JwtConfiguration`, qui construit les deux beans `JwtEncoder` et `JwtDecoder`.

**Le secret n'a aucune valeur par défaut dans `application.yml`** : sans la variable
d'environnement, l'application refuse de démarrer. C'est la leçon de l'écart assumé
n° 8 (`secondbrain.base-url` a un défaut qui ment en production) appliquée sur un cas
où le défaut serait bien pire : un secret de signature partagé et connu. Le confort de
développement est rendu là où il ne peut pas fuir en production — `compose.yaml`, qui
n'existe que pour le développement, fournit un défaut, et `src/test/resources/application.properties`
en fournit un pour la suite de tests.

HS256 exige une clé de 256 bits minimum. `JwtConfiguration` refuse un secret de moins
de 32 octets avec un message explicite, plutôt que de laisser Nimbus lever
« This key is too small for any standard JWK symmetric signing algorithm ».

*Écarté :* RS256 avec une paire de clés générée au démarrage. Une clé regénérée à chaque
redémarrage invalide tous les jetons en vol, et une clé persistée demande un magasin de
clés à gérer — pour un bénéfice nul en l'absence de tiers vérificateur.

### 3. Les revendications du jeton se limitent à `sub`, `iat` et `exp`

`sub` porte l'UUID du compte, seule identité stable. **Pas de revendication `email`** :
elle dupliquerait une donnée que `/api/profile` sert précisément à lire, et ferait du
jeton un porteur de donnée personnelle qui traîne dans le `localStorage` du navigateur.

*Écarté :* ajouter `email` pour épargner un aller-retour au front. C'est exactement
l'aller-retour que le ticket demande (« Pouvoir être authentifié via une route API
/api/profile afin de pouvoir maintenir une connexion ») : cet appel *est* la
vérification que la session tient encore.

Pas de revendication `iss` non plus : le décodeur à clé symétrique ne valide pas
l'émetteur par défaut, et écrire une revendication que personne ne vérifie est un
ornement.

### 4. Se connecter est une **query**, pas une commande

`AuthenticateUser(String email, String rawPassword) implements Query<AccessTokenView>`.

Le socle CQRS du projet est catégorique : « Une commande ne retourne rien. Tout besoin
de lecture passe par une query. » Or la connexion doit rendre un jeton et **n'écrit
rien en base** — pas de session, pas de trace, pas de compteur de tentatives. C'est une
lecture qui produit une preuve.

Conséquence assumée : la règle « une absence de résultat se représente par un `Optional`
vide, pas par une exception » ne s'applique pas ici. Cette query ne demande pas « existe-t-il
un compte ? » mais « délivre-moi un jeton » ; un refus est un **refus métier avec un
message affichable**, comme les trois refus de la route de vérification. Elle lève donc
`InvalidCredentialsException` ou `UnverifiedAccountException`.

*Écarté :* une commande `LogIn` qui déposerait le jeton dans un champ mutable. C'est
précisément la triche que l'interdiction de retour cherche à empêcher.

*Écarté aussi :* faire authentifier par un `UserDetailsService` et le
`DaoAuthenticationProvider` de Spring Security. Le domaine possède déjà son port
`PasswordHasher` et son entité `User` ; passer par `UserDetails` ajouterait un modèle
parallèle au nôtre, et l'adapter de hachage existant deviendrait mort.

### 5. Un compte non vérifié est refusé, avec son propre message

C'est l'usage que la colonne `verified` attendait. Le message est distinct de
« identifiants incorrects » : l'utilisateur légitime doit savoir qu'il lui reste un lien
à cliquer, sinon le produit est cassé sans explication.

Le contrôle **vient après la vérification du mot de passe**. Ainsi seul celui qui connaît
déjà le mot de passe apprend que le compte existe mais n'est pas vérifié. Et l'existence
d'un compte est de toute façon déjà observable : le formulaire d'inscription répond
« email déjà utilisé ». L'oracle n'est donc pas ouvert par ce ticket.

*Écarté :* un message indistinct de « identifiants incorrects ». Cohérent avec le choix
fait sur `/verification`, mais là-bas le secret était le jeton lui-même ; ici l'appelant
a déjà prouvé qu'il connaît le mot de passe.

### 6. Le front est un projet séparé, servi par Vite, qui *proxifie* le back

`frontend/` à la racine, hors du build Gradle. En développement, un service `frontend`
dans `compose.yaml` fait tourner le serveur Vite sur `5173` avec un proxy
`/api` → `http://app:8080`.

Le navigateur ne voit **qu'une seule origine** : aucune configuration CORS, aucun
`@CrossOrigin`, aucun préflight. C'est la raison principale de ce choix — CORS aurait
été une deuxième surface de sécurité à régler dans le même ticket.

*Écarté :* invoquer Node depuis Gradle et copier le bundle dans
`src/main/resources/static`. Même origine aussi, mais le build Java deviendrait
dépendant de Node (donc la CI, donc le `Dockerfile` de production, donc l'image de
développement), et le rechargement à chaud du front passerait par Gradle. Pour un
« tout début d'app », c'est payer d'avance.

Corollaire assumé : **le build de production du front est hors périmètre.** Rien ne
sert `frontend/dist` aujourd'hui. Le ticket demande un début d'application, pas un
déploiement.

### 7. Le jeton vit dans `localStorage`

Il survit à un rafraîchissement de page, ce qui est le minimum pour que « maintenir une
connexion » veuille dire quelque chose. Le prix est connu : une faille XSS dans le front
donne le jeton. La parade réelle — cookie `httpOnly` `Secure` `SameSite` plus jeton de
rafraîchissement, donc CSRF à réactiver — est un ticket à elle seule.

Durée de vie courte (1 h) et **pas de jeton de rafraîchissement** : à l'expiration, on
se reconnecte. Le ticket ne demande rien de plus.

### 8. La durée de vie du jeton est une règle du domaine, pas un réglage

`AccessTokenPolicy.LIFETIME = Duration.ofHours(1)`, à la racine de `users/domain/`, à
côté de `PasswordPolicy` : statique, sans dépendance, testable sans Spring — et
symétrique de `VerificationToken.VALIDITY`. Le temps entre par paramètre, jamais par un
`Instant.now()` interne.

*Écarté :* une propriété `secondbrain.jwt.lifetime`. Un exploitant qui la pousserait à
30 jours changerait la sécurité du produit par un fichier de configuration.

### 9. `STATELESS` et CSRF désactivé cessent d'être une dette

L'écart assumé n° 2 promettait que « le ticket login lèvera cette dette » en introduisant
une authentification par session. **Ce ticket la lève autrement** : l'authentification se
fait par jeton porteur dans un en-tête `Authorization`, jamais par cookie. Un navigateur
n'envoie pas spontanément un en-tête `Authorization` : il n'y a rien à contrefaire depuis
un site tiers. `STATELESS` devient donc le réglage correct, et CSRF désactivé un choix
cohérent — plus une dette à rembourser. Les formulaires Thymeleaf restants (`POST /register`)
ne portent aucune session ni cookie d'authentification : ils ne sont pas non plus
exposés. `CLAUDE.md` doit être corrigé en ce sens ; le jour où un cookie
d'authentification apparaît, CSRF redevient obligatoire.

### 10. `/api/profile` renvoie `401` quand le compte a disparu

Un jeton bien signé dont le `sub` ne correspond plus à aucune ligne n'identifie personne.
Répondre `404` inviterait le front à traiter ce cas ; `401` le fait retomber sur le
chemin déjà prévu — le garde de route renvoie vers `/login`. Un seul chemin d'échec côté
front.

## Architecture

```
config/
├── SecurityConfig.java              ← /api/profile authentifié + oauth2ResourceServer
├── JwtConfiguration.java            ← NOUVEAU : JwtEncoder + JwtDecoder (HS256)
└── OpenApiConfig.java               ← + schéma de sécurité « bearer »

users/
├── domain/
│   ├── AccessTokenPolicy.java               ← NOUVEAU (durée de vie, règle pure)
│   ├── valueobject/AccessToken.java         ← NOUVEAU (valeur + expiration, toString masqué)
│   ├── port/AccessTokenIssuer.java          ← NOUVEAU
│   └── exception/
│       ├── InvalidCredentialsException.java ← NOUVEAU
│       └── UnverifiedAccountException.java  ← NOUVEAU
├── application/query/
│   ├── AuthenticateUser.java                ← NOUVEAU (Query<AccessTokenView>)
│   ├── AuthenticateUserHandler.java         ← NOUVEAU
│   ├── AccessTokenView.java                 ← NOUVEAU (modèle de lecture)
│   ├── FindUserById.java                    ← NOUVEAU (Query<Optional<UserView>>)
│   ├── FindUserByIdHandler.java             ← NOUVEAU
│   ├── UserView.java                        ← + fabrique of(User)
│   └── FindUserByEmailHandler.java          ← utilise UserView.of
└── infrastructure/
    ├── security/JwtAccessTokenIssuer.java   ← NOUVEAU (adapter du port)
    └── web/
        ├── IssueAccessTokenController.java  ← NOUVEAU : POST /api/token
        ├── AccessTokenResponse.java         ← NOUVEAU : corps RFC 6749 §5.1
        ├── OAuth2ErrorResponse.java         ← NOUVEAU : corps RFC 6749 §5.2
        └── ShowProfileController.java       ← NOUVEAU : GET /api/profile

frontend/                            ← NOUVEAU, hors build Gradle
├── package.json · vite.config.js · index.html
└── src/
    ├── main.js · App.vue
    ├── api/client.js                ← seul endroit qui parle HTTP
    ├── stores/auth.js               ← jeton, expiration, profil (pinia)
    ├── router/index.js              ← routes + garde d'authentification
    └── views/{LoginView,HomeView}.vue
```

### Domaine

**`AccessTokenPolicy`** — `LIFETIME = Duration.ofHours(1)` et
`expiresAt(Instant maintenant)`. Rien d'autre.

**`AccessToken`** — `record AccessToken(String value, Instant expiresAt)`. Refuse une
valeur vide et une expiration nulle dans son constructeur compact. `toString()` masqué :
c'est un porteur d'identité, il ne doit apparaître dans aucun log. `expiresIn(Instant
maintenant)` rend le nombre de secondes restantes, jamais négatif — c'est ce que la
réponse HTTP appelle `expires_in`.

**`AccessTokenIssuer`** — port sortant :
`AccessToken issue(UUID subject, Instant issuedAt, Instant expiresAt)`. Le domaine dit
*pour qui* et *jusqu'à quand* ; l'adapter décide du format. Le port ne connaît ni JWT,
ni signature, ni revendication.

### Application

`AuthenticateUserHandler` (dépendances : `UserRepository`, `PasswordHasher`,
`AccessTokenIssuer`, `Clock`) :

1. `new Email(command.email())` — `InvalidEmailException` attrapée et retraduite en
   `InvalidCredentialsException` : un email mal formé est un identifiant faux, pas une
   panne, et ne mérite pas un message différent.
2. `userRepository.findByEmail(email)` — vide → `InvalidCredentialsException`.
3. `passwordHasher.matches(...)` — faux → `InvalidCredentialsException`.
4. `!user.isVerified()` → `UnverifiedAccountException` (après le mot de passe, décision 5).
5. `maintenant = clock.instant()` ; `expiration = AccessTokenPolicy.expiresAt(maintenant)` ;
   `accessTokenIssuer.issue(user.getId(), maintenant, expiration)`.
6. `new AccessTokenView(accessToken.value(), accessToken.expiresIn(maintenant))`.

`FindUserById` double `FindUserByEmail` pour le profil, sur l'identité que porte le
jeton. La conversion `User` → `UserView` devient `UserView.of(User)`, appelée par les
deux handlers.

### Infrastructure

**`JwtAccessTokenIssuer`** — `JwtClaimsSet.builder().subject(subject.toString())
.issuedAt(issuedAt).expiresAt(expiresAt)`, puis `jwtEncoder.encode(JwtEncoderParameters.from(claims))`.
`NimbusJwtEncoder.withSecretKey(...)` pose déjà l'en-tête HS256 par défaut : aucun
`JwsHeader` à construire.

**`JwtConfiguration`** — un `SecretKeySpec` HmacSHA256 sur les octets UTF-8 du secret,
`NimbusJwtEncoder.withSecretKey(cle).build()` et
`NimbusJwtDecoder.withSecretKey(cle).macAlgorithm(MacAlgorithm.HS256).build()`. Le
décodeur applique par défaut `JwtTimestampValidator`, dont la tolérance d'horloge est de
**60 secondes** — un test d'expiration doit donc dater son jeton de bien plus que ça.

**`SecurityConfig`** — `/api/profile` en `authenticated()`, le reste en `permitAll()`,
`oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))`, qui prend le bean
`JwtDecoder`. `STATELESS` et CSRF désactivé sont conservés, désormais comme des choix
motivés (décision 9).

**`IssueAccessTokenController`** — paramètres lus en `@RequestParam(defaultValue = "")`
comme `VerifyAccountController` le fait déjà, pour que l'absence d'un paramètre donne
*notre* erreur et non le `400` générique de Spring. Trois refus de forme
(`invalid_request`, `unsupported_grant_type`) et un refus d'identité (`invalid_grant`).
`Cache-Control: no-store` sur la réponse de succès, comme l'exige RFC 6749 §5.1.

**`AccessTokenResponse` / `OAuth2ErrorResponse`** — records annotés `@JsonProperty`
(`com.fasterxml.jackson.annotation`, package inchangé en Jackson 3) pour produire le
`snake_case` du protocole sans le faire remonter dans les noms Java.

### Front

`api/client.js` est le seul module qui connaît HTTP : il pose l'en-tête
`Authorization`, traduit `401` en `UnauthorizedError` et le corps `error_description`
en message. Le store `auth` détient le jeton, son instant d'expiration et le profil ;
`isAuthenticated` est vrai si le jeton existe **et** n'est pas expiré. Le garde de route
est une fonction exportée — donc testable avec un `createMemoryHistory` — et non une
closure anonyme dans `beforeEach`.

Deux vues seulement : `LoginView` (formulaire, message d'erreur) et `HomeView` (charge
le profil, affiche l'email, bouton de déconnexion). `/` redirige vers `/home`, qui exige
l'authentification : un visiteur sans jeton valable atterrit sur `/login` sans qu'on ait
à écrire de page d'accueil publique côté Vue — celle de Thymeleaf existe déjà.

## Tests

**Unitaires purs (sans Spring, sans Node)**

- `AccessTokenPolicy` : l'expiration tombe une heure après l'instant donné.
- `AccessToken` : refus d'une valeur vide et d'une expiration nulle, `toString()` masqué,
  `expiresIn` en secondes, jamais négatif.
- `AuthenticateUser` : `toString()` ne divulgue pas le mot de passe.

**Intégration back (`@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`)**

- `JwtConfiguration` : un jeton encodé se décode, ses revendications `sub`/`exp` sont
  celles qu'on a posées ; un jeton daté dans le passé est refusé par le décodeur.
- `AccessTokenIssuer` (le **port**, pas l'adapter) : le jeton émis se décode et porte le
  bon `sub`.
- `AuthenticateUserHandler` : succès, mot de passe faux, email inconnu, email mal formé,
  compte non vérifié.
- `IssueAccessTokenController` : `200` et forme du corps, `invalid_grant` sur mauvais mot
  de passe, message dédié sur compte non vérifié, `unsupported_grant_type`,
  `invalid_request`, en-tête `Cache-Control`.
- `ShowProfileController` : `401` sans jeton, `401` avec un jeton expiré, `200` et bon
  email avec un jeton obtenu par `/api/token`.
- `SecurityConfig` : `/api/profile` répond `401` et les routes publiques restent
  accessibles.

Un utilitaire de test `users/AccountFixture` crée un compte **et le vérifie** en
rejouant le vrai chemin (inscription, lecture du jeton dans
`RecordingNotificationSender`, `VerifyAccount`) : aucun test ne bascule `verified` par
un raccourci qui contournerait le domaine.

**Unitaires front (Vitest, environnement jsdom)**

- `stores/auth` : `login` mémorise jeton et expiration, `login` en échec propage le
  message du serveur, `isAuthenticated` est faux après expiration, `logout` vide le
  `localStorage`, un `401` sur le profil déconnecte.
- `router` : `/home` sans jeton redirige vers `/login` ; avec un jeton valable il est
  atteint ; `/login` avec un jeton valable redirige vers `/home`.

Pas de test de rendu de composant : aucun `@vue/test-utils`, aucun navigateur. Ce qui
peut casser silencieusement, c'est la logique de session, et elle est couverte.

## Hors-périmètre

- **Jeton de rafraîchissement et déconnexion côté serveur** (donc révocation, donc liste
  de refus). Un jeton JWT reste valable jusqu'à son `exp` : « se déconnecter » efface le
  jeton du navigateur, rien de plus. Ticket dédié.
- **Limitation du débit sur `/api/token`.** Rien n'empêche aujourd'hui une recherche
  exhaustive de mot de passe. À traiter avec le durcissement de la production, au même
  endroit que la journalisation des tentatives.
- **Le temps de réponse trahit l'existence d'un compte** : un email inconnu revient sans
  calcul BCrypt, donc plus vite. Corriger demande de hacher un leurre systématiquement ;
  sans limitation de débit, ce serait boucher une fissure à côté d'une porte ouverte.
- **Build de production du front** (décision 6) et **service du bundle par Spring**.
- **CSS et mise en forme.** Les deux vues sont du HTML nu, comme les templates Thymeleaf
  existants.
- **Rendre `verified` obligatoire ailleurs que sur la connexion.**
- **Renvoyer un lien de vérification**, toujours attendu par son propre ticket, et dont
  le besoin devient criant maintenant qu'un compte non vérifié ne peut plus se connecter.
- **Retirer les templates Thymeleaf** au profit du front Vue. L'inscription et la
  vérification restent servies par Spring ; les deux mondes cohabitent le temps que le
  front grandisse.

## Pointeurs

- `src/main/java/xyz/sterenn/secondbrain/users/` — le contexte à étendre
- `users/infrastructure/security/BCryptPasswordHasher.java` — gabarit de l'adapter d'un
  port de sécurité, et le `PasswordHasher` que la connexion réutilise tel quel
- `users/application/query/FindUserByEmailHandler.java` — gabarit de handler de query
- `users/infrastructure/web/VerifyAccountController.java` — le parti pris
  `@RequestParam(defaultValue = "")` repris par `/api/token`
- `src/test/java/xyz/sterenn/secondbrain/users/RecordingNotificationSenderConfiguration.java`
  — comment un test récupère le jeton de vérification en clair
- `.claude/rules/frontend.md` — à remplir : c'est ce ticket qui pose les conventions
- `CLAUDE.md` — écarts assumés n° 2 (à réécrire, décision 9) et n° 3 (`FindUserByEmail`
  toujours sans écran : le profil lit par identifiant)
