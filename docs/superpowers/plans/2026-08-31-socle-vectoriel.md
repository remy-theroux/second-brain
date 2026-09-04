# Le socle vectoriel — plan d'implémentation

> **Pour les agents d'exécution :** SOUS-COMPÉTENCE REQUISE — utiliser
> `superpowers:subagent-driven-development` (recommandé) ou `superpowers:executing-plans`
> pour dérouler ce plan tâche par tâche. Les étapes sont des cases à cocher (`- [ ]`).

**But :** le contexte `knowledge` dispose d'un port pour obtenir des vecteurs, d'un adapter
qui interroge un Ollama local, et d'une base capable d'héberger des colonnes `vector`.

**Architecture :** l'image de la base passe à `pgvector/pgvector`, et une migration active
l'extension. Le domaine gagne `EmbeddingPolicy` (la dimension est une règle, pas une
propriété de configuration), l'objet-valeur `Embedding` qui la fait respecter, le port
`EmbeddingPort` et le refus `EmbeddingUnavailableException`. Un unique adapter,
`OllamaEmbeddingAdapter`, interroge `POST /api/embed` par lots de 32 avec trois tentatives.
La pile de développement gagne un service `ollama` et un conteneur one-shot qui tire
`bge-m3`.

**Ce plan ne découpe rien et ne vectorise aucun document.** Il rend deux capacités
disponibles et s'arrête là. Le découpage est le second livrable.

**Stack :** Java 25 · Spring Boot 4.0.7 · PostgreSQL 17 + pgvector 0.8.6 · Ollama · `bge-m3`
(1024 dimensions) · JUnit 5 + AssertJ + Testcontainers + `MockRestServiceServer`.

**Spec :** `docs/superpowers/specs/2026-08-31-socle-vectoriel-design.md` — le plan argumente
depuis elle ; la lire avant d'exécuter.

## Contraintes globales

Elles s'ajoutent implicitement aux exigences de **chaque** tâche.

- **Tout passe par Docker.** Aucun JDK, aucun Gradle, aucun Node sur l'hôte. Définir la
  fonction `gtest` de `CLAUDE.md` une fois par session, avant la première commande.
- **`gtest` et `docker compose up` ne cohabitent pas** : `docker compose down` avant de
  lancer la suite.
- **Français** pour les commentaires, la Javadoc, les messages d'exception, les libellés et
  les noms de méthodes de test. **Anglais** pour les noms de classes, de méthodes de
  production et de packages.
- **`make format-back` avant chaque commit.** Le style est décidé par palantir-java-format ;
  ne pas se battre avec lui. La Javadoc et les commentaires ne sont jamais reformatés : leur
  mise en forme reste à la charge du rédacteur, et c'est elle qui porte le raisonnement.
- **Toute exception métier hérite de `RuntimeException`** — une exception checked ne
  déclenche pas le rollback promis par le `CommandBus`.
- **Le domaine n'importe jamais `org.springframework.*` ni `…infrastructure.*`.** Aucune
  classe créée par ce plan dans `domain/` n'a d'exception à demander : ni JPA, ni Spring.
- **Flyway est maître du schéma**, `ddl-auto: validate`. Ne jamais modifier une migration
  déjà appliquée. La seule migration de ce plan est **`V9`**.
- **Pattern d'intégration imposé** : `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`.
  Ne pas introduire `@DataJpaTest`.
- **Aucun ADR n'est dû par ce plan.** Arbitré avec le porteur du ticket : aucune de ces
  décisions n'en est une au sens de `.claude/rules/decisions.md`. Le raisonnement va dans la
  Javadoc des classes et les messages de commit. **Ne pas en écrire un de sa propre
  initiative** — la règle est explicite.
- **Un commit par tâche**, tests verts, préfixe conventionnel minuscule (`feat:`, `conf:`,
  `test:`, `docs:`).

## Structure des fichiers

```
src/main/java/xyz/sterenn/secondbrain/knowledge/
├── domain/
│   ├── EmbeddingPolicy.java                      CRÉÉ  T2  la dimension, règle pure sans dépendance
│   ├── valueobject/
│   │   └── Embedding.java                        CRÉÉ  T2  un vecteur valide, immuable
│   ├── port/
│   │   └── EmbeddingPort.java                    CRÉÉ  T3  List<String> → List<Embedding>
│   └── exception/
│       └── EmbeddingUnavailableException.java    CRÉÉ  T3  refus affichable tel quel
└── infrastructure/
    └── ai/
        ├── OllamaEmbeddingAdapter.java           CRÉÉ  T3  package-private, RestClient, lots de 32
        ├── OllamaEmbeddingRequest.java           CRÉÉ  T3  record du corps envoyé
        └── OllamaEmbeddingResponse.java          CRÉÉ  T3  record du corps reçu

src/main/resources/
├── db/migration/V9__enable_vector_extension.sql  CRÉÉ  T1  CREATE EXTENSION vector
└── application.yml                               MODIF T3  secondbrain.embedding.*

src/test/java/xyz/sterenn/secondbrain/
├── TestcontainersConfiguration.java              MODIF T1  image pgvector
├── VectorExtensionTest.java                      CRÉÉ  T1  l'extension est là et le type marche
└── knowledge/
    ├── domain/valueobject/EmbeddingTest.java     CRÉÉ  T2  pur, sans Spring
    └── infrastructure/ai/
        └── OllamaEmbeddingAdapterTest.java       CRÉÉ  T3  MockRestServiceServer, aucun réseau

compose.yaml                                      MODIF T1 (image db), T4 (ollama, ollama-pull, env)
.env.example                                      MODIF T4  SECONDBRAIN_EMBEDDING_MODEL
CLAUDE.md                                         MODIF T4  la pile gagne un service
README.md                                         MODIF T4  si la section « démarrage » liste les services
```

**Ce que ce plan ne crée pas, et c'est voulu :** aucune table, aucune entité, aucune
dépendance `hibernate-vector`. Ils arrivent avec `TextChunk`, dans le second livrable. Une
table sans entité et une dépendance sans appelant sont du poids mort.

---

