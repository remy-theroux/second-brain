# Règles backend — Java / Spring Boot

Ces règles complètent l'architecture décrite dans `CLAUDE.md`. Elles ne sont pas des
préférences de style : chacune est un piège déjà rencontré sur ce repo.

## Placement du code

Avant d'écrire une classe, décider de sa couche :

| Ce que fait la classe | Où elle va |
|---|---|
| Porte un invariant sur une valeur, se normalise à la construction | `<contexte>/domain/valueobject/` |
| Est un agrégat : une identité, un cycle de vie | `<contexte>/domain/entity/` |
| Déclare un besoin du domaine vers l'extérieur (interface) | `<contexte>/domain/port/` — c'est un **port** |
| Signale un refus métier | `<contexte>/domain/exception/` |
| Porte une règle métier pure, transverse aux types ci-dessus | `<contexte>/domain/` (racine) |
| Exprime une intention d'écriture et l'orchestre | `<contexte>/application/command/` |
| Exprime une lecture et sa projection | `<contexte>/application/query/` |
| Implémente un port avec une techno concrète | `<contexte>/infrastructure/<techno>/` |
| Projette un type du domaine sur une colonne (`AttributeConverter`) | `<contexte>/infrastructure/persistence/` |
| Traduit HTTP en commande/query | `<contexte>/infrastructure/web/` |
| Est une forme de réponse d'erreur commune à toutes les routes | `shared/web/` |
| Sert plusieurs contextes sans logique métier | `shared/` |

- **Le domaine n'importe jamais `org.springframework.*` ni `…infrastructure.*`.**
  Seule exception actée : les annotations `jakarta.persistence` sur l'entité `User`. Elle
  **ne s'étend pas au mapping** : un `AttributeConverter` est un détail de persistance, il
  vit dans `infrastructure/persistence/` et aucune classe du domaine ne le nomme.
- `domain/` se découpe en `entity/`, `valueobject/`, `port/` et `exception/`, et garde à sa
  racine les règles métier pures sans dépendance (`PasswordPolicy`). Ne pas créer un
  sous-package d'avance : il naît quand la première classe l'exige.
- Un nouveau bounded context reprend la même arborescence à trois couches. `users`
  est le gabarit.
- Les classes purement techniques d'un adapter sont **package-private** quand rien
  au-dehors ne doit en dépendre (voir `SpringDataUserRepository`). Exception :
  `EmailAttributeConverter` reste `public` — package-private fonctionnerait, mais plus
  aucun appel ne le référence, et le rendre invisible en ferait du code mort en apparence.

## Nommage

- **Un champ, un paramètre ou une variable de type repository porte le nom de son type
  en lowerCamelCase** : `userRepository`, `verificationTokenRepository`,
  `springDataUserRepository`. Jamais le pluriel de l'entité (`users`, `tokens`), jamais
  une abréviation de la techno (`jpa`, `repo`). Le pluriel se lit comme une collection en
  mémoire alors que l'appel part en base, et il devient ambigu dès qu'un contexte
  manipule deux repositories. Cette règle vaut aussi dans les tests.

## Bus, commandes et queries

- **Ne jamais annoter un handler avec `@Transactional`.** La transaction appartient
  au bus. Annoter le handler le fait proxifier en JDK proxy, ce qui casse la
  résolution de son type générique au démarrage — l'appli ne démarre plus.
- **Toute exception métier hérite de `RuntimeException`.** Une exception checked ne
  déclenche pas de rollback avec les réglages Spring par défaut : le rollback promis
  par le `CommandBus` serait silencieusement faux.
- Une commande est un **record immuable** portant des `String` bruts, tels que saisis.
  Elle transporte l'intention, elle ne la valide pas : c'est le handler qui convertit
  en value objects, et le value object qui refuse une valeur invalide.
- Une commande ne retourne rien. Tout besoin de lecture passe par une query.
- Une query porte son type de retour dans `Query<R>`. Une absence de résultat se
  représente par un `Optional` vide, pas par une exception.
