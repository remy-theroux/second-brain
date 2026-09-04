# Recherche vectorielle des extraits — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /api/search?q=…` rend les huit extraits les plus proches d'une question, chacun avec son texte, le nom de son document, sa position et son score de similarité.

**Architecture:** Une query CQRS (`SearchChunks`) vectorise la question par le port `EmbeddingPort` existant, puis interroge `knowledge_text_chunks` par une méthode nouvelle du port `TextChunkRepository`. La requête de proximité est du **SQL natif** posé sur le dépôt Spring Data existant : elle joint `knowledge_documents` pour cloisonner par propriétaire et pour rapporter le nom du document, et ordonne par `<=>` (distance cosinus pgvector). Aucune migration, aucune dépendance nouvelle, aucun changement côté front.

**Tech Stack:** Java 25 · Spring Boot 4.0.7 · Spring Data JPA · PostgreSQL 17 + pgvector · hibernate-vector · Ollama (`bge-m3`, 1024 dimensions) · JUnit 5 + AssertJ + Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-04-recherche-vectorielle-design.md`

## Global Constraints

- **Branche :** `feat/recherche-vectorielle`. Un commit par tâche, tests verts, préfixe conventionnel en minuscule et description en français.
- **Langue :** commentaires, Javadoc, messages d'exception et noms de méthodes de test **en français**. Noms de classes, de méthodes de production et de packages **en anglais**.
- **Commentaires :** par défaut, aucun. Un commentaire est une exception qui se justifie par un piège qu'aucune lecture attentive ne déduirait. Trois lignes est un plafond. Pas de Javadoc de façade. Les seuls commentaires prévus par ce plan sont ceux écrits noir sur blanc dans ses blocs de code : **ne pas en ajouter d'autres**.
- **Formatage :** `make format-back` avant chaque commit. Le style est décidé par Spotless + palantir-java-format ; ne pas se battre avec son résultat.
- **Aucun JDK, aucun Gradle, aucun Node sur l'hôte.** Définir la fonction `gtest` de `CLAUDE.md` une fois par session avant toute commande Gradle.
- **`gtest` et `docker compose up` ne cohabitent pas** : ils verrouillent le même `.gradle/`. Faire `docker compose down` avant de lancer les tests.
- **Jamais de `@Transactional` sur un handler** : la transaction appartient au bus.
- **Toute exception métier hérite de `RuntimeException`** : une exception checked ne déclencherait pas de rollback.
- **Le domaine n'importe jamais `org.springframework.*` ni `…infrastructure.*`.**
- **Aucun ADR n'est à écrire dans ce ticket** — arbitré à la conception. Les décisions vivent dans la spec.
- **Dimension du vecteur : 1024**, figée par `EmbeddingPolicy.DIMENSIONS` et par le type de la colonne.
- **Top-k : 8**, porté par `SearchPolicy.RESULTS` (tâche 1).

**Fonction `gtest` à définir une fois avant de commencer :**

```bash
gtest() {
  docker run --rm \
    --network host \
    -v "$PWD":/app -w /app \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v second-brain-gradle-home:/home/gradle/.gradle \
    gradle:jdk25 gradle --no-daemon "$@"
}
```

---

### Task 1 : `Question`, son refus et la politique de recherche

Le domaine pur, testable sans Spring. Indépendant de la tâche 2 : les deux peuvent se faire dans n'importe quel ordre.

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/SearchPolicy.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/exception/InvalidQuestionException.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/Question.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/QuestionTest.java`

**Interfaces:**
- Consumes: rien.
- Produces:
  - `SearchPolicy.RESULTS` — `public static final int`, vaut `8`.
  - `new Question(String value)` — record ; `question.value()` rend la chaîne amputée de ses espaces de bord ; lève `InvalidQuestionException` sur `null`, vide ou blanc.
  - `InvalidQuestionException extends RuntimeException`, constructeur `(String message)`.

- [ ] **Step 1: Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/QuestionTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidQuestionException;

class QuestionTest {

    @Test
    void refuse_une_question_absente() {
        assertThatThrownBy(() -> new Question(null))
                .isInstanceOf(InvalidQuestionException.class)
                .hasMessage("La question ne peut pas être vide.");
    }

    @Test
    void refuse_une_question_vide() {
        assertThatThrownBy(() -> new Question("")).isInstanceOf(InvalidQuestionException.class);
    }

    @Test
    void refuse_une_question_faite_d_espaces() {
        assertThatThrownBy(() -> new Question("   \n\t ")).isInstanceOf(InvalidQuestionException.class);
    }

    @Test
    void ampute_les_espaces_de_bord() {
        assertThat(new Question("  Qui a signé le rapport ?  ").value()).isEqualTo("Qui a signé le rapport ?");
    }

    @Test
    void deux_ecritures_d_une_meme_question_sont_egales() {
        assertThat(new Question(" Quand ? ")).isEqualTo(new Question("Quand ?"));
    }
}
```

- [ ] **Step 2: Lancer le test pour le voir échouer**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.valueobject.QuestionTest"
```

Attendu : ÉCHEC à la compilation — `Question` et `InvalidQuestionException` n'existent pas.

- [ ] **Step 3: Écrire l'implémentation minimale**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/exception/InvalidQuestionException.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.exception;

public class InvalidQuestionException extends RuntimeException {