## Tâche 1 : La base sait héberger des vecteurs

L'image `postgres:17-alpine` ne fournit pas l'extension `vector`, et une extension fournie
n'est pas une extension activée. Deux gestes, donc : changer d'image, et migrer.

**Fichiers :**
- Modifier : `compose.yaml` (service `db`, ligne `image:`)
- Modifier : `src/test/java/xyz/sterenn/secondbrain/TestcontainersConfiguration.java`
- Créer : `src/main/resources/db/migration/V9__enable_vector_extension.sql`
- Test : `src/test/java/xyz/sterenn/secondbrain/VectorExtensionTest.java`

**Interfaces :**
- Consomme : rien.
- Produit : le type SQL `vector` et ses opérateurs de distance, disponibles pour toute
  migration ultérieure. Aucune signature Java.

> **Avertissement à lire avant l'étape 1.** `postgres:17-alpine` est bâtie sur musl,
> `pgvector/pgvector:0.8.6-pg17` sur Debian, donc glibc. Les deux servent bien PostgreSQL 17,
> mais **les collations diffèrent** : un volume `db-data` initialisé par l'image Alpine et
> monté sous l'image Debian peut rendre des index texte incohérents. En développement, le
> volume se jette :
>
> ```bash
> docker compose down -v
> ```
>
> C'est sans conséquence — la base de développement se reconstruit par Flyway au démarrage
> suivant, et aucune production n'existe (spec, décision 4).

- [ ] **Étape 1 : Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/VectorExtensionTest.java` :

```java
package xyz.sterenn.secondbrain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * La base sait-elle héberger des vecteurs ?
 *
 * <p>Deux questions distinctes, et il faut les deux. L'extension peut être <em>fournie</em>
 * par l'image sans être <em>activée</em> sur la base : c'est le rôle de la migration. Et une
 * extension activée sans opérateur utilisable ne servirait à rien — d'où le second test, qui
 * calcule une vraie distance plutôt que de lire une ligne de catalogue.
 *
 * <p>Ce test vit à la racine et non dans {@code knowledge} : c'est une capacité de la base,
 * pas une règle d'un contexte borné. Il rejoint {@code SecondBrainApplicationTests}, qui
 * vérifie déjà que Flyway migre la base Testcontainers.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class VectorExtensionTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void la_base_active_l_extension_de_recherche_vectorielle() {
        Optional<String> version = jdbcClient
                .sql("SELECT extversion FROM pg_extension WHERE extname = 'vector'")
                .query(String.class)
                .optional();

        assertThat(version).isPresent();
    }

    @Test
    void la_base_calcule_la_distance_cosinus_entre_deux_vecteurs() {
        // Deux vecteurs orthogonaux : leur distance cosinus vaut exactement 1.
        Double distance = jdbcClient
                .sql("SELECT '[1,0,0]'::vector <=> '[0,1,0]'::vector")
                .query(Double.class)
                .single();

        assertThat(distance).isEqualTo(1.0d);
    }
}
```

- [ ] **Étape 2 : Lancer le test, vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.VectorExtensionTest"
```

Attendu : ÉCHEC. Le premier test rend un `Optional` vide ; le second lève une
`BadSqlGrammarException` sur `type "vector" does not exist`.

- [ ] **Étape 3 : Changer l'image dans `TestcontainersConfiguration`**

Remplacer la méthode `postgresContainer()` et compléter la Javadoc de la classe :

```java
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        // pgvector/pgvector et non postgres : l'extension `vector` doit être FOURNIE par
        // l'image pour que la migration V9 puisse l'activer. Version épinglée, pas le tag
        // `pg17` flottant — la CI et le poste doivent servir la même chose.
        return new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:0.8.6-pg17")
                // L'image dérive de `postgres` mais ne porte pas son nom : sans cette
                // ligne, Testcontainers refuse de la traiter comme une PostgreSQL.
                .asCompatibleSubstituteFor("postgres"));
    }
```

- [ ] **Étape 4 : Changer l'image dans `compose.yaml`**

Dans le service `db`, remplacer la ligne `image:` :

```yaml
  db:
    # pgvector/pgvector et non postgres:17-alpine : l'extension `vector` doit être fournie
    # par l'image, la migration V9 se charge de l'activer. Version épinglée comme le reste
    # de la pile. Debian et non Alpine : c'est ce que publie pgvector — un volume db-data
    # initialisé par l'ancienne image se jette (`docker compose down -v`), musl et glibc ne
    # trient pas le texte de la même façon.
    image: pgvector/pgvector:0.8.6-pg17
```

- [ ] **Étape 5 : Écrire la migration**

Créer `src/main/resources/db/migration/V9__enable_vector_extension.sql` :

```sql
-- Le type `vector`, ses opérateurs de distance et ses index.
--
-- L'image pgvector FOURNIT l'extension ; elle ne l'active sur aucune base. Sans ce
-- CREATE EXTENSION, `'[1,0,0]'::vector` échoue sur « type "vector" does not exist ».
--
-- Aucune table ici : celle des extraits arrive avec l'entité qui la mappe, dans le
-- livrable suivant. Une table sans entité est du poids mort, et `ddl-auto: validate`
-- n'aurait rien à valider.
CREATE EXTENSION IF NOT EXISTS vector;
```

- [ ] **Étape 6 : Lancer le test, vérifier qu'il passe**

```bash
docker compose down -v
gtest test --tests "xyz.sterenn.secondbrain.VectorExtensionTest"
```

Attendu : SUCCÈS, deux tests verts. Le premier téléchargement de l'image pgvector prend une
minute ou deux.

- [ ] **Étape 7 : Lancer toute la suite**

```bash
gtest test
```

Attendu : SUCCÈS. Changer l'image de la base touche **tous** les tests d'intégration ; c'est
le seul moment où on le vérifie à moindre coût.

- [ ] **Étape 8 : Formater et committer**

```bash
make format-back
git add compose.yaml src/main/resources/db/migration/V9__enable_vector_extension.sql \
        src/test/java/xyz/sterenn/secondbrain/TestcontainersConfiguration.java \
        src/test/java/xyz/sterenn/secondbrain/VectorExtensionTest.java
git commit -m "conf: la base héberge des vecteurs, image pgvector et extension activée"
```