- Un handler est **sans état** et traite un seul message.
- Une query renvoie un **modèle de lecture** dédié (`UserView`), jamais l'agrégat :
  la projection n'expose pas l'empreinte du mot de passe et évolue au rythme des écrans.
- Redéfinir `toString()` sur toute commande transportant un secret, pour ne jamais
  laisser fuiter le clair dans un log ou un message d'échec d'assertion
  (voir `RegisterUser`).

## Événements métier

- Un événement est un **record au passé** dans `<contexte>/domain/event/`, implémente
  `DomainEvent` (`shared/event/`), n'importe rien de Spring, et porte des identifiants —
  pas l'état. Le consommateur relit.
- **C'est le handler qui publie**, par le port `DomainEventPublisher`, en dernière étape.
  Pas depuis l'agrégat, pas depuis un adapter, pas depuis un contrôleur.
- Jamais d'`ApplicationEventPublisher` de Spring pour un fait métier : les deux familles
  d'événements restent séparées.
- Un nouvel événement se **déclare** dans le `DomainEventRegistration` de son contexte
  (`<contexte>/infrastructure/messaging/`), sinon le convertisseur ne le connaît pas et la
  désérialisation le refuse — c'est voulu, un `Class.forName` sur un nom venu du réseau
  n'est pas une option.
- Un listener est un **adapter entrant** dans `<contexte>/infrastructure/messaging/` :
  `@Profile("worker")`, une classe, une queue, une commande dispatchée sur le bus. Aucune
  règle métier, aucun accès direct à un repository.
- Un test qui observe une publication observe un **commit** : pas de `@Transactional`
  sur la classe, nettoyage explicite en `@AfterEach`. Un test du rôle worker pose
  `@ActiveProfiles("worker")` **et** `webEnvironment = NONE`.

## Domaine

- Un value object valide et normalise **dans son constructeur compact** : il doit être
  impossible d'en construire un invalide. Deux écritures d'une même valeur doivent être
  égales (`Email` fait `trim` + minuscules).
- Un agrégat expose une **fabrique statique** nommée par l'intention (`User.register`)
  et garde son constructeur privé, pour que ses invariants de naissance tiennent.
- Les messages d'exception métier sont **affichables tels quels à l'utilisateur** :
  ils énoncent la règle, en français, sans jargon technique.
- Les règles métier pures (`PasswordPolicy`) sont statiques et sans dépendance :
  elles se testent sans Spring.
- Le découpage de `domain/` transforme les anciens voisinages de package en imports
  explicites, **y compris en Javadoc** : un `{@link}` ou un `@throws` vers un type d'un
  autre sous-package a besoin de son import, sinon le lien ne résout plus (voir
  `UserRepository.save` et `EmailAlreadyUsedException`). `javac` ne le signale pas.

## Adapters

- Un adapter de persistance **traduit les erreurs techniques en erreurs métier** :
  aucune exception Spring ne doit remonter à l'application ni au domaine
  (`DataIntegrityViolationException` → `EmailAlreadyUsedException`).
- Utiliser `saveAndFlush` quand la traduction d'une violation de contrainte doit se
  faire dans le `try/catch` : sans flush explicite, l'erreur ne survient qu'au commit,
  hors de portée.
- **Un `AttributeConverter` porte `@Converter(autoApply = true)` et vit dans
  `infrastructure/persistence/`.** C'est ce qui dispense l'entité de déclarer
  `@Convert(converter = …)`, donc de rien importer de l'infrastructure. Pas de
  `@Component` : Hibernate instancie le converter lui-même.
- Le prix d'`autoApply` : **plus aucune ligne de code ne référence le converter**. Il n'est
  trouvé que par le scan de packages — `PersistenceManagedTypesScanner` filtre sur
  `@Entity`, `@Embeddable`, `@MappedSuperclass` et `@Converter`, à partir du package de
  `SecondBrainApplication` (ce projet n'a pas d'`@EntityScan`). Le sortir de
  `xyz.sterenn.secondbrain`, ou ajouter un `@EntityScan` plus étroit, le retirerait
  silencieusement du scan. Ne jamais supprimer un converter au motif qu'il paraît inutilisé.