    public InvalidQuestionException(String message) {
        super(message);
    }
}
```

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/Question.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidQuestionException;

public record Question(String value) {

    public Question {
        if (value == null || value.isBlank()) {
            throw new InvalidQuestionException("La question ne peut pas être vide.");
        }
        value = value.strip();
    }

    @Override
    public String toString() {
        return value;
    }
}
```

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/SearchPolicy.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain;

public final class SearchPolicy {

    /** Ce que RAG-9 consommera pour composer une réponse : un nombre du domaine, pas un réglage. */
    public static final int RESULTS = 8;

    private SearchPolicy() {}
}
```

- [ ] **Step 4: Lancer le test pour le voir passer**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.valueobject.QuestionTest"
```

Attendu : SUCCÈS, 5 tests.

- [ ] **Step 5: Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/knowledge/domain/SearchPolicy.java \
        src/main/java/xyz/sterenn/secondbrain/knowledge/domain/exception/InvalidQuestionException.java \
        src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/Question.java \
        src/test/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/QuestionTest.java
git commit -m "feat: une question vide n'en est pas une, et huit résultats est une règle"
```

---

### Task 2 : La requête de proximité, du port au SQL

**La tâche qui porte le risque du ticket** : le binding du type `vector` côté JDBC. Elle se fait tôt et elle se prouve contre Testcontainers avant que quoi que ce soit ne s'appuie dessus.

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/ChunkMatch.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/ChunkMatchRow.java`
- Modify: `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/port/TextChunkRepository.java`
- Modify: `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/SpringDataTextChunkRepository.java`
- Modify: `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/JpaTextChunkRepositoryAdapter.java`
- Modify: `src/test/java/xyz/sterenn/secondbrain/knowledge/KnowledgeFixture.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/JpaTextChunkRepositoryAdapterTest.java`

**Interfaces:**
- Consumes: `Embedding` (`Embedding.of(float[])`, `embedding.values()`), `Chunk(String heading, String text)`, `TextChunk.of(UUID documentId, int position, Chunk chunk, Embedding embedding, Instant createdAt)`, `EmbeddingPolicy.DIMENSIONS`.
- Produces:
  - `ChunkMatch(UUID documentId, String filename, int position, Chunk chunk, double similarity)` — record du domaine.
  - `TextChunkRepository.findNearest(UUID ownerId, Embedding question, int limit)` → `List<ChunkMatch>`, du plus proche au plus lointain.
  - `KnowledgeFixture.uneQuestion()` → `Embedding` orienté sur la dimension 0.
  - `KnowledgeFixture.unVecteurProche(float proximite)` → `Embedding` dont la similarité cosinus avec `uneQuestion()` croît strictement avec `proximite` sur `[0, 1]`.

- [ ] **Step 1: Ajouter la fixture de vecteurs orientés**

`KnowledgeFixture.unVecteur` remplit toutes les dimensions de la même valeur : tous les vecteurs qu'elle produit sont **colinéaires**, donc à distance cosinus nulle deux à deux. Aucun ordre ne s'y lit, et un test de proximité bâti dessus ne testerait rien.

Ajouter à `src/test/java/xyz/sterenn/secondbrain/knowledge/KnowledgeFixture.java`, à la suite de `unVecteur` :

```java
    /**
     * La question de référence des tests de proximité : orientée sur la seule dimension 0.
     * {@link #unVecteur} ne convient pas — elle rend des vecteurs tous colinéaires, donc à
     * distance cosinus nulle deux à deux.
     */
    public static Embedding uneQuestion() {
        float[] valeurs = new float[EmbeddingPolicy.DIMENSIONS];
        valeurs[0] = 1f;
        return Embedding.of(valeurs);
    }

    /** D'autant plus proche de {@link #uneQuestion()} que {@code proximite} approche de 1. */
    public static Embedding unVecteurProche(float proximite) {
        float[] valeurs = new float[EmbeddingPolicy.DIMENSIONS];
        valeurs[0] = proximite;
        valeurs[1] = 1f - proximite;
        return Embedding.of(valeurs);
    }
```

- [ ] **Step 2: Écrire les tests qui échouent**

Remplacer les deux méthodes privées de fin de fichier de `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/JpaTextChunkRepositoryAdapterTest.java` par les trois ci-dessous — `unDocumentDepose(String)` garde exactement son comportement actuel, les tests existants ne bougent pas :

```java
    private static TextChunk unExtrait(Document document, int position, String titre, String corps) {
        return TextChunk.of(
                document.getId(), position, new Chunk(titre, corps), KnowledgeFixture.unVecteur(0.5f), Instant.now());
    }

    private UUID unCompte(String email) {
        return userRepository.save(User.register(new Email(email), "empreinte")).getId();
    }

    private Document unDocumentDepose(UUID proprietaire, String nom) {
        byte[] octets = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        return documentRepository.save(
                Document.upload(proprietaire, nom, DocumentFormat.MARKDOWN, Checksum.of(octets), octets.length));
    }

    private Document unDocumentDepose(String email) {
        return unDocumentDepose(unCompte(email), "notes.md");
    }
```

Puis ajouter ces six tests à la même classe, avant les méthodes privées :