---

## Tâche 2 : La dimension est une règle du domaine

Un vecteur de 768 dimensions rangé dans une colonne `vector(1024)` échouerait à l'écriture,
loin de l'endroit où l'erreur a été commise. `Embedding` le refuse à l'entrée du domaine.

**Fichiers :**
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/EmbeddingPolicy.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/Embedding.java`
- Test : `src/test/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/EmbeddingTest.java`

**Interfaces :**
- Consomme : rien de la tâche 1.
- Produit :
  - `EmbeddingPolicy.DIMENSIONS = 1024`
  - `Embedding.of(float[] values) → Embedding` — lève `IllegalArgumentException` si la
    longueur diffère de `EmbeddingPolicy.DIMENSIONS`, `NullPointerException` sur `null`
  - `Embedding.values() → float[]` — **une copie**, jamais le tableau interne
  - `equals` / `hashCode` par le contenu du tableau ; `toString` sans les valeurs

- [ ] **Étape 1 : Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/EmbeddingTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy;

class EmbeddingTest {

    @Test
    void accepte_un_vecteur_de_la_dimension_attendue() {
        Embedding vecteur = Embedding.of(unVecteur(EmbeddingPolicy.DIMENSIONS));

        assertThat(vecteur.values()).hasSize(EmbeddingPolicy.DIMENSIONS);
    }

    @Test
    void refuse_un_vecteur_trop_court_en_nommant_la_dimension_recue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Embedding.of(unVecteur(768)))
                .withMessageContaining("768")
                .withMessageContaining(String.valueOf(EmbeddingPolicy.DIMENSIONS));
    }

    @Test
    void refuse_un_vecteur_absent() {
        assertThatNullPointerException().isThrownBy(() -> Embedding.of(null));
    }

    @Test
    void deux_vecteurs_de_meme_contenu_sont_egaux() {
        assertThat(Embedding.of(unVecteur(EmbeddingPolicy.DIMENSIONS)))
                .isEqualTo(Embedding.of(unVecteur(EmbeddingPolicy.DIMENSIONS)))
                .hasSameHashCodeAs(Embedding.of(unVecteur(EmbeddingPolicy.DIMENSIONS)));
    }

    @Test
    void ne_laisse_pas_modifier_le_tableau_qu_il_a_recu() {
        float[] source = unVecteur(EmbeddingPolicy.DIMENSIONS);
        Embedding vecteur = Embedding.of(source);

        source[0] = 42f;

        assertThat(vecteur.values()[0]).isEqualTo(0.5f);
    }

    @Test
    void ne_laisse_pas_modifier_le_tableau_qu_il_rend() {
        Embedding vecteur = Embedding.of(unVecteur(EmbeddingPolicy.DIMENSIONS));

        vecteur.values()[0] = 42f;

        assertThat(vecteur.values()[0]).isEqualTo(0.5f);
    }

    @Test
    void ne_montre_jamais_ses_valeurs_quand_on_l_affiche() {
        assertThat(Embedding.of(unVecteur(EmbeddingPolicy.DIMENSIONS)).toString())
                .contains(String.valueOf(EmbeddingPolicy.DIMENSIONS))
                .doesNotContain("0.5");
    }

    /** Un vecteur constant : ce qui est testé ici, c'est la forme, jamais le contenu. */
    private static float[] unVecteur(int dimensions) {
        float[] valeurs = new float[dimensions];
        Arrays.fill(valeurs, 0.5f);
        return valeurs;
    }
}
```

- [ ] **Étape 2 : Lancer le test, vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.valueobject.EmbeddingTest"
```

Attendu : ÉCHEC de compilation, `cannot find symbol: class Embedding`.

- [ ] **Étape 3 : Écrire `EmbeddingPolicy`**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/EmbeddingPolicy.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain;

/**
 * Ce que le domaine tient pour un vecteur.
 *
 * <p>Règle métier pure, statique, sans dépendance — à la racine du domaine, à côté
 * d'{@link ExtractionPolicy} : elle se teste sans Spring.
 *
 * <p><strong>La dimension n'est pas une propriété de configuration, et c'est délibéré.</strong>
 * Elle est le contrat entre le modèle qui produit les vecteurs, la colonne qui les range et
 * l'index qui les compare : la désaligner par un fichier de configuration rendrait toute la
 * base incohérente sans qu'aucune erreur ne le dise à temps. Les vecteurs de deux modèles ne
 * se comparent de toute façon pas — changer de modèle est une migration et une réindexation,
 * pas une variable d'environnement.
 *
 * <p>Même arbitrage que la durée de vie du jeton d'accès, qui vit dans
 * {@code AccessTokenPolicy} et non dans {@code application.yml}.
 */
public final class EmbeddingPolicy {

    /** Ce que produit {@code bge-m3}, et ce qu'attend la colonne {@code vector(1024)}. */
    public static final int DIMENSIONS = 1024;

    private EmbeddingPolicy() {
        // règle métier, pas un objet
    }
}
```

- [ ] **Étape 4 : Écrire `Embedding`**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/Embedding.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Arrays;
import java.util.Objects;
import xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy;

/**
 * Un vecteur produit par le service de vectorisation.
 *
 * <p>Objet-valeur : il valide dans sa fabrique, il est immuable, et deux vecteurs de même
 * contenu sont égaux. <strong>Il est impossible d'en construire un dont la dimension ne soit
 * pas celle du modèle</strong> — une configuration pointée sur un autre modèle se fait donc
 * refuser à l'endroit exact où le vecteur entre dans le domaine, et non trois couches plus
 * loin par une contrainte PostgreSQL au moment de l'écriture.
 *
 * <p>Une classe et non un {@code record} : le champ est un {@code float[]}, et l'{@code
 * equals} qu'un record engendrerait comparerait les <em>références</em> de tableau. Deux
 * vecteurs identiques seraient différents.
 *
 * <p>Le tableau est copié à l'entrée comme à la sortie. Un tableau est mutable ; sans ces
 * deux copies, l'appelant garderait la main sur l'état d'un objet-valeur.
 *
 * <p>Le refus est une {@link IllegalArgumentException} et non un refus métier : ce n'est
 * jamais l'utilisateur qui a mal fait, c'est la configuration ou le service. C'est l'adapter
 * qui la traduit en un message affichable, comme un adapter de persistance traduit une
 * violation de contrainte.
 */