- La panne, elle, est bruyante : `Email` n'étant ni `@Embeddable` ni `Serializable`, un
  converter non découvert fait échouer le démarrage sur `Could not determine recommended
  JdbcType for Java type '…Email'`. Aucun chemin ne mène à un contexte qui démarre avec un
  mapping faux.
- `autoApply` vaut pour **toute l'unité de persistance** : tout attribut `Email`, dans
  n'importe quel bounded context, passera par ce converter. C'est voulu ici.
- Un contrôleur ne contient **aucune règle métier**. Il valide la présence des champs,
  dispatche, et traduit les exceptions métier en erreurs de champ.
- **Une classe de contrôleur ne porte qu'un seul mapping**, et elle est nommée par
  l'intention de la route, pas par son verbe HTTP : `ShowRegistrationFormController`
  et `RegisterUserController`, pas `RegistrationController`. Un contrôleur mono-route
  n'injecte que ce dont sa route a besoin — celui qui affiche le formulaire ne connaît
  pas le `CommandBus` — et son test n'a qu'un seul sujet. Le nom se lit comme celui
  d'une commande : un verbe et son objet.
- Ne pas dupliquer une règle du domaine dans la validation du formulaire — les deux
  divergeraient. `@NotBlank` côté form, format et robustesse côté domaine.
- Après un POST réussi : redirect-after-post.
- **Une route qui reçoit un multipart exige `spring.servlet.multipart.resolve-lazily=true`.**
  Sans lui, `MaxUploadSizeExceededException` est levée par `DispatcherServlet.checkMultipart`,
  donc **avant** qu'un contrôleur soit désigné : aucun `@ExceptionHandler` de contrôleur ne
  la voit, et il faudrait un `@RestControllerAdvice` global — ce que ce projet refuse. Avec
  lui, la résolution a lieu au binding de l'argument, et le refus se traduit auprès de sa
  route.
- Un refus qui doit transporter plus qu'une phrase mérite son propre record de réponse
  (`DuplicateDocumentResponse` porte l'identifiant du doublon). Noyer un identifiant dans un
  message le rend inexploitable par l'appelant.

## Base de données

- **Flyway est maître du schéma**, `ddl-auto: validate`. Toute évolution passe par un
  nouveau `src/main/resources/db/migration/V<n>__<nom>.sql`.
- **Ne jamais supprimer ni modifier une migration déjà appliquée.** Pour retirer une
  table, ajouter une migration qui la `DROP`.
- Si Hibernate remonte `Schema-validation: wrong column type` au démarrage, corriger
  la migration ou les annotations de l'entité — **jamais `ddl-auto`**.
- Les tables sont préfixées par leur bounded context (`users_users`).
- Les colonnes de longueur bornée portent un `length` explicite côté entité :
  `validate` compare les métadonnées au schéma réel.

## Tests

- **Pattern d'intégration imposé** : `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`.
  **Ne pas introduire `@DataJpaTest`** : il remplace la datasource et casse Flyway +
  Testcontainers.
- Tout ce qui peut être testé **sans Spring** l'est en test unitaire pur (domaine, bus).
- `@Transactional` sur la classe de test fait rouler chaque test en arrière — la
  PostgreSQL Testcontainers est partagée par toute la suite. **Sauf** quand le test
  observe un rollback : la transaction englobante le masquerait, il faut alors nettoyer
  explicitement en `@AfterEach` (voir `CommandBusTransactionTest`).
- Tester le **port**, pas l'adapter : injecter `UserRepository`, pas
  `JpaUserRepositoryAdapter`. C'est le contrat du domaine qui est vérifié.
- Dispatcher via le bus plutôt qu'appeler le handler en direct : c'est le chemin réel
  de production.
- Noms de méthodes de test en français avec des underscores :
  `refuse_un_email_deja_utilise`. Assertions AssertJ.
- Un test par scénario Gherkin du ticket, au niveau où le scénario est observable.
- **Dans un test `@Transactional`, un appel HTTP refusé doit être le dernier du test.**
  L'exception métier traverse le proxy transactionnel du bus et marque la transaction
  englobante « rollback-only » ; la requête suivante échouerait sur une
  `UnexpectedRollbackException` sans rien apprendre sur la route. Ce qu'il reste à vérifier
  après un refus se lit **par le port**, dans la transaction du test, pas par une seconde
  requête.
- **`@Transactional` annule la base, jamais le disque.** Un test qui écrit un fichier le
  nettoie explicitement en `@AfterEach`, sans quoi il laisse derrière lui un état qu'aucune
  ligne ne désigne — et que le refus d'écrasement de l'adapter de stockage transformera en
  échec lors d'une exécution ultérieure.
- **MockMvc ne traverse aucun analyseur multipart** : un `MockMultipartFile` est déjà
  découpé, et `MaxUploadSizeExceededException` n'y sera jamais levée. Ce qui dépend de
  l'analyse réelle du corps se teste sur un vrai serveur (`webEnvironment = RANDOM_PORT` et
  `RestTestClient.bindToServer()`, qui vient de `spring-test` et ne demande aucune
  dépendance de plus).
