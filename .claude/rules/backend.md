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

## Commits

- Préfixe conventionnel en minuscule (`feat:`, `fix:`, `refactor:`, `conf:`, `test:`)
  suivi d'une description en français.
- Un commit par tâche cohérente, avec les tests verts.