public final class Embedding {

    private final float[] values;

    private Embedding(float[] values) {
        this.values = values;
    }

    /**
     * @throws IllegalArgumentException si la dimension n'est pas celle
     *     d'{@link EmbeddingPolicy#DIMENSIONS}
     */
    public static Embedding of(float[] values) {
        Objects.requireNonNull(values, "Le vecteur est obligatoire");
        if (values.length != EmbeddingPolicy.DIMENSIONS) {
            throw new IllegalArgumentException("Un vecteur porte " + EmbeddingPolicy.DIMENSIONS
                    + " dimensions, reçu : " + values.length);
        }
        return new Embedding(values.clone());
    }

    /** Une copie : personne ne modifie l'état d'un objet-valeur. */
    public float[] values() {
        return values.clone();
    }

    @Override
    public boolean equals(Object autre) {
        return autre instanceof Embedding vecteur && Arrays.equals(values, vecteur.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    /** Volontairement sans les valeurs : mille flottants dans un journal ne servent personne. */
    @Override
    public String toString() {
        return "Embedding[" + values.length + " dimensions]";
    }
}
```

- [ ] **Étape 5 : Lancer le test, vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.valueobject.EmbeddingTest"
```

Attendu : SUCCÈS, sept tests verts. Aucun contexte Spring n'a démarré.

- [ ] **Étape 6 : Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/knowledge/domain/EmbeddingPolicy.java \
        src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/Embedding.java \
        src/test/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/EmbeddingTest.java
git commit -m "feat: un vecteur porte la dimension du modèle, et le domaine la fait respecter"
```

---

## Tâche 3 : Le port de vectorisation et son adapter Ollama

Le cœur du ticket. Un port, un refus, un adapter d'une cinquantaine de lignes, et un test qui
ne sort jamais de la machine.

**Fichiers :**
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/port/EmbeddingPort.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/exception/EmbeddingUnavailableException.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/ai/OllamaEmbeddingRequest.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/ai/OllamaEmbeddingResponse.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/ai/OllamaEmbeddingAdapter.java`
- Modifier : `src/main/resources/application.yml` (bloc `secondbrain:`)
- Test : `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/ai/OllamaEmbeddingAdapterTest.java`

**Interfaces :**
- Consomme : `Embedding.of(float[])`, `EmbeddingPolicy.DIMENSIONS` (tâche 2).
- Produit :
  - `EmbeddingPort.embed(List<String> texts) → List<Embedding>` — même ordre, même taille ;
    rend `List.of()` sur une entrée vide ; lève `EmbeddingUnavailableException`
  - `EmbeddingUnavailableException(String message)` et `(String message, Throwable cause)`,
    fille de `RuntimeException`
  - `OllamaEmbeddingAdapter.BATCH_SIZE = 32`, `OllamaEmbeddingAdapter.MAX_ATTEMPTS = 3`
    (package-private, lus par le test)
  - Propriétés `secondbrain.embedding.base-url` et `secondbrain.embedding.model`

> **`EmbeddingUnavailableException` hérite directement de `RuntimeException` dans ce
> livrable.** Le second lui donnera pour parent `DocumentProcessingException`, en même temps
> qu'il renommera `MarkDocumentExtractionFailed`. Poser ce parent maintenant créerait une
> hiérarchie dont aucune ligne ne se sert.

- [ ] **Étape 1 : Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/ai/OllamaEmbeddingAdapterTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.exception.EmbeddingUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;

/**
 * L'adapter, contre un serveur bouché. Aucun appel ne sort de la machine : c'est la règle
 * du projet — « les deux adapters se testent avec des doublures, sans appel réseau ».
 *
 * <p>Pas de {@code @SpringBootTest} : l'adapter se construit à la main avec un
 * {@code RestClient.Builder} auquel {@link MockRestServiceServer} s'est branché. Démarrer un
 * contexte n'apprendrait rien de plus et coûterait quelques secondes à chaque exécution.
 */
class OllamaEmbeddingAdapterTest {

    private static final String BASE_URL = "http://ollama-de-test:11434";
    private static final String URL_EMBED = BASE_URL + "/api/embed";
    private static final String MODELE = "bge-m3";

    private MockRestServiceServer serveur;
    private OllamaEmbeddingAdapter adapter;

    @BeforeEach
    void brancher_le_serveur_bouche() {
        RestClient.Builder builder = RestClient.builder();
        serveur = MockRestServiceServer.bindTo(builder).build();
        adapter = new OllamaEmbeddingAdapter(builder, BASE_URL, MODELE);
    }

    @Test
    void rend_un_vecteur_par_texte_dans_le_meme_ordre() {
        serveur.expect(requestTo(URL_EMBED))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value(MODELE))
                .andExpect(jsonPath("$.input[0]").value("premier"))
                .andExpect(jsonPath("$.input[1]").value("second"))
                .andRespond(withSuccess(corpsDeReponse(0.1f, 0.2f), MediaType.APPLICATION_JSON));

        List<Embedding> vecteurs = adapter.embed(List.of("premier", "second"));

        assertThat(vecteurs).hasSize(2);
        assertThat(vecteurs.get(0).values()[0]).isEqualTo(0.1f);
        assertThat(vecteurs.get(1).values()[0]).isEqualTo(0.2f);
        serveur.verify();
    }

    @Test
    void decoupe_en_lots_et_recolle_les_resultats_dans_l_ordre() {
        // 120 textes, lots de 32 : 32 + 32 + 32 + 24, donc quatre appels.
        List<String> textes =
                IntStream.range(0, 120).mapToObj(i -> "texte " + i).toList();

        // Un lot après l'autre, chacun répondant des valeurs qui identifient son rang.
        // Quatre attentes successives : MockRestServiceServer les consomme dans l'ordre, et
        // `verify()` échoue s'il en reste une, donc « exactement quatre appels » est vérifié.
        serveur.expect(requestTo(URL_EMBED)).andRespond(reponsePour(32, 0f));
        serveur.expect(requestTo(URL_EMBED)).andRespond(reponsePour(32, 1f));
        serveur.expect(requestTo(URL_EMBED)).andRespond(reponsePour(32, 2f));
        serveur.expect(requestTo(URL_EMBED)).andRespond(reponsePour(24, 3f));

        List<Embedding> vecteurs = adapter.embed(textes);

        assertThat(vecteurs).hasSize(120);
        assertThat(vecteurs.get(0).values()[0]).isEqualTo(0f);
        assertThat(vecteurs.get(31).values()[0]).isEqualTo(0f);
        assertThat(vecteurs.get(32).values()[0]).isEqualTo(1f);
        assertThat(vecteurs.get(119).values()[0]).isEqualTo(3f);
        serveur.verify();
    }

    @Test
    void n_appelle_pas_le_service_pour_une_liste_vide() {
        assertThat(adapter.embed(List.of())).isEmpty();

        serveur.verify(); // aucune attente posée : un appel ferait échouer la vérification
    }

    @Test
    void retente_trois_fois_puis_remonte_un_refus_affichable() {
        serveur.expect(ExpectedCount.times(OllamaEmbeddingAdapter.MAX_ATTEMPTS), requestTo(URL_EMBED))
                .andRespond(withServerError());

        assertThatExceptionOfType(EmbeddingUnavailableException.class)
                .isThrownBy(() -> adapter.embed(List.of("un texte")))
                .withMessageContaining("vectorisation");

        serveur.verify();
    }

    @Test
    void refuse_un_vecteur_dont_la_dimension_n_est_pas_celle_du_modele() {
        String corps = "{\"embeddings\":[[" + "0.5,".repeat(767) + "0.5]]}";
        serveur.expect(requestTo(URL_EMBED)).andRespond(withSuccess(corps, MediaType.APPLICATION_JSON));

        assertThatExceptionOfType(EmbeddingUnavailableException.class)
                .isThrownBy(() -> adapter.embed(List.of("un texte")))
                .withMessageContaining("768")
                .withMessageContaining(String.valueOf(EmbeddingPolicy.DIMENSIONS));
    }

    @Test
    void refuse_une_reponse_qui_ne_rend_pas_autant_de_vecteurs_que_de_textes() {
        serveur.expect(requestTo(URL_EMBED))
                .andRespond(withSuccess(corpsDeReponse(0.1f), MediaType.APPLICATION_JSON));

        assertThatExceptionOfType(EmbeddingUnavailableException.class)
                .isThrownBy(() -> adapter.embed(List.of("premier", "second")))
                .withMessageContaining("2");
    }

    /** Un corps JSON portant un vecteur de la bonne dimension par valeur donnée. */
    private static String corpsDeReponse(float... premieresValeurs) {
        StringBuilder corps = new StringBuilder("{\"embeddings\":[");
        for (int i = 0; i < premieresValeurs.length; i++) {
            corps.append(i == 0 ? "" : ",").append(unVecteurJson(premieresValeurs[i]));
        }
        return corps.append("]}").toString();
    }

    /** Une réponse de {@code combien} vecteurs portant tous la même première valeur. */
    private static ResponseCreator reponsePour(int combien, float valeur) {
        StringBuilder corps = new StringBuilder("{\"embeddings\":[");
        for (int i = 0; i < combien; i++) {
            corps.append(i == 0 ? "" : ",").append(unVecteurJson(valeur));
        }
        return withSuccess(corps.append("]}").toString(), MediaType.APPLICATION_JSON);
    }

    /** Un vecteur complet : la première valeur identifie le lot, le reste est du remplissage. */
    private static String unVecteurJson(float premiereValeur) {
        StringBuilder vecteur = new StringBuilder("[").append(premiereValeur);
        for (int i = 1; i < EmbeddingPolicy.DIMENSIONS; i++) {
            vecteur.append(",0.0");
        }
        return vecteur.append("]").toString();
    }
}
```

- [ ] **Étape 2 : Lancer le test, vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.ai.OllamaEmbeddingAdapterTest"
```

Attendu : ÉCHEC de compilation, `cannot find symbol: class OllamaEmbeddingAdapter`.

- [ ] **Étape 3 : Écrire le refus**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/exception/EmbeddingUnavailableException.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * Le service de vectorisation n'a pas rendu ce qu'on lui demandait.
 *
 * <p>Son message est <strong>affichable tel quel</strong>, et il nomme la vectorisation. Ce
 * n'est pas de la coquetterie : il n'y a aucun contrôle au démarrage sur la disponibilité du
 * service, donc une URL fausse ou un nom de modèle mal orthographié ne se voient qu'au
 * premier document traité. Sans un message qui désigne le bon coupable, l'utilisateur lirait
 * un motif générique et chercherait du côté de son fichier.
 *
 * <p>Elle couvre trois pannes, parce qu'elles appellent le même geste — regarder la
 * configuration et le service, pas le document : le service injoignable ou en erreur après
 * trois tentatives, une réponse qui ne rend pas autant de vecteurs que de textes, et un
 * vecteur d'une dimension étrangère au modèle attendu.
 *
 * <p>{@code RuntimeException} et non checked : c'est ce qui déclenche le rollback promis par
 * le {@code CommandBus}. Elle prendra pour parent {@code DocumentProcessingException} dans le
 * livrable suivant, quand un second consommateur en aura besoin.
 */
public class EmbeddingUnavailableException extends RuntimeException {