- **Un corps volumineux exige `RestTestClient.bindToServer(new SimpleClientHttpRequestFactory())`.**
  La fabrique par défaut s'appuie sur le client HTTP du JDK, qui émet en
  `Transfer-Encoding: chunked` et abandonne sur une `IOException` — « chunked transfer
  encoding, state: READING_LENGTH » — plutôt que de lire la réponse que le serveur a
  pourtant déjà envoyée. Le test échoue alors que la route répond correctement : vérifier
  au `curl` avant de conclure que le code est en cause.
- Un mapping qui ne tient qu'à un scan de packages se vérifie **en intégration**. Un test
  unitaire d'`EmailAttributeConverter` passerait au vert même si Hibernate ne l'appliquait
  jamais ; ce qui fait foi, c'est `SecondBrainApplicationTests` pour la découverte, et
  `JpaUserRepositoryAdapterTest.projette_l_email_sur_une_colonne_texte` pour le contenu
  réel de la colonne.

## Dépendances et build

- **Ne pas pinner les versions Spring** dans `gradle/libs.versions.toml` : elles
  viennent du BOM Spring Boot. Les starters s'ajoutent sans version.
- Ne pinner que ce que le BOM ne couvre pas (plugins, springdoc).
- Toute dépendance nouvelle passe par le version catalog quand elle porte une version.

## Formatage

- **Le style est décidé par Spotless + palantir-java-format**, pas par le rédacteur :
  `make format-back` avant de committer, et ne pas se battre avec le résultat. Une chaîne
  fluent que le formateur casse en une ligne par maillon est correcte ; la remettre à la
  main la fera revenir au prochain `spotlessApply`.
- Palantir et non google-java-format, y compris en style AOSP : ce dernier fait exploser
  les chaînes de builders (`SecurityConfig`) en cascades de huit espaces et repousse le
  code si loin à droite que les commentaires de fin de ligne se retrouvent redécoupés en
  milieu de phrase.
- **Le Javadoc et les commentaires ne sont jamais reformatés** — seule leur indentation
  suit le code. Leur mise en forme reste donc entièrement à la charge du rédacteur : c'est
  voulu, ce sont eux qui portent le raisonnement.
- `spotlessCheck` est accroché à la tâche `check`, donc à `build` : la CI échoue sur du
  Java mal formaté sans qu'aucune étape dédiée n'apparaisse dans `ci.yml`.
- Les `--add-exports` de `gradle.properties` sont la condition pour que le formateur
  tourne (API internes de javac, fermées par JEP 396). Ne pas les retirer.

## Commits

- Préfixe conventionnel en minuscule (`feat:`, `fix:`, `refactor:`, `conf:`, `test:`)
  suivi d'une description en français.
- Un commit par tâche cohérente, avec les tests verts.
