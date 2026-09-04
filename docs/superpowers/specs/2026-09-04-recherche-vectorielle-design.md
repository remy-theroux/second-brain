# Retrouver les extraits les plus proches d'une question — design

Date : 2026-09-04 · Contexte : `knowledge` · Branche : `feat/recherche-vectorielle`

Ticket Notion : « RAG-8 — Retrouver les extraits les plus proches d'une question »
(<https://app.notion.com/p/3c0215c5e46e81e88090d5eb8219ae01>).

## Contexte

Les extraits vectorisés dorment en base. `knowledge_text_chunks` existe depuis RAG-5, son
index HNSW en `vector_cosine_ops` aussi, et le commentaire de la migration V10 le dit sans
détour : « l'index est écrit ici mais interrogé par personne — c'est RAG-8 qui écrira la
requête. » C'est ce ticket.

C'est le premier chemin de **lecture** du contexte `knowledge` qui ne se contente pas de
rendre ce qu'on a rangé. Tout ce qui précède écrivait : déposer, extraire, découper,
vectoriser. Ici on interroge, et la qualité de ce qui remonte ne se constate qu'à l'œil.
D'où l'insistance du ticket sur le diagnostic : la route rend les scores bruts, parce qu'un
défaut de pertinence ne s'instruit pas autrement.

La v1 s'en tient au cosinus pur. La recherche hybride (lexicale plus vectorielle) est un
chantier de la v1.1, et l'engager sans baseline mesurée reviendrait à optimiser à l'aveugle
— c'est RAG-14 qui fournira la mesure.

## Objectif

Une question posée à `GET /api/search?q=…` rend les huit extraits les plus proches, chacun
avec son texte, le nom de son document, sa position et son score de similarité.

**Réussi si :** une question dont la réponse figure dans un document ramène l'extrait qui la
contient parmi les trois premiers résultats ; chaque résultat est lisible sans autre appel ;
une base vide rend une liste vide et non une erreur ; les extraits d'un autre compte
n'apparaissent jamais.

## Attendus métier

Les trois scénarios du ticket :

```gherkin
Fonctionnalité: Recherche des extraits pertinents

  Scénario: Recherche pertinente
    Étant donné une base de connaissance contenant la réponse à une question
    Quand je pose cette question
    Alors l'extrait qui contient la réponse figure dans les trois premiers résultats

  Scénario: Diagnostic de la recherche
    Étant donné une question quelconque
    Quand je consulte les résultats bruts de la recherche
    Alors chaque extrait m'est rendu avec son contenu, son document d'origine et son score de similarité

  Scénario: Base de connaissance vide
    Étant donné une base de connaissance sans aucun document
    Quand je pose une question
    Alors j'obtiens une liste vide de résultats, et non une erreur
```

Deux exigences que le Gherkin ne porte pas, et qui valent autant :

- **Le cloisonnement.** Toute la base est cloisonnée par propriétaire ; la recherche ne fait
  pas exception. Elle n'est pas mentionnée dans le ticket parce qu'elle va de soi — raison de
  plus pour l'écrire ici et la tester.
- **La contrainte des 200 ms** hors appel d'embedding. Voir la décision 7 : elle ne devient
  pas une assertion.

## Décisions de conception

### 1. La recherche s'écrit en SQL natif, sur le dépôt Spring Data existant

`hibernate-vector` 7.2.19 enregistre bien une fonction HQL `cosine_distance(a, b)`, traduite
en `(?1 <=> ?2)` sur PostgreSQL (`PGVectorFunctionContributor`). L'écrire en HQL était donc
une option réelle, et un lecteur futur la proposera. Elle est écartée pour deux raisons qui
se cumulent :

- La jointure vers `knowledge_documents` est **imposée** — par le cloisonnement d'abord, par
  le nom du document ensuite. `Document` et `TextChunk` ne se référencent que par identifiant
  (ADR-0006), donc cette jointure s'écrit en HQL comme un produit cartésien filtré à la main.
  L'élégance qui justifiait le HQL disparaît là.
- Le binding du `float[]` reposerait sur l'inférence de type d'Hibernate, c'est-à-dire sur
  exactement le point que le ticket signale comme piège, et qu'on ne découvrirait qu'à
  l'exécution.

Entre deux SQL bruts, celui qui ne crée pas de catégorie d'adapter nouvelle est le moins
cher : la requête vit en `@Query(nativeQuery = true)` sur `SpringDataTextChunkRepository`,
qui reste package-private, et non dans un `JdbcTemplate` à part comme le suggérait le ticket.
Le projet n'a qu'une technique de persistance ; il continue de n'en avoir qu'une.

### 2. Le vecteur est lié en littéral pgvector, et cette conversion vit dans l'adapter

```sql
SELECT d.id             AS document_id,
       d.filename       AS filename,
       c.chunk_position AS chunk_position,
       c.heading        AS heading,
       c.text           AS chunk_text,
       1 - (c.embedding <=> CAST(:question AS vector)) AS similarity
FROM knowledge_text_chunks c
JOIN knowledge_documents d ON d.id = c.document_id
WHERE d.owner_id = :ownerId
ORDER BY c.embedding <=> CAST(:question AS vector)
LIMIT :limit
```

Le paramètre `:question` est une chaîne `"[0.1,0.2,…]"` et le `CAST` explicite s'appuie sur
la conversion d'entrée-sortie de PostgreSQL. Aucune conversion implicite n'est attendue :
c'est le point d'attention du ticket, et il est traité frontalement.

La fabrication du littéral à partir d'`Embedding` est un **détail de persistance** : elle vit
dans `JpaTextChunkRepositoryAdapter`, jamais dans le domaine, qui ne connaît qu'`Embedding`.

Trois précisions qui coûtent cher à redécouvrir :

- **Les alias évitent les mots réservés.** `AS position` et `AS text` heurtent la grammaire
  de PostgreSQL ; d'où `chunk_position` et `chunk_text`, que Spring Data mappe sur
  `getChunkPosition()` et `getChunkText()` d'une projection d'interface. Une projection de
  record n'est pas une option : Spring Data ne la sait pas construire depuis une requête
  native sans `@SqlResultSetMapping`.
- **Le score rendu est une similarité, pas une distance** : `1 - (… <=> …)`, donc 1 pour
  identique. C'est ce que lit un humain qui diagnostique.
- **PostgreSQL retombera en scan séquentiel** sur une petite table plutôt que d'attaquer le
  HNSW, et le filtre sur le propriétaire n'arrange rien. C'est le comportement normal de
  pgvector et ça n'invalide pas l'index, qui prendra le relais quand le volume le justifiera.

**Repli.** Si le `CAST` refuse un paramètre lié en `varchar`, cette seule méthode bascule sur
un `JdbcTemplate` et un `PGobject` typé `vector`. Rien d'autre du design ne bouge. C'est
pourquoi la première tâche du plan est un squelette qui prouve le binding contre
Testcontainers, avant tout le reste.

### 3. Le port rend un objet-valeur du domaine, pas des entités

`TextChunkRepository` gagne une méthode :

```java
List<ChunkMatch> findNearest(UUID ownerId, Embedding question, int limit);
```

Pas de second port : même table, même cycle de vie, et `DocumentRepository` mêle déjà lecture
et écriture. `ChunkMatch(UUID documentId, String filename, int position, Chunk chunk, double
similarity)` est un value object du domaine qui réutilise `Chunk` — lequel *est* déjà « un
extrait », titre et corps.

Rendre `List<TextChunk>` aurait perdu le score, que le ticket exige. Rendre un type de
`application/query/` aurait inversé le sens des dépendances : le domaine ne connaît pas
l'application.

`filename` y figure alors qu'il appartient à `Document` : c'est une projection de lecture,
pas un agrégat, et la jointure qui le rapporte est de toute façon payée par le cloisonnement.

### 4. Huit résultats, aucun plancher de similarité

`SearchPolicy.RESULTS = 8`, à la racine de `knowledge/domain/`, aux côtés des trois autres
policies. C'est une **règle du domaine**, pas une propriété de configuration : c'est RAG-9 qui
consommera ces huit extraits pour composer une réponse, pas un exploitant qui règle un
curseur. Même raisonnement qu'`AccessTokenPolicy.LIFETIME`.

Aucun paramètre `?k=` : ajouter un réglage pour une question qu'on ne se pose pas encore, et
qu'il faudrait valider et documenter, est de la souplesse payée d'avance.

Aucun score plancher non plus, et c'est le point important : une route de **diagnostic** doit
montrer les scores faibles, puisque c'est précisément là que se lit un défaut de pertinence.
Un seuil masquerait ce qu'on cherche à voir, et le bon seuil ne se connaîtra pas avant
RAG-14. La liste vide ne vient donc que d'une base vide.

### 5. La question est vectorisée nue

À l'indexation, ce qui part au modèle est préfixé : `Chunk.contextualised(filename)` produit
`Document: rapport.pdf — Section: Introduction` suivi du corps. La question, elle, part telle
que l'utilisateur l'a écrite. L'asymétrie est délibérée.

Le préfixe donne à l'extrait le contexte qu'il perd en sortant de son document ; une question
n'a pas ce problème, elle est déjà complète. Et `bge-m3` est un modèle **sans instruction de
rôle** — contrairement à un e5, qui exige `query:` d'un côté et `passage:` de l'autre, et
dont les scores s'effondrent si on inverse. Préfixer la question d'un miroir décoratif
ajouterait du bruit sans rien apporter.

C'est un réglage de pertinence, pas une structure : il se mesure, et RAG-14 le mesurera. S'il
faut le changer, ça coûte une revectorisation, pas une migration.

### 6. L'appel de vectorisation a lieu dans la transaction du query bus

`SpringQueryBus.ask` est `@Transactional(readOnly = true)`, donc l'aller-retour vers Ollama —
de l'ordre de la seconde pour un texte — tient une connexion PostgreSQL. C'est assumé et sans
contournement : c'est une lecture, rien n'est écrit, le bus est le chemin réel de production,
et l'application est mono-utilisateur.

Un Ollama à terre lève `EmbeddingUnavailableException`, que le contrôleur traduit en `503` —
même forme que l'échec de notification à l'inscription.

### 7. Les 200 ms se mesurent, elles ne s'assertent pas

Le ticket demande « recherche en moins de 200 ms hors appel d'embedding, mesuré en intégration
(Testcontainers) ». Elle ne devient pas une assertion chronométrique.

Une suite Testcontainers partagée par toute l'exécution, sur une CI dont la charge n'est pas
la nôtre, rend un chronomètre instable par construction : le test finirait par échouer sans
rien apprendre, et on le neutraliserait. Le contrôle est fait à la main une fois, sur la pile
`docker compose`, et sa mesure — la durée de la requête SQL seule, hors appel d'embedding —
est consignée dans le rapport de la tâche qui livre l'adapter. Le vrai garde-fou de
performance est RAG-14, qui mesure la recherche pour de bon.

### 8. La route rend les refus par champ, comme le reste de l'API

`SearchChunksController`, un seul mapping, `GET /api/search?q=…`. `/api/**` est déjà
authentifié : `SecurityConfig` ne bouge pas.

| Cas | Réponse |
|---|---|
| Résultats, y compris zéro | `200` + tableau de `ChunkMatchView` |
| `q` vide, blanc ou absent | `422 {"errors": {"q": "…"}}` |
| Ollama injoignable | `503 {"message": "…"}` |
| `sub` illisible | `401` |

Le paramètre est déclaré `@RequestParam(name = "q", defaultValue = "")` pour qu'un `q` absent
suive le même chemin qu'un `q` vide : un seul refus à écrire, un seul à tester.

Le refus lui-même est porté par le domaine. `Question` est un record d'un seul champ qui
ampute les espaces et refuse le vide en levant `InvalidQuestionException`, dont le message est
affichable tel quel — `Email` est le gabarit. Pas de longueur maximale : la ligne de requête
d'un `GET` est déjà bornée par le conteneur, et `bge-m3` tronque au-delà de sa fenêtre.

## Ce qui change dans le code

```
knowledge/domain/
├── SearchPolicy.java                        NOUVEAU  RESULTS = 8
├── valueobject/Question.java                NOUVEAU
├── valueobject/ChunkMatch.java              NOUVEAU
├── exception/InvalidQuestionException.java  NOUVEAU
└── port/TextChunkRepository.java            + findNearest

knowledge/application/query/
├── SearchChunks.java                        NOUVEAU
├── SearchChunksHandler.java                 NOUVEAU
└── ChunkMatchView.java                      NOUVEAU

knowledge/infrastructure/
├── persistence/SpringDataTextChunkRepository.java  + @Query natif, + projection
├── persistence/JpaTextChunkRepositoryAdapter.java  + littéral pgvector, + mapping
└── web/SearchChunksController.java                 NOUVEAU
```

Aucune migration, aucune dépendance nouvelle, aucun changement côté front, aucun ADR.

## Tests

| Test | Nature | Ce qu'il couvre |
|---|---|---|
| `QuestionTest` | unitaire | refuse le vide et le blanc, ampute les espaces |
| `SearchChunksTest` | intégration, par le bus | les trois scénarios Gherkin, le cloisonnement, le plafond à huit |
| `SearchChunksControllerTest` | MockMvc | la forme JSON, le `422`, le `401`, le `503` |

Trois pièges de montage, tous déjà payés ailleurs dans ce dépôt :

- **`KnowledgeFixture.unVecteur` ne convient pas.** Elle remplit toutes les dimensions de la
  même valeur, donc tous les vecteurs qu'elle produit sont **colinéaires** : leur distance
  cosinus est nulle deux à deux, et aucun ordre ne s'y lit. Il faut une fabrique qui oriente
  le vecteur sur une dimension choisie, sans quoi le test de pertinence ne teste rien.
- **`RecordingEmbeddingPortConfiguration` se vide en `@BeforeEach` et en `@AfterEach`** : le
  bean est partagé par le contexte Spring et le rollback ne le vide pas.
- **Un appel HTTP refusé est le dernier appel de son test** `@Transactional` : l'exception
  métier marque la transaction englobante « rollback-only ».

## Ce qui reste hors périmètre

- **Recherche hybride, reranking, filtres par document** — v1.1, explicitement écartés par le
  ticket.
- **Écran de recherche.** Le ticket parle d'un point d'entrée de diagnostic et son Gherkin ne
  mentionne aucune interface. Swagger UI suffit à instruire la pertinence. L'écran viendra
  avec RAG-9, quand il y aura autre chose à montrer qu'une liste de scores.
- **Mesure de la pertinence** — RAG-14.
- **Réindexation d'un document resté `EXTRACTED`** — RAG-7. Un document indexé avant ce
  ticket est cherchable ; un document jamais indexé ne l'est pas, et rien ici ne le rattrape.

## Pour aller plus loin

- RAG-9 consomme les extraits retrouvés pour composer une réponse.
- RAG-14 mesure la pertinence de cette recherche, et c'est elle qui rouvrira les décisions 4,
  5 et 7.
- ADR-0006 — deux agrégats se référencent par identifiant : la raison pour laquelle la
  jointure de la décision 1 ne peut pas s'écrire en HQL sans effort.
- ADR-0030 — chaque typologie a ses propres tables d'extraction : la raison du nom
  `knowledge_text_chunks`, et de ce que cette recherche ne vaudra que pour la typologie
  textuelle.
