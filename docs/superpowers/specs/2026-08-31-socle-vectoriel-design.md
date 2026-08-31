# Le socle vectoriel : pgvector, Ollama et le port d'embeddings — design

Date : 2026-08-31 · Contexte : `knowledge` · Branche : `feat/chunk-and-ambeddings`

Ticket Notion : « RAG-5 — Découper un document en extraits contextualisés »
(<https://app.notion.com/p/3c0215c5e46e8190beeedb048ca7889b>), **premier des deux
livrables**. Le second, le découpage lui-même, est décrit par
`2026-08-31-decoupage-extraits-design.md` et ne peut rien construire tant que celui-ci
n'existe pas.

## Contexte

RAG-5 est écrit comme s'il n'avait qu'à découper. Il suppose acquis une base pgvector, un
Ollama servant `bge-m3`, et un port `EmbeddingPort` — c'est-à-dire le contenu de RAG-1 et
de RAG-2, tous deux marqués *Done* dans Notion.

**Aucun des trois n'existe dans le dépôt.** Un `grep` sur `pgvector`, `ollama`, `embedding`,
`vector` et `springframework.ai` ne rend qu'une ligne, et c'est un commentaire dans la
javadoc de `DocumentStatus`. L'image de la base est `postgres:17-alpine`, `compose.yaml` ne
porte aucun service d'inférence, et le version catalog ignore Spring AI.

Ce qui *a* été livré sous ces numéros est autre chose, et c'est mieux : le socle Boot 4 /
Gradle / hexagonal par contexte borné, et une orchestration par worker RabbitMQ là où RAG-6
décrivait un `IngestDocumentUseCase` en `@Async` et des `batchUpdate` de `JdbcTemplate`. Les
tickets RAG-1, RAG-2 et RAG-6 décrivent un projet Maven / Java 21 / Spring Boot 3.x qui
n'est pas celui-ci. **Ils ne font plus autorité sur leurs moyens ; seulement sur leurs
intentions.**

D'où ce ticket : livrer le socle que RAG-5 croit trouver, et rien de plus. Il ne découpe
rien, ne vectorise aucun document, n'ajoute aucun statut. Il rend deux capacités
disponibles — **la base sait héberger un vecteur, l'application sait en fabriquer** — et
s'arrête là.

## Objectif

Le contexte `knowledge` dispose d'un port pour obtenir des vecteurs, d'un adapter qui
interroge un Ollama local, et d'une base capable de stocker et d'indexer des colonnes
`vector`.

**Réussi si :** `docker compose up` sur un poste vierge rend une base pgvector migrée et un
Ollama portant `bge-m3`, sans une seule étape manuelle ; un appel au port rend autant de
vecteurs de 1024 dimensions qu'on lui a passé de textes, dans le même ordre ; un modèle qui
rendrait une autre dimension est refusé ; la suite de tests le prouve sans qu'aucun appel
réseau ne sorte de la machine.

## Attendus métier

Le Gherkin de RAG-5 porte sur le découpage : il appartient au second livrable. Les scénarios
ci-dessous viennent de RAG-2, retenus pour leur intention et réécrits pour ce projet.

```gherkin
Fonctionnalité: Accès au service de vectorisation

  Scénario: Vectorisation d'un lot de textes
    Étant donné un service d'embeddings disponible
    Quand je demande la vectorisation de 120 textes
    Alors je reçois 120 vecteurs dans le même ordre
    Et le service d'embeddings a été sollicité en plusieurs lots

  Scénario: Service momentanément indisponible
    Étant donné un service d'embeddings qui échoue
    Quand je demande une vectorisation
    Alors la demande est retentée trois fois
    Et l'échec final est remonté explicitement

  Scénario: Modèle rendant une dimension inattendue
    Étant donné un service configuré sur un modèle qui ne rend pas 1024 dimensions
    Quand je demande une vectorisation
    Alors elle est refusée en nommant la dimension reçue

  Scénario: La base héberge des vecteurs
    Étant donné une base de données vide
    Quand l'application démarre
    Alors l'extension de recherche vectorielle est disponible
```

## Décisions de conception

Chacune a été arbitrée avec le porteur du ticket avant l'écriture du plan. **Aucune ne
donne lieu à un ADR** : il a jugé qu'aucune n'était une décision d'architecture au sens de
`decisions.md`. Le raisonnement vit donc dans la javadoc des classes concernées et dans les
messages de commit — la première et la troisième contredisant RAG-2, c'est la javadoc de
`OllamaEmbeddingAdapter` qui en portera la trace.

### 1. Un client Ollama écrit à la main, pas Spring AI

`OllamaEmbeddingAdapter`, dans `knowledge/infrastructure/ai/`, tient dans une cinquantaine
de lignes : un `RestClient`, un `POST /api/embed` de corps `{model, input: [...]}`, une
réponse `{embeddings: [[...]]}`. Package-private, comme les autres adapters techniques.

Écarté : Spring AI 2.0, que RAG-2 imposait explicitement. Trois raisons, dans cet ordre.

**On n'en emploierait qu'une méthode.** Le `VectorStore` de Spring AI est précisément ce
qu'on ne veut pas : le schéma est à nous, nommé par la typologie du document, et Spring AI
imposerait le sien. Restent `ChatClient` et ses conseillers, pour RAG-9, qui reposera la
question — et où la bibliothèque mérite bien davantage son prix.

**Le BOM.** Spring AI 2.0 exige Boot 4.0, ce qui est notre cas, mais ses starters 2.0.0
tirent des dépendances alignées sur Boot **4.1.0**
(<https://github.com/spring-projects/spring-ai/issues/6465>). Le `CLAUDE.md` dit « ne pas
changer ces versions » ; arbitrer un conflit de BOM pour un `POST` de trois champs est un
mauvais échange.

**Le lotissement et les tentatives sont des règles à nous.** Les déléguer, c'est les
découvrir dans une propriété de configuration au lieu de les lire dans le code qui les
applique.

### 2. `bge-m3`, 1024 dimensions, par lots de 32

Le modèle que RAG-2 nommait déjà, et le bon pour ce produit : multilingue et solide en
français, là où `nomic-embed-text` est anglophone. Sa fenêtre de 8192 tokens rend un extrait
de 800 parfaitement confortable, et il s'évalue au cosinus — ce qui décidera l'index HNSW du
second livrable.

**Par lots, jamais un par un.** `/api/embed` prend un tableau `input` et rend un tableau
`embeddings` : un texte à la fois, c'est un aller-retour HTTP par extrait, et un PDF de
trente pages en fait une centaine. Trente-deux par lot : assez pour amortir la latence,
assez peu pour qu'un échec ne coûte pas tout le document et que la mémoire d'un Ollama CPU
ne s'en émeuve pas.

Écarté : les lots de 100 de RAG-6. Le chiffre y était posé sans justification, et il triple
le coût d'un échec pour un gain de latence qui n'a jamais été mesuré.

**Le lotissement vit dans l'adapter, pas dans l'appelant.** Le port reçoit une liste de
textes et rend une liste de vecteurs ; que le réseau existe est un détail d'infrastructure.

### 3. Aucun contrôle au démarrage : l'échec se voit au premier document

Écarté : le fail-fast que RAG-2 exigeait — « l'application refuse de démarrer en indiquant
quel modèle manque ».

Ce projet pratique pourtant le fail-fast partout : la table de routage des bus, la
couverture des extracteurs, le secret JWT sans valeur par défaut. **La différence est de
nature.** Ces trois-là sont des défauts de câblage : déterministes, locaux, vrais ou faux
une fois pour toutes au démarrage. La disponibilité d'un service d'inférence est une
condition réseau qui change dans le temps — un worker qui refuse de démarrer parce que le
conteneur tire encore 2,2 Go de modèle n'a pas le même sens qu'un worker mal câblé, et un
Ollama redémarré ne doit pas emporter le worker avec lui.

Le chemin d'échec existe déjà, et il est éprouvé : le document passe `FAILED` avec son motif,
depuis la seconde transaction du listener. Rien à construire.

**Le prix est réel et il est payé ici** : une URL fausse ou un nom de modèle mal orthographié
ne se voient qu'à la première ingestion. D'où `EmbeddingUnavailableException`, dont le
message est affichable tel quel et dit que c'est **le service de vectorisation** qui est
injoignable. Sans elle, une variable d'environnement mal saisie se lirait à l'écran comme un
document illisible, et on chercherait au mauvais endroit.

Écarté aussi : le contrôle non bloquant, journalisé en `WARN`. Un `WARN` se rate, et c'est
exactement l'argument que ce projet retient contre les défauts silencieux — il ne vaut pas
mieux que rien tout en coûtant du code.

### 4. L'image de la base passe à pgvector ; ce ticket n'installe que l'extension

`db` passe de `postgres:17-alpine` à `pgvector/pgvector:0.8.6-pg17` — version épinglée, pas
le `pg17` flottant : le `CLAUDE.md` pin déjà tout le reste. `TestcontainersConfiguration`
suit la même image, sans quoi la CI validerait un schéma que la production ne peut pas
porter.

**Le changement ne coûte aucune migration de données** : la vérification faite sur le
serveur Coolify ne trouve aucune ressource `second-brain`. ADR-0013 décrit une intention de
déploiement, pas une pile en fonctionnement. C'est un swap en développement et en CI.

`V9__enable_vector_extension.sql` ne fait qu'un `CREATE EXTENSION IF NOT EXISTS vector`.
**La table des extraits n'est pas ici**, bien que ce ticket décide sa dimension : une table
sans entité pendant tout un livrable est du poids mort, et ce projet fait naître les choses
quand la première classe les exige. Elle arrive avec `TextChunk`, dans le second livrable.

Conséquence assumée : **ce ticket ne vérifie pas l'aller-retour d'un `vector(1024)` en
base.** Il constate que l'extension est là ; l'aller-retour attend la table.

### 5. Le vecteur est un `float[]`, mappé par `hibernate-vector`

Hibernate 7 sait mapper pgvector nativement : `@JdbcTypeCode(SqlTypes.VECTOR)` et
`@Array(length = 1024)` sur un `float[]`, via le module `hibernate-vector`. On reste donc en
JPA avec `ddl-auto: validate`, et le mapping continue d'être vérifié au démarrage.

Écarté : le `JdbcTemplate` et ses `batchUpdate` que RAG-6 décrivait. Il datait d'un temps où
Hibernate ne savait pas faire ; l'employer aujourd'hui, ce serait sortir un agrégat du seul
mécanisme qui valide son schéma.

Le module s'ajoute au version catalog. S'il s'avère porté par le BOM Boot 4, il s'y déclare
sans version, comme les starters.

### 6. La dimension est une règle du domaine, pas une propriété de configuration

`EmbeddingPolicy.DIMENSIONS = 1024`, à la racine du domaine, aux côtés d'`ExtractionPolicy`.
`Embedding` est un objet-valeur qui valide sa dimension dans son constructeur : un Ollama
configuré par erreur sur un modèle à 768 dimensions se fait refuser par le type, à
l'endroit exact où le vecteur entre dans le domaine, et non trois couches plus loin par une
contrainte PostgreSQL au moment de l'écriture.

C'est le même arbitrage que la durée de vie du jeton d'accès : un exploitant ne doit pas
pouvoir désaligner par un fichier de configuration une valeur dont dépend la cohérence de
toute la base. Les vecteurs de deux modèles ne se comparent pas ; changer de modèle est une
migration et une réindexation, pas une variable d'environnement.

Sont en revanche des variables d'environnement, parce qu'elles décrivent *où* joindre le
service et non *ce que* le domaine attend : `OLLAMA_BASE_URL` et
`SECONDBRAIN_EMBEDDING_MODEL`. La première compte : un Ollama tourne déjà sur le serveur
Coolify, avec son volume de modèles, et la production le visera plutôt que d'en embarquer un
second.

### 7. Ollama est un service de la pile de développement, tiré au premier démarrage

Un service `ollama` dans `compose.yaml`, un volume nommé pour ses modèles, et un conteneur
one-shot qui tire `bge-m3` au premier démarrage. Jamais à la main : c'est l'exigence de
RAG-1, et elle vaut toujours.

Le worker ne dépend pas de sa santé pour démarrer — c'est la décision 3. Il en dépend pour
travailler, et un premier document déposé pendant le téléchargement du modèle échouera avec
son motif. C'est acceptable et c'est visible ; ça ne l'aurait pas été si l'échec avait été
silencieux.

## Ce qui reste hors périmètre

- **Le découpage en extraits, la table `knowledge_text_chunks`, l'index HNSW, le statut
  `READY`** : second livrable, `2026-08-31-decoupage-extraits-design.md`.
- **La recherche vectorielle** : RAG-8. Ce ticket ne pose aucune requête de similarité.
- **`LlmPort` et la génération** : RAG-2 les demandait dans le même souffle, mais rien ne
  les consomme avant RAG-9. Le port naîtra avec son premier appelant.
- **Le déploiement** : `second-brain` n'est pas déployé. Le jour venu, la production visera
  l'Ollama déjà en place ; c'est tout ce que ce ticket lui prépare, par une variable
  d'environnement.
- **Le choix d'un autre modèle, la comparaison de modèles, la mesure de qualité** : RAG-14.

## Pour aller plus loin

- Second livrable : `2026-08-31-decoupage-extraits-design.md`
- Spec du socle événementiel : `2026-08-25-evenements-metier-rabbitmq-design.md`
- Spec de l'extraction : `2026-08-26-extraction-texte-documents-design.md`
- ADR-0028 (l'échec hors de la transaction annulée), ADR-0030 (les tables portent leur
  typologie), ADR-0013 (le déploiement vit dans Coolify)