```java
    @Test
    void rend_les_extraits_du_plus_proche_au_plus_lointain() {
        UUID alice = unCompte("sylvie@exemple.fr");
        Document document = unDocumentDepose(alice, "rapport.md");
        textChunkRepository.saveAll(List.of(
                unExtraitOriente(document, 0, "Le plus lointain.", 0.1f),
                unExtraitOriente(document, 1, "Le plus proche.", 1f),
                unExtraitOriente(document, 2, "L'intermédiaire.", 0.6f)));

        assertThat(textChunkRepository.findNearest(alice, KnowledgeFixture.uneQuestion(), 8))
                .extracting(match -> match.chunk().text())
                .containsExactly("Le plus proche.", "L'intermédiaire.", "Le plus lointain.");
    }

    @Test
    void rend_le_nom_du_document_la_position_et_le_titre_de_chaque_extrait() {
        UUID alice = unCompte("thomas@exemple.fr");
        Document document = unDocumentDepose(alice, "rapport-annuel.md");
        textChunkRepository.saveAll(List.of(TextChunk.of(
                document.getId(),
                3,
                new Chunk("Introduction", "Le corps de l'extrait."),
                KnowledgeFixture.unVecteurProche(1f),
                Instant.now())));

        assertThat(textChunkRepository.findNearest(alice, KnowledgeFixture.uneQuestion(), 8))
                .singleElement()
                .satisfies(match -> {
                    assertThat(match.documentId()).isEqualTo(document.getId());
                    assertThat(match.filename()).isEqualTo("rapport-annuel.md");
                    assertThat(match.position()).isEqualTo(3);
                    assertThat(match.chunk()).isEqualTo(new Chunk("Introduction", "Le corps de l'extrait."));
                });
    }

    @Test
    void rend_une_similarite_de_un_pour_un_extrait_dont_le_vecteur_est_celui_de_la_question() {
        UUID alice = unCompte("ursula@exemple.fr");
        Document document = unDocumentDepose(alice, "notes.md");
        textChunkRepository.saveAll(List.of(unExtraitOriente(document, 0, "Identique.", 1f)));

        assertThat(textChunkRepository.findNearest(alice, KnowledgeFixture.uneQuestion(), 8))
                .singleElement()
                .satisfies(match -> assertThat(match.similarity()).isCloseTo(1d, within(0.0001d)));
    }

    @Test
    void ne_rend_pas_les_extraits_d_un_autre_compte() {
        UUID alice = unCompte("valentine@exemple.fr");
        UUID bob = unCompte("walid@exemple.fr");
        Document leSien = unDocumentDepose(alice, "a-elle.md");
        Document celuiDeBob = unDocumentDepose(bob, "a-lui.md");
        textChunkRepository.saveAll(List.of(
                unExtraitOriente(leSien, 0, "Le sien.", 0.5f), unExtraitOriente(celuiDeBob, 0, "Celui de Bob.", 1f)));

        assertThat(textChunkRepository.findNearest(alice, KnowledgeFixture.uneQuestion(), 8))
                .extracting(match -> match.chunk().text())
                .containsExactly("Le sien.");
    }

    @Test
    void plafonne_le_nombre_d_extraits_rendus() {
        UUID alice = unCompte("xavier@exemple.fr");
        Document document = unDocumentDepose(alice, "long.md");
        textChunkRepository.saveAll(IntStream.range(0, 12)
                .mapToObj(position -> unExtraitOriente(document, position, "Extrait " + position, 0.5f))
                .toList());

        assertThat(textChunkRepository.findNearest(alice, KnowledgeFixture.uneQuestion(), 8))
                .hasSize(8);
    }

    @Test
    void reste_muet_quand_le_compte_ne_porte_aucun_extrait() {
        UUID alice = unCompte("yasmine@exemple.fr");

        assertThat(textChunkRepository.findNearest(alice, KnowledgeFixture.uneQuestion(), 8))
                .isEmpty();
    }

    private static TextChunk unExtraitOriente(Document document, int position, String corps, float proximite) {
        return TextChunk.of(
                document.getId(),
                position,
                new Chunk("Titre", corps),
                KnowledgeFixture.unVecteurProche(proximite),
                Instant.now());
    }
```

Ajouter les imports manquants en tête de fichier :

```java
import static org.assertj.core.api.Assertions.within;

import java.util.stream.IntStream;
```

- [ ] **Step 3: Lancer les tests pour les voir échouer**

```bash
docker compose down
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.persistence.JpaTextChunkRepositoryAdapterTest"
```

Attendu : ÉCHEC à la compilation — `findNearest` et `ChunkMatch` n'existent pas.

- [ ] **Step 4: Écrire le value object du domaine**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/ChunkMatch.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record ChunkMatch(UUID documentId, String filename, int position, Chunk chunk, double similarity) {

    public ChunkMatch {
        Objects.requireNonNull(documentId, "Le document dont cet extrait provient est obligatoire");
        Objects.requireNonNull(filename, "Le nom du document est obligatoire");
        Objects.requireNonNull(chunk, "L'extrait est obligatoire");
    }
}
```

- [ ] **Step 5: Ouvrir le port**

Dans `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/port/TextChunkRepository.java`, ajouter l'import `xyz.sterenn.secondbrain.knowledge.domain.valueobject.ChunkMatch` et `xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding`, puis la méthode après `findByDocumentId` :

```java
    /**
     * Les extraits du propriétaire les plus proches du vecteur donné, du plus proche au plus
     * lointain, au plus {@code limit}.
     */
    List<ChunkMatch> findNearest(UUID ownerId, Embedding question, int limit);