    public EmbeddingUnavailableException(String message) {
        super(message);
    }

    public EmbeddingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Étape 4 : Écrire le port**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/port/EmbeddingPort.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.exception.EmbeddingUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;

/**
 * Port sortant vers le service qui transforme du texte en vecteurs.
 *
 * <p>Le domaine ignore qu'il y a un réseau, un modèle et des lots. Il demande des vecteurs
 * pour des textes, et il exige deux choses : autant de vecteurs que de textes, et
 * <strong>dans le même ordre</strong>. Sans cette garantie, l'appelant ne pourrait plus
 * rattacher un vecteur à l'extrait dont il provient — c'est tout le contrat.
 *
 * <p>Le lotissement appartient à l'adapter : c'est une propriété du transport, pas une règle
 * métier. L'appelant passe sa liste entière.
 */
public interface EmbeddingPort {

    /**
     * Vectorise les textes, dans l'ordre.
     *
     * @return autant de vecteurs que de textes ; une liste vide pour une entrée vide
     * @throws EmbeddingUnavailableException si le service ne répond pas, ou répond quelque
     *     chose qu'on ne peut pas rattacher aux textes demandés
     */
    List<Embedding> embed(List<String> texts);
}
```

- [ ] **Étape 5 : Écrire les deux records du fil**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/ai/OllamaEmbeddingRequest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import java.util.List;

/**
 * Le corps de {@code POST /api/embed}. Package-private : la forme du fil ne regarde que
 * l'adapter.
 *
 * <p>{@code input} et non {@code prompt} : c'est la route lotissable d'Ollama.
 * {@code /api/embeddings}, au singulier, est l'ancienne, qui ne prend qu'un texte.
 */
record OllamaEmbeddingRequest(String model, List<String> input) {}
```

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/ai/OllamaEmbeddingResponse.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import java.util.List;

/**
 * Ce qu'Ollama rend : un vecteur par texte, dans l'ordre d'entrée.
 *
 * <p>Les autres champs de la réponse ({@code model}, les durées) ne sont pas déclarés :
 * Jackson ignore ce qu'il ne sait pas placer, et un champ déclaré est un champ qu'on
 * s'engage à maintenir.
 */
record OllamaEmbeddingResponse(List<float[]> embeddings) {}
```

- [ ] **Étape 6 : Écrire l'adapter**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/ai/OllamaEmbeddingAdapter.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.exception.EmbeddingUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.EmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;

/**
 * Adapter Ollama du port {@link EmbeddingPort}. Un {@code POST /api/embed}, par lots, avec
 * trois tentatives.
 *
 * <p><strong>Écrit à la main plutôt que Spring AI</strong>, et c'est un écart assumé à RAG-2,
 * qui imposait ses starters. Trois raisons. On n'emploierait qu'une méthode de la
 * bibliothèque : son {@code VectorStore} imposerait son propre schéma, alors que le nôtre est
 * nommé par la typologie du document. Ses starters 2.0.0 tirent des dépendances alignées sur
 * Spring Boot 4.1 quand ce projet en épingle 4.0.7. Et le lotissement comme les tentatives
 * sont des règles à nous : déléguées, elles se liraient dans une propriété de configuration
 * au lieu du code qui les applique. RAG-9 reposera la question pour la génération, où la
 * bibliothèque mérite bien davantage son prix.
 *
 * <p><strong>Aucun contrôle au démarrage</strong>, second écart à RAG-2. Ce projet pratique
 * pourtant le fail-fast partout — table de routage des bus, couverture des extracteurs,
 * secret JWT sans défaut. La différence est de nature : ces trois-là sont des défauts de
 * <em>câblage</em>, déterministes et vrais une fois pour toutes, là où la disponibilité d'un
 * service d'inférence est une condition réseau qui change dans le temps. Un worker qui
 * refuserait de démarrer parce que le conteneur tire encore 2,2 Go de modèle n'aurait pas le
 * même sens qu'un worker mal câblé. Le prix est payé par le message
 * d'{@link EmbeddingUnavailableException}, qui nomme la vectorisation.
 *
 * <p>Package-private : rien au-dehors ne doit dépendre d'autre chose que du port.
 */
@Component
class OllamaEmbeddingAdapter implements EmbeddingPort {

    /**
     * Assez pour amortir la latence d'un aller-retour, assez peu pour qu'un échec ne coûte
     * pas tout le document et que la mémoire d'un Ollama sur CPU ne s'en émeuve pas. RAG-6
     * disait 100 ; le chiffre y était posé sans justification et triple le coût d'un échec.
     */
    static final int BATCH_SIZE = 32;

    /** Utile au démarrage, quand Ollama charge encore le modèle en mémoire. */
    static final int MAX_ATTEMPTS = 3;

    /** Court : on attend un modèle qui se charge, pas un service qui se répare. */
    private static final long RETRY_BACKOFF_MILLIS = 200L;

    private final RestClient restClient;
    private final String model;

    OllamaEmbeddingAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${secondbrain.embedding.base-url}") String baseUrl,
            @Value("${secondbrain.embedding.model}") String model) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.model = model;
    }

    @Override
    public List<Embedding> embed(List<String> texts) {
        Objects.requireNonNull(texts, "La liste des textes à vectoriser est obligatoire");
        List<Embedding> vecteurs = new ArrayList<>(texts.size());
        for (int debut = 0; debut < texts.size(); debut += BATCH_SIZE) {
            vecteurs.addAll(embedBatch(texts.subList(debut, Math.min(debut + BATCH_SIZE, texts.size()))));
        }
        return List.copyOf(vecteurs);
    }

    /**
     * Un lot, et la traduction de tout ce qui peut mal tourner en un refus affichable.
     *
     * <p>C'est la règle des adapters de ce projet : aucune exception technique ne remonte à
     * l'application ni au domaine. L'{@link IllegalArgumentException} d'{@link Embedding} est
     * rattrapée ici pour la même raison qu'un adapter de persistance rattrape une violation
     * de contrainte — l'appelant n'a que faire de savoir <em>où</em> la dimension a été
     * vérifiée.
     */
    private List<Embedding> embedBatch(List<String> lot) {
        OllamaEmbeddingResponse reponse = appelerAvecTentatives(lot);
        if (reponse == null || reponse.embeddings() == null || reponse.embeddings().size() != lot.size()) {
            int recus = reponse == null || reponse.embeddings() == null
                    ? 0
                    : reponse.embeddings().size();
            throw new EmbeddingUnavailableException("Le service de vectorisation a rendu " + recus
                    + " vecteurs pour " + lot.size() + " textes : sa réponse est inexploitable.");
        }
        try {
            return reponse.embeddings().stream().map(Embedding::of).toList();
        } catch (IllegalArgumentException dimensionInattendue) {
            throw new EmbeddingUnavailableException(
                    "Le service de vectorisation ne produit pas des vecteurs de "
                            + EmbeddingPolicy.DIMENSIONS + " dimensions : " + dimensionInattendue.getMessage()
                            + ". Vérifier le modèle configuré.",
                    dimensionInattendue);
        }
    }

    private OllamaEmbeddingResponse appelerAvecTentatives(List<String> lot) {
        RestClientException dernierEchec = null;
        for (int tentative = 1; tentative <= MAX_ATTEMPTS; tentative++) {
            try {
                return restClient
                        .post()
                        .uri("/api/embed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new OllamaEmbeddingRequest(model, lot))
                        .retrieve()
                        .body(OllamaEmbeddingResponse.class);
            } catch (RestClientException echec) {
                dernierEchec = echec;
                if (tentative < MAX_ATTEMPTS) {
                    patienter();
                }
            }
        }
        throw new EmbeddingUnavailableException(
                "Le service de vectorisation est injoignable après " + MAX_ATTEMPTS + " tentatives.",
                dernierEchec);
    }

    private static void patienter() {
        try {
            Thread.sleep(RETRY_BACKOFF_MILLIS);
        } catch (InterruptedException interruption) {
            // Réarmer le drapeau : l'appelant doit pouvoir constater l'interruption.
            Thread.currentThread().interrupt();
            throw new EmbeddingUnavailableException("La vectorisation a été interrompue.", interruption);
        }
    }
}
```

- [ ] **Étape 7 : Déclarer les deux propriétés**

Dans `src/main/resources/application.yml`, à la suite du bloc `storage:` de `secondbrain:` :

```yaml
  embedding:
    # Où joindre le service de vectorisation. Comme la datasource, le défaut sert le
    # développement hors conteneur — 11434 est le port d'Ollama. En production, la variable
    # visera l'Ollama déjà en place plutôt qu'un second, embarqué pour rien.
    base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
    # Le modèle, et lui seul, est configurable. Sa DIMENSION ne l'est pas : elle vit dans
    # EmbeddingPolicy, parce qu'elle est le contrat entre le modèle, la colonne et l'index.
    # Changer de modèle est une migration et une réindexation, pas une variable.
    model: ${SECONDBRAIN_EMBEDDING_MODEL:bge-m3}
```

- [ ] **Étape 8 : Lancer le test, vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.ai.OllamaEmbeddingAdapterTest"
```

Attendu : SUCCÈS, six tests verts, en moins de deux secondes. Le test des tentatives coûte
400 ms de patience — c'est normal.

- [ ] **Étape 9 : Vérifier que le contexte démarre toujours**

```bash
gtest test --tests "xyz.sterenn.secondbrain.SecondBrainApplicationTests"
```

Attendu : SUCCÈS. C'est ce qui prouve que les deux `@Value` se résolvent : sans les
propriétés de l'étape 7, l'adapter empêcherait tout contexte de démarrer, y compris celui de
l'API — le bean n'est pas profilé, et il ne doit pas l'être, car le handler du livrable
suivant l'injectera sans savoir quel rôle l'exécute.

- [ ] **Étape 10 : Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/knowledge/domain/port/EmbeddingPort.java \
        src/main/java/xyz/sterenn/secondbrain/knowledge/domain/exception/EmbeddingUnavailableException.java \
        src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/ai/ \
        src/main/resources/application.yml \
        src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/ai/
git commit -m "feat: un port rend des vecteurs, un adapter Ollama les fabrique par lots"
```

---

## Tâche 4 : Ollama dans la pile de développement

Le code sait vectoriser ; il n'y a encore rien à qui parler. Cette tâche ne s'accompagne
d'aucun test automatisé — **c'est une configuration d'environnement, et elle se vérifie en la
lançant.** Les étapes de contrôle sont donc des commandes à exécuter et à lire.

**Fichiers :**
- Modifier : `compose.yaml` (services `ollama` et `ollama-pull`, variables sur `app` et
  `worker`, volume `ollama-models`)
- Modifier : `.env.example`
- Modifier : `CLAUDE.md` (la pile gagne un service)
- Modifier : `README.md` si sa section de démarrage énumère les services

**Interfaces :**
- Consomme : les propriétés `secondbrain.embedding.*` de la tâche 3.
- Produit : un Ollama joignable en `http://ollama:11434` depuis le réseau de la pile, portant
  `bge-m3`.

- [ ] **Étape 1 : Ajouter les deux services dans `compose.yaml`**

À la suite du service `rabbitmq` :

```yaml
  ollama:
    # `latest` et non une version épinglée, même statut que mailpit : un outil de
    # développement, pas un composant dont la version décide d'un comportement du produit.
    image: ollama/ollama:latest
    volumes:
      # Les modèles pèsent des gigaoctets : un volume nommé, jamais le bind mount.
      - ollama-models:/root/.ollama
    healthcheck:
      # `ollama list` répond dès que le serveur écoute, sans rien télécharger.
      test: ["CMD", "ollama", "list"]
      interval: 5s
      timeout: 30s
      retries: 10
    # Aucun port publié, à la différence de db, rabbitmq et mailpit. Deux raisons : seul le
    # worker lui parle, depuis le réseau de la pile ; et un port de plus devrait entrer dans
    # le décalage d'indices du skill `worktree`, sans quoi deux features se le disputeraient.
    # Pour l'interroger à la main : `docker compose exec ollama ollama list`.

  ollama-pull:
    image: ollama/ollama:latest
    # Un client, pas un serveur : OLLAMA_HOST le fait parler au conteneur voisin, qui
    # télécharge dans SON volume. Le puller n'a donc aucun volume à monter.
    environment:
      OLLAMA_HOST: http://ollama:11434
    entrypoint: ["/bin/sh", "-c"]
    command: ["ollama pull ${SECONDBRAIN_EMBEDDING_MODEL:-bge-m3}"]
    depends_on:
      ollama:
        condition: service_healthy
    # Il tire le modèle et s'arrête. Sans cette ligne, Compose le relancerait en boucle.
    restart: "no"
```

- [ ] **Étape 2 : Déclarer le volume**

Dans le bloc `volumes:` en fin de `compose.yaml`, à la suite de `originals:` :

```yaml
  ollama-models:
```

- [ ] **Étape 3 : Poser les variables sur `app` et sur `worker`**

Dans le bloc `environment:` de **chacun** des deux services, à la suite de
`SECONDBRAIN_ORIGINALS_PATH` :

```yaml
      OLLAMA_BASE_URL: http://ollama:11434
      SECONDBRAIN_EMBEDDING_MODEL: ${SECONDBRAIN_EMBEDDING_MODEL:-bge-m3}
```

Sur `app` aussi, bien que l'API ne vectorise rien : l'adapter n'est pas profilé — il ne peut
pas l'être, le handler du livrable suivant l'injectera sans savoir quel rôle l'exécute — et
deux rôles configurés différemment sont deux comportements à démêler le jour d'une panne.

Ajouter au `depends_on:` du **worker seul** :

```yaml
      ollama:
        condition: service_started
```

`service_started` et non `service_healthy`, et surtout pas
`ollama-pull: service_completed_successfully` : le worker démarre sans attendre les 2,2 Go du
modèle. C'est la décision « aucun fail-fast ». Un document déposé pendant le téléchargement
échouera avec son motif, ce qui est visible et rattrapable.

- [ ] **Étape 4 : Documenter la variable dans `.env.example`**

À la suite du bloc RabbitMQ :

```bash
# Modèle d'embedding servi par Ollama. La DIMENSION qui va avec (1024) n'est pas une
# variable : elle vit dans EmbeddingPolicy, côté domaine, parce qu'elle est le contrat entre
# le modèle, la colonne `vector` et son index. En changer demande une migration et une
# réindexation complète — les vecteurs de deux modèles ne se comparent pas.
SECONDBRAIN_EMBEDDING_MODEL=bge-m3
```

- [ ] **Étape 5 : Lancer la pile et vérifier**

```bash
docker compose down -v
docker compose up -d
docker compose logs -f ollama-pull
```

Attendu : la barre de progression du téléchargement, puis `success`, puis le conteneur
s'arrête. Compter plusieurs minutes au premier lancement.

Puis :

```bash
docker compose exec ollama ollama list
```

Attendu : une ligne `bge-m3` avec sa taille.

- [ ] **Étape 6 : Vérifier que le worker joint le service**

```bash
docker compose exec worker sh -c 'curl -s -X POST http://ollama:11434/api/embed \
  -H "Content-Type: application/json" \
  -d "{\"model\":\"bge-m3\",\"input\":[\"bonjour\"]}" | head -c 200'
```

Attendu : un début de JSON `{"model":"bge-m3","embeddings":[[...`. Si `curl` manque dans
l'image, faire le même appel depuis le conteneur `ollama` en visant `localhost` — ce qu'on
vérifie ici est que le réseau de la pile relie les deux, et le nom `ollama` se résout de la
même façon.

- [ ] **Étape 7 : Vérifier que la pile est saine de bout en bout**

```bash
docker compose ps
docker compose logs app | tail -30
docker compose logs worker | tail -30
```

Attendu : `app` sain, `worker` démarré sans erreur, `ollama-pull` en `exited (0)`. Déposer un
document par l'interface doit continuer de produire son texte extrait comme avant — **ce
livrable ne change rien au traitement**, et c'est ce qu'on vérifie.

- [ ] **Étape 8 : Mettre `CLAUDE.md` à jour**

Dans la section « Stack et versions », sous-section **Développement**, ajouter Ollama à
l'énumération des services de la pile, et remplacer la mention de PostgreSQL 17 par
PostgreSQL 17 + pgvector dans la sous-section **Back**. Ajouter une phrase à la section
« Commandes », après le paragraphe sur le worker :

```markdown
Un service `ollama` sert le modèle d'embedding (`bge-m3`, 1024 dimensions), tiré au premier
démarrage par le conteneur one-shot `ollama-pull`. Il ne publie aucun port : seul le worker
lui parle, par le réseau de la pile. Pour l'interroger à la main,
`docker compose exec ollama ollama list`. Le worker **ne l'attend pas** pour démarrer — un
document traité pendant le téléchargement du modèle échoue avec un motif qui nomme la
vectorisation.
```

Vérifier `README.md` : si sa section de démarrage énumère les services, y ajouter Ollama.

- [ ] **Étape 9 : Arrêter la pile et lancer toute la suite**

```bash
docker compose down
gtest test
```

Attendu : SUCCÈS. `gtest` et `docker compose up` ne cohabitent pas.

- [ ] **Étape 10 : Committer**

```bash
git add compose.yaml .env.example CLAUDE.md README.md
git commit -m "conf: la pile de développement sert bge-m3 par un Ollama local"
```

---

## Ce que ce plan ne fait pas

- **Aucune table, aucune entité, aucun index HNSW.** `knowledge_text_chunks` arrive avec
  `TextChunk`, dans le livrable suivant.
- **Aucune dépendance `hibernate-vector`.** Elle arrive avec sa première entité mappée, pour
  la même raison.
- **Aucun aller-retour d'un `vector(1024)` en base.** Il faut une table ; c'est une dette
  explicite de ce livrable, payée par le premier test d'intégration du suivant.
- **Aucun découpage, aucun statut `READY`, aucun changement au traitement d'un document.**
  Déposer un fichier produit exactement ce qu'il produisait avant.
- **Aucun `LlmPort`, aucune génération.** RAG-2 les demandait dans le même souffle ; rien ne
  les consomme avant RAG-9, et un port naît avec son premier appelant.
- **Aucun contrôle au démarrage de la disponibilité d'Ollama.** C'est une décision, pas un
  oubli : voir la Javadoc d'`OllamaEmbeddingAdapter`.
- **Aucun ADR.** Arbitré avec le porteur du ticket. Ne pas en écrire.