```

Adapter la phrase d'ouverture du Javadoc de l'interface, qui ne dit plus toute la vérité :

```java
/**
 * Port sortant vers le stockage des extraits vectorisés : ils se lisent par l'identifiant de
 * leur document, dans l'ordre du document, ou par proximité avec un vecteur.
 */
```

- [ ] **Step 6: Écrire la projection et la requête native**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/ChunkMatchRow.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.UUID;

interface ChunkMatchRow {

    UUID getDocumentId();

    String getFilename();

    int getChunkPosition();

    String getHeading();

    String getChunkText();

    double getSimilarity();
}
```

Dans `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/SpringDataTextChunkRepository.java`, ajouter les imports `org.springframework.data.jpa.repository.Query` et `org.springframework.data.repository.query.Param`, puis la méthode :

```java
    // `position` et `text` heurtent la grammaire de PostgreSQL comme alias, et pgvector
    // n'accepte aucune conversion implicite : d'où les alias préfixés et le CAST explicite.
    @Query(
            value =
                    """
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
                    """,
            nativeQuery = true)
    List<ChunkMatchRow> findNearest(
            @Param("ownerId") UUID ownerId, @Param("question") String question, @Param("limit") int limit);
```

- [ ] **Step 7: Écrire l'adapter**

Dans `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/JpaTextChunkRepositoryAdapter.java`, ajouter les imports `xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk`, `…valueobject.ChunkMatch` et `…valueobject.Embedding`, puis la méthode après `findByDocumentId` :

```java
    @Override
    public List<ChunkMatch> findNearest(UUID ownerId, Embedding question, int limit) {
        return springDataTextChunkRepository.findNearest(ownerId, litteralPgvector(question), limit).stream()
                .map(ligne -> new ChunkMatch(
                        ligne.getDocumentId(),
                        ligne.getFilename(),
                        ligne.getChunkPosition(),
                        new Chunk(ligne.getHeading(), ligne.getChunkText()),
                        ligne.getSimilarity()))
                .toList();
    }

    private static String litteralPgvector(Embedding embedding) {
        float[] valeurs = embedding.values();
        StringBuilder litteral = new StringBuilder(valeurs.length * 12).append('[');
        for (int dimension = 0; dimension < valeurs.length; dimension++) {
            if (dimension > 0) {
                litteral.append(',');
            }
            litteral.append(valeurs[dimension]);
        }
        return litteral.append(']').toString();
    }
```

- [ ] **Step 8: Lancer les tests pour les voir passer**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.persistence.JpaTextChunkRepositoryAdapterTest"
```

Attendu : SUCCÈS, les six tests nouveaux comme les six existants.

**Si le binding échoue** — message du genre `column "question" does not exist`, `could not determine data type of parameter`, ou `cannot cast type character varying to vector` — c'est le repli prévu par la spec, décision 2, et **lui seul** : remplacer la méthode de `SpringDataTextChunkRepository` par un `JdbcTemplate` injecté dans `JpaTextChunkRepositoryAdapter`, avec un `PGobject` dont `setType("vector")` et `setValue(litteralPgvector(question))`. Le port, `ChunkMatch`, les tests et tout le reste du plan ne bougent pas. Ne pas improviser une troisième voie.

- [ ] **Step 9: Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/ChunkMatch.java \
        src/main/java/xyz/sterenn/secondbrain/knowledge/domain/port/TextChunkRepository.java \
        src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/ \
        src/test/java/xyz/sterenn/secondbrain/knowledge/KnowledgeFixture.java \
        src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/JpaTextChunkRepositoryAdapterTest.java
git commit -m "feat: le port des extraits sait rendre les plus proches d'un vecteur"
```

---

### Task 3 : La query et son handler

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/application/query/SearchChunks.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/application/query/ChunkMatchView.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/application/query/SearchChunksHandler.java`
- Modify: `src/test/java/xyz/sterenn/secondbrain/knowledge/RecordingEmbeddingPortConfiguration.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/knowledge/application/query/SearchChunksTest.java`

**Interfaces:**
- Consumes: `SearchPolicy.RESULTS`, `new Question(String)`, `InvalidQuestionException` (tâche 1) ; `TextChunkRepository.findNearest(UUID, Embedding, int)`, `ChunkMatch`, `KnowledgeFixture.uneQuestion()`, `KnowledgeFixture.unVecteurProche(float)` (tâche 2) ; `EmbeddingPort.embed(List<String>)` → `List<Embedding>` ; `QueryBus.ask(Query<R>)`.
- Produces:
  - `SearchChunks(String question, UUID ownerId) implements Query<List<ChunkMatchView>>`.
  - `ChunkMatchView(UUID documentId, String filename, int position, String heading, String text, double similarity)` avec la fabrique `ChunkMatchView.of(ChunkMatch)`.
  - `RecordingEmbeddingPort.repondra(Embedding vecteur)` — impose le vecteur rendu par tout appel suivant ; `clear()` le remet à néant.

- [ ] **Step 1: Rendre le port de vectorisation de test pilotable**

Les tests existants comptent sur `vecteurDuRang`, qui rend des vecteurs colinéaires : ils ne peuvent pas servir à ordonner. Ajouter une réponse imposée, **sans changer le comportement par défaut**.

Dans `src/test/java/xyz/sterenn/secondbrain/knowledge/RecordingEmbeddingPortConfiguration.java`, ajouter l'import `java.util.concurrent.atomic.AtomicReference`, puis dans `RecordingEmbeddingPort` :

```java
        private final AtomicReference<Embedding> reponseImposee = new AtomicReference<>();
```

Remplacer la boucle de `embed` par :

```java
            List<Embedding> vecteurs = new ArrayList<>();
            for (String texte : texts) {
                Embedding impose = reponseImposee.get();
                vecteurs.add(impose != null ? impose : vecteurDuRang(recus.size()));
                recus.add(texte);
            }
            return vecteurs;
```

Ajouter la méthode et compléter `clear()` :

```java
        public void repondra(Embedding vecteur) {
            reponseImposee.set(vecteur);
        }
```

```java
        public void clear() {
            recus.clear();
            enPanne.set(false);
            reponseImposee.set(null);
        }
```

- [ ] **Step 2: Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/knowledge/application/query/SearchChunksTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration.RecordingEmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidQuestionException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import({TestcontainersConfiguration.class, RecordingEmbeddingPortConfiguration.class})
@SpringBootTest
@Transactional
class SearchChunksTest {

    @Autowired
    private QueryBus queryBus;

    @Autowired
    private RecordingEmbeddingPort recordingEmbeddingPort;

    @Autowired
    private TextChunkRepository textChunkRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void la_question_se_vectorise_toujours_de_la_meme_facon() {
        recordingEmbeddingPort.clear();
        recordingEmbeddingPort.repondra(KnowledgeFixture.uneQuestion());
    }

    @AfterEach
    void rend_le_port_de_vectorisation_comme_il_l_a_trouve() {
        recordingEmbeddingPort.clear();
    }

    @Test
    void rend_l_extrait_qui_porte_la_reponse_en_tete() {
        UUID alice = unCompte("alice@exemple.fr");
        Document document = unDocumentDepose(alice, "rapport.md");
        textChunkRepository.saveAll(List.of(
                unExtrait(document, 0, "Une digression sans rapport.", 0.1f),
                unExtrait(document, 1, "La réponse est quarante-deux.", 1f),
                unExtrait(document, 2, "Un passage à peu près sur le sujet.", 0.6f)));

        List<ChunkMatchView> resultats = queryBus.ask(new SearchChunks("Quelle est la réponse ?", alice));

        assertThat(resultats)
                .extracting(ChunkMatchView::text)
                .startsWith("La réponse est quarante-deux.");
    }

    @Test
    void rend_pour_chaque_extrait_son_contenu_son_document_sa_position_et_son_score() {
        UUID alice = unCompte("bruno@exemple.fr");
        Document document = unDocumentDepose(alice, "rapport-annuel.md");
        textChunkRepository.saveAll(List.of(TextChunk.of(
                document.getId(),
                2,
                new Chunk("Introduction", "Le corps de l'extrait."),
                KnowledgeFixture.unVecteurProche(1f),
                Instant.now())));

        List<ChunkMatchView> resultats = queryBus.ask(new SearchChunks("Une question", alice));

        assertThat(resultats).singleElement().satisfies(resultat -> {
            assertThat(resultat.documentId()).isEqualTo(document.getId());
            assertThat(resultat.filename()).isEqualTo("rapport-annuel.md");
            assertThat(resultat.position()).isEqualTo(2);
            assertThat(resultat.heading()).isEqualTo("Introduction");
            assertThat(resultat.text()).isEqualTo("Le corps de l'extrait.");
            assertThat(resultat.similarity()).isGreaterThan(0.99d);
        });
    }

    @Test
    void rend_une_liste_vide_pour_une_base_de_connaissance_vide() {
        UUID alice = unCompte("clara@exemple.fr");

        assertThat(queryBus.ask(new SearchChunks("Une question", alice))).isEmpty();
    }

    @Test
    void ne_rend_pas_les_extraits_d_un_autre_compte() {
        UUID alice = unCompte("diane@exemple.fr");
        UUID bob = unCompte("edgar@exemple.fr");
        textChunkRepository.saveAll(List.of(
                unExtrait(unDocumentDepose(alice, "a-elle.md"), 0, "Le sien.", 0.5f),
                unExtrait(unDocumentDepose(bob, "a-lui.md"), 0, "Celui de Bob.", 1f)));

        assertThat(queryBus.ask(new SearchChunks("Une question", alice)))
                .extracting(ChunkMatchView::text)
                .containsExactly("Le sien.");
    }

    @Test
    void ne_rend_jamais_plus_de_huit_extraits() {
        UUID alice = unCompte("fatou@exemple.fr");
        Document document = unDocumentDepose(alice, "long.md");
        textChunkRepository.saveAll(IntStream.range(0, 12)
                .mapToObj(position -> unExtrait(document, position, "Extrait " + position, 0.5f))
                .toList());

        assertThat(queryBus.ask(new SearchChunks("Une question", alice))).hasSize(8);
    }

    @Test
    void refuse_une_question_vide() {
        UUID alice = unCompte("gaspard@exemple.fr");

        assertThatThrownBy(() -> queryBus.ask(new SearchChunks("   ", alice)))
                .isInstanceOf(InvalidQuestionException.class)
                .hasMessage("La question ne peut pas être vide.");
    }

    @Test
    void vectorise_la_question_telle_qu_elle_a_ete_posee() {
        UUID alice = unCompte("helena@exemple.fr");

        queryBus.ask(new SearchChunks("  Qui a signé le rapport ?  ", alice));

        assertThat(recordingEmbeddingPort.textesRecus()).containsExactly("Qui a signé le rapport ?");
    }

    private static TextChunk unExtrait(Document document, int position, String corps, float proximite) {
        return TextChunk.of(
                document.getId(),
                position,
                new Chunk("Titre", corps),
                KnowledgeFixture.unVecteurProche(proximite),
                Instant.now());
    }

    private UUID unCompte(String email) {
        return userRepository.save(User.register(new Email(email), "empreinte")).getId();
    }

    private Document unDocumentDepose(UUID proprietaire, String nom) {
        byte[] octets = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        return documentRepository.save(
                Document.upload(proprietaire, nom, DocumentFormat.MARKDOWN, Checksum.of(octets), octets.length));
    }
}
```

Le test `vectorise_la_question_telle_qu_elle_a_ete_posee` est celui qui constate la décision 5 de la spec : **la question part nue**, sans le préfixe `Document: … — Section: …` qu'ajoute `Chunk.contextualised` à l'indexation.

- [ ] **Step 3: Lancer le test pour le voir échouer**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.application.query.SearchChunksTest"
```

Attendu : ÉCHEC à la compilation — `SearchChunks` et `ChunkMatchView` n'existent pas.

- [ ] **Step 4: Écrire la query, la vue et le handler**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/application/query/SearchChunks.java` :

```java
package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.List;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Query;

public record SearchChunks(String question, UUID ownerId) implements Query<List<ChunkMatchView>> {}
```

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/application/query/ChunkMatchView.java` :

```java
package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ChunkMatch;

public record ChunkMatchView(
        UUID documentId, String filename, int position, String heading, String text, double similarity) {

    public static ChunkMatchView of(ChunkMatch chunkMatch) {
        return new ChunkMatchView(
                chunkMatch.documentId(),
                chunkMatch.filename(),
                chunkMatch.position(),
                chunkMatch.chunk().heading(),
                chunkMatch.chunk().text(),
                chunkMatch.similarity());
    }
}
```

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/application/query/SearchChunksHandler.java` :

```java
package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.List;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.SearchPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.port.EmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Question;
import xyz.sterenn.secondbrain.shared.bus.QueryHandler;

@Component
public class SearchChunksHandler implements QueryHandler<SearchChunks, List<ChunkMatchView>> {

    private final EmbeddingPort embeddingPort;
    private final TextChunkRepository textChunkRepository;

    public SearchChunksHandler(EmbeddingPort embeddingPort, TextChunkRepository textChunkRepository) {
        this.embeddingPort = embeddingPort;
        this.textChunkRepository = textChunkRepository;
    }

    @Override
    public List<ChunkMatchView> handle(SearchChunks query) {
        Question question = new Question(query.question());
        Embedding vecteur =
                embeddingPort.embed(List.of(question.value())).getFirst();
        return textChunkRepository.findNearest(query.ownerId(), vecteur, SearchPolicy.RESULTS).stream()
                .map(ChunkMatchView::of)
                .toList();
    }
}
```

- [ ] **Step 5: Lancer le test pour le voir passer**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.application.query.SearchChunksTest"
```

Attendu : SUCCÈS, 7 tests.

- [ ] **Step 6: Vérifier qu'aucun test existant n'a bougé**

Le port de vectorisation de test est partagé par plusieurs classes ; la modification du step 1 doit être invisible pour elles.

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.application.command.IndexDocumentTextTest" \
           --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.messaging.KnowledgeEventListenerTest"
```

Attendu : SUCCÈS.

- [ ] **Step 7: Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/knowledge/application/query/ \
        src/test/java/xyz/sterenn/secondbrain/knowledge/RecordingEmbeddingPortConfiguration.java \
        src/test/java/xyz/sterenn/secondbrain/knowledge/application/query/SearchChunksTest.java
git commit -m "feat: une question vectorisée ramène les huit extraits les plus proches"
```

---

### Task 4 : La route de diagnostic

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/web/SearchChunksController.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/web/SearchChunksControllerTest.java`

**Interfaces:**
- Consumes: `SearchChunks`, `ChunkMatchView`, `RecordingEmbeddingPort.repondra(Embedding)` et `RecordingEmbeddingPort.tombeEnPanne()` (tâche 3) ; `InvalidQuestionException` (tâche 1) ; `KnowledgeFixture.uneQuestion()` et `KnowledgeFixture.unVecteurProche(float)` (tâche 2) ; `EmbeddingUnavailableException` ; `KnowledgeFixture.jeton(AccessTokenIssuer, UUID)` ; `JwtSubject.accountId(Jwt)` et `JwtSubject.UnreadableSubjectException` (package-private, même package que le contrôleur) ; `ErrorResponse(String message)` et `ValidationErrorResponse(Map<String, String> errors)` de `shared/web`.
- Produces: la route `GET /api/search?q=…`. Rien qu'une tâche ultérieure ne consomme.

- [ ] **Step 1: Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/web/SearchChunksControllerTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration.RecordingEmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import({TestcontainersConfiguration.class, RecordingEmbeddingPortConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SearchChunksControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingEmbeddingPort recordingEmbeddingPort;

    @Autowired
    private TextChunkRepository textChunkRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    private UUID alice;
    private String jetonAlice;

    @BeforeEach
    void prepare_un_compte_connecte() {
        recordingEmbeddingPort.clear();
        recordingEmbeddingPort.repondra(KnowledgeFixture.uneQuestion());
        alice = userRepository
                .save(User.register(new Email("alice@exemple.fr"), "empreinte"))
                .getId();
        jetonAlice = KnowledgeFixture.jeton(accessTokenIssuer, alice);
    }

    @AfterEach
    void rend_le_port_de_vectorisation_comme_il_l_a_trouve() {
        recordingEmbeddingPort.clear();
    }

    @Test
    void rend_le_contenu_le_document_la_position_et_le_score_de_chaque_extrait() throws Exception {
        Document document = unDocumentDepose("rapport-annuel.md");
        textChunkRepository.saveAll(List.of(TextChunk.of(
                document.getId(),
                2,
                new Chunk("Introduction", "Le corps de l'extrait."),
                KnowledgeFixture.unVecteurProche(1f),
                Instant.now())));

        mockMvc.perform(get("/api/search")
                        .param("q", "Quelle est la réponse ?")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(document.getId().toString()))
                .andExpect(jsonPath("$[0].filename").value("rapport-annuel.md"))
                .andExpect(jsonPath("$[0].position").value(2))
                .andExpect(jsonPath("$[0].heading").value("Introduction"))
                .andExpect(jsonPath("$[0].text").value("Le corps de l'extrait."))
                .andExpect(jsonPath("$[0].similarity").isNumber());
    }

    @Test
    void rend_une_liste_vide_pour_une_base_de_connaissance_vide() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "Quelle est la réponse ?")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void refuse_sans_jeton() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "Quelle est la réponse ?"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refuse_une_question_vide_en_nommant_le_parametre() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "   ")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.q").value("La question ne peut pas être vide."));
    }

    @Test
    void refuse_une_question_absente_comme_une_question_vide() throws Exception {
        mockMvc.perform(get("/api/search").header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.q").value("La question ne peut pas être vide."));
    }

    @Test
    void repond_indisponible_quand_la_vectorisation_ne_repond_pas() throws Exception {
        recordingEmbeddingPort.tombeEnPanne();

        mockMvc.perform(get("/api/search")
                        .param("q", "Quelle est la réponse ?")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private Document unDocumentDepose(String nom) {
        byte[] octets = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        return documentRepository.save(
                Document.upload(alice, nom, DocumentFormat.MARKDOWN, Checksum.of(octets), octets.length));
    }
}
```

Chacun des trois tests de refus n'a qu'un seul appel HTTP, et c'est le dernier : l'exception métier traverse le proxy transactionnel du bus et marque la transaction du test « rollback-only ». Un second appel échouerait sur une `UnexpectedRollbackException` sans rien apprendre.

- [ ] **Step 2: Lancer le test pour le voir échouer**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.web.SearchChunksControllerTest"
```

Attendu : ÉCHEC — `404` sur `/api/search`, la route n'existe pas.

- [ ] **Step 3: Écrire le contrôleur**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/web/SearchChunksController.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.knowledge.application.query.SearchChunks;
import xyz.sterenn.secondbrain.knowledge.domain.exception.EmbeddingUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidQuestionException;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.shared.web.ErrorResponse;
import xyz.sterenn.secondbrain.shared.web.ValidationErrorResponse;

@RestController
public class SearchChunksController {

    private final QueryBus queryBus;

    public SearchChunksController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    // defaultValue = "" : un paramètre absent suit le chemin d'un paramètre vide, donc un seul
    // refus à écrire.
    @GetMapping("/api/search")
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<Object> search(
            @RequestParam(name = "q", defaultValue = "") String question, @AuthenticationPrincipal Jwt jwt) {
        try {
            return ResponseEntity.ok(queryBus.ask(new SearchChunks(question, JwtSubject.accountId(jwt))));
        } catch (InvalidQuestionException questionIllisible) {
            return ResponseEntity.unprocessableEntity()
                    .body(new ValidationErrorResponse(Map.of("q", questionIllisible.getMessage())));
        } catch (EmbeddingUnavailableException vectorisationInjoignable) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse("La recherche est momentanément indisponible : le service de "
                            + "vectorisation n'a pas répondu. Réessayez dans quelques instants."));
        }
    }

    @ExceptionHandler(JwtSubject.UnreadableSubjectException.class)
    public ResponseEntity<Object> sujetIllisible() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
```

- [ ] **Step 4: Lancer le test pour le voir passer**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.web.SearchChunksControllerTest"
```

Attendu : SUCCÈS, 6 tests.

- [ ] **Step 5: Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/web/SearchChunksController.java \
        src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/web/SearchChunksControllerTest.java
git commit -m "feat: GET /api/search rend les extraits proches d'une question avec leurs scores"
```

---

### Task 5 : Documentation, contrôle sur la pile et mesure de référence

Aucune fonctionnalité nouvelle : la suite complète, la mesure que la spec promet, et les deux paragraphes de `CLAUDE.md` qui décrivent le flux.

**Files:**
- Modify: `CLAUDE.md` (section « Architecture » : arborescence de `knowledge`, et une sous-section de flux)

**Interfaces:**
- Consumes: tout ce qui précède.
- Produces: rien de code.

- [ ] **Step 1: Lancer la suite complète**

```bash
docker compose down
make check
```

Attendu : SUCCÈS des deux côtés — le formatage et les tests. `spotlessCheck` est accroché à `check` : du Java mal formaté fait échouer la CI sans étape dédiée.

- [ ] **Step 2: Décrire le flux dans `CLAUDE.md`**

Dans la section « Architecture », compléter l'arborescence de `knowledge` :

- sous `domain/` : `SearchPolicy   nombre d'extraits rendus par une recherche`
- sous `domain/valueobject/` : `Question` (la question posée, non vide) et `ChunkMatch` (un extrait retrouvé et son score)
- sous `domain/exception/` : `InvalidQuestionException`
- sous `application/query/` : `SearchChunks + ChunkMatchView`
- la ligne de `port/` mentionne déjà `TextChunkRepository` : ne rien y changer

Ajouter, après la sous-section « Le flux du découpage et de la vectorisation », une sous-section « Le flux de la recherche » d'une quinzaine de lignes, qui dit — et rien de plus :

- `GET /api/search?q=…` vectorise la question par le même port qu'à l'indexation, puis rend les huit extraits les plus proches au cosinus, chacun avec son texte, son document, sa position et son score.
- Le score est une **similarité** (`1 - distance`), donc 1 pour identique, parce que c'est une route de **diagnostic** : elle montre les scores faibles, seul moyen d'instruire un défaut de pertinence. Aucun plancher, aucun `?k=`.
- La requête est du **SQL natif** sur le dépôt Spring Data, avec un `CAST` explicite en `vector` — pgvector n'accepte aucune conversion implicite — et une **jointure vers `knowledge_documents`** qui porte à la fois le cloisonnement et le nom du document.
- **La question part nue**, sans le préfixe `Document: … — Section: …` que porte l'extrait à l'indexation : `bge-m3` ne réclame aucune instruction de rôle. Renvoyer à la spec pour la raison longue.
- L'appel de vectorisation a lieu **dans la transaction `readOnly` du query bus**.
- Un document resté `EXTRACTED` n'est pas cherchable, et rien ici ne le rattrape : c'est RAG-7.

Respecter le ton du fichier : des phrases, pas de listes à puces télégraphiques, et le *pourquoi* plutôt que le *quoi*.

- [ ] **Step 3: Mesurer la requête sur la pile de développement**

```bash
docker compose up -d --build
docker compose logs -f app     # attendre que Tomcat écoute, puis Ctrl-C
```

Déposer deux ou trois documents par l'interface (<http://localhost:8080/documents>) et attendre qu'ils passent `READY` — `docker compose logs -f worker` montre l'avancement ; le premier démarrage télécharge le modèle `bge-m3`.

Puis mesurer la requête SQL **seule**, hors appel d'embedding, avec un vecteur quelconque :

```bash
docker compose exec db psql -U second_brain -d second_brain -c "\timing on" -c "
EXPLAIN ANALYZE
SELECT c.id, 1 - (c.embedding <=> (SELECT embedding FROM knowledge_text_chunks LIMIT 1)) AS similarity
FROM knowledge_text_chunks c
JOIN knowledge_documents d ON d.id = c.document_id
WHERE d.owner_id = (SELECT owner_id FROM knowledge_documents LIMIT 1)
ORDER BY c.embedding <=> (SELECT embedding FROM knowledge_text_chunks LIMIT 1)
LIMIT 8;"
```

Relever l'`Execution Time` et le consigner dans le rapport de la tâche. La spec (décision 7) le demande explicitement, et il n'y a **pas** d'assertion à écrire : une suite Testcontainers partagée rend un chronomètre instable, et RAG-14 est le vrai garde-fou. Un scan séquentiel plutôt qu'un parcours du HNSW est attendu sur un si petit volume : le noter, ne pas le corriger.

- [ ] **Step 4: Vérifier la route à la main**

Récupérer un jeton, puis :

```bash
curl -s "http://localhost:8080/api/search?q=Quelle+est+la+réponse" \
  -H "Authorization: Bearer $JETON" | head -c 2000
```

Contrôler que la forme des résultats est celle attendue et que l'ordre des scores est décroissant. Vérifier aussi que la route apparaît dans Swagger UI (<http://localhost:8080/swagger-ui.html>).

- [ ] **Step 5: Arrêter la pile, formater et committer**

```bash
docker compose down
make format
git add CLAUDE.md
git commit -m "docs: CLAUDE.md décrit le flux de la recherche vectorielle"
```

- [ ] **Step 6: Dernier contrôle avant relecture**

```bash
make check
git log --oneline main..HEAD
```

Attendu : `make check` vert, et cinq commits — quatre `feat:`/`docs:` de tâches plus le `docs:` de la spec.

---

## Ce que ce plan ne fait pas

- **Pas d'écran de recherche.** Swagger UI et `curl` suffisent à instruire la pertinence. L'écran viendra avec RAG-9.
- **Pas de migration, pas de dépendance nouvelle, pas d'ADR.**
- **Pas d'assertion de temps.** Spec, décision 7.
- **Pas de recherche hybride, pas de reranking, pas de filtre par document.** v1.1, écartés par le ticket.
- **Pas de réindexation d'un document resté `EXTRACTED`.** RAG-7.
