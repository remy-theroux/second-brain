# Découper un document en extraits contextualisés — plan d'implémentation

> **Pour les agents d'exécution :** SOUS-COMPÉTENCE REQUISE — utiliser
> `superpowers:subagent-driven-development` (recommandé) ou `superpowers:executing-plans`
> pour dérouler ce plan tâche par tâche. Les étapes sont des cases à cocher (`- [ ]`).

**But :** un document dont le texte vient d'être extrait porte, quelques dizaines de secondes
plus tard, ses extraits vectorisés en base et le statut `READY` — ou un `FAILED` et un motif
lisible qui nomme la vectorisation.

**Architecture :** le découpage est une **logique de domaine pure** (`RecursiveChunker`) qui
rend des objets-valeurs `Chunk` ; le handler `IndexDocumentTextHandler` en fait des entités
`TextChunk` vectorisées, dans la transaction ouverte par le bus. Le comptage de tokens passe
par un port (`TokenCounter`, adapter jtokkit) parce que `cl100k_base` n'est pas la toise de
`bge-m3`. Le worker remplace sa ligne de journal par un dispatch, et le cycle de vie du
document gagne son état terminal.

**Stack :** Java 25 · Spring Boot 4.0.7 · PostgreSQL 17 + pgvector 0.8.6 · Hibernate ORM
7.2 (`hibernate-vector`) · jtokkit 1.1.0 · `java.text.BreakIterator` (JDK) · Ollama ·
`bge-m3` (1024 dimensions) · JUnit 5 + AssertJ + Testcontainers + Awaitility.

**Spec :** `docs/superpowers/specs/2026-08-31-decoupage-extraits-design.md` — le plan
argumente depuis elle ; **la lire avant d'exécuter**. Premier livrable du même ticket, déjà
livré : `docs/superpowers/specs/2026-08-31-socle-vectoriel-design.md`.

## Contraintes globales

Elles s'ajoutent implicitement aux exigences de **chaque** tâche.

- **Tout passe par Docker.** Aucun JDK, aucun Gradle, aucun Node sur l'hôte. Définir les
  fonctions `gtest` et `gfront` de `CLAUDE.md` une fois par session, avant la première
  commande.
- **`gtest` et `docker compose up` ne cohabitent pas** : `docker compose down` avant de
  lancer la suite. Les deux verrouillent le même `.gradle/`.
- **Français** pour les commentaires, la Javadoc, les messages d'exception, les libellés et
  les noms de méthodes de test. **Anglais** pour les noms de classes, de méthodes de
  production et de packages.
- **`make format-back` avant chaque commit** (et `make format-front` pour la tâche 8). Le
  style est décidé par palantir-java-format ; ne pas se battre avec lui. La Javadoc et les
  commentaires ne sont jamais reformatés : leur mise en forme reste à la charge du rédacteur,
  et c'est elle qui porte le raisonnement.
- **Le domaine n'importe jamais `org.springframework.*` ni `…infrastructure.*`.** Seule
  exception, déjà actée : les annotations de persistance sur les entités (ADR-0002), que
  `TextChunk` reprend telles quelles — `jakarta.persistence` et `org.hibernate.annotations`,
  comme `Document` porte déjà `@CreationTimestamp`.
- **Toute exception métier hérite de `RuntimeException`** — une exception checked ne
  déclenche pas le rollback promis par le `CommandBus`.
- **Jamais de `@Transactional` sur un handler.** La transaction appartient au bus ; annoter
  le handler casse la résolution de son type générique au démarrage.
- **Flyway est maître du schéma**, `ddl-auto: validate`. Ne jamais modifier une migration
  déjà appliquée. La seule migration de ce plan est **`V10`**.
- **Pattern d'intégration imposé** : `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`.
  Ne pas introduire `@DataJpaTest`.
- **Dans un test `@Transactional`, un appel refusé est le dernier du test** : l'exception
  marque la transaction englobante « rollback-only ».
- **Aucun ADR n'est dû par ce plan.** Arbitré avec le porteur du ticket : les dix décisions
  de la spec n'en sont pas au sens de `.claude/rules/decisions.md`. Le raisonnement va dans
  la Javadoc des classes et les messages de commit. **Ne pas en écrire un de sa propre
  initiative** — la règle est explicite.
- **Un commit par tâche**, tests verts, préfixe conventionnel minuscule (`feat:`, `conf:`,
  `refactor:`, `test:`, `docs:`).

## Structure des fichiers

```
src/main/java/xyz/sterenn/secondbrain/knowledge/
├── domain/
│   ├── ChunkingPolicy.java                       CRÉÉ  T3  cible, plafond, recouvrement
│   ├── RecursiveChunker.java                     CRÉÉ  T3  logique pure, sans Spring
│   ├── valueobject/
│   │   ├── Chunk.java                            CRÉÉ  T2  heading + text, contextualised()
│   │   └── DocumentStatus.java                   MODIF T6  READY
│   ├── entity/
│   │   ├── TextChunk.java                        CRÉÉ  T4  entité, colonne vector(1024)
│   │   └── Document.java                         MODIF T6  markIndexed()
│   ├── port/
│   │   ├── TokenCounter.java                     CRÉÉ  T1  String → int
│   │   └── TextChunkRepository.java              CRÉÉ  T4  saveAll / find / delete
│   ├── event/
│   │   └── DocumentTextIndexed.java              CRÉÉ  T6  knowledge.document-text.indexed
│   └── exception/
│       ├── DocumentProcessingException.java      CRÉÉ  T5  mère des refus de traitement
│       ├── DocumentExtractionException.java      MODIF T5  change de parent
│       └── EmbeddingUnavailableException.java    MODIF T5  change de parent
├── application/command/
│   ├── IndexDocumentText.java                    CRÉÉ  T6  documentId + ownerId
│   ├── IndexDocumentTextHandler.java             CRÉÉ  T6  découpe, vectorise, écrit
│   ├── MarkDocumentProcessingFailed.java         CRÉÉ  T5  remplace …ExtractionFailed
│   └── MarkDocumentProcessingFailedHandler.java  CRÉÉ  T5  remplace …ExtractionFailedHandler
└── infrastructure/
    ├── ai/
    │   └── JtokkitTokenCounter.java              CRÉÉ  T1  cl100k_base, package-private
    ├── persistence/
    │   ├── JpaTextChunkRepositoryAdapter.java    CRÉÉ  T4  adapter du port
    │   └── SpringDataTextChunkRepository.java    CRÉÉ  T4  package-private
    └── messaging/
        ├── KnowledgeEventListener.java           MODIF T5 (motif), T6 (handler indexed),
        │                                                T7 (dispatch)
        └── KnowledgeMessagingConfiguration.java  MODIF T6  déclare l'événement

src/main/resources/db/migration/
└── V10__create_knowledge_text_chunks.sql         CRÉÉ  T4  table + index HNSW

src/test/java/xyz/sterenn/secondbrain/knowledge/
├── KnowledgeFixture.java                         MODIF T4  unVecteur()
├── ConstantEmbeddingPortConfiguration.java       CRÉÉ  T6  doublure @Primary du port
├── domain/
│   ├── RecursiveChunkerTest.java                 CRÉÉ  T3  pur, compteur « un mot un token »
│   ├── valueobject/ChunkTest.java                CRÉÉ  T2  pur
│   └── exception/                                CRÉÉ  T5  filiation des refus
│       └── DocumentProcessingExceptionTest.java
├── application/command/
│   ├── IndexDocumentTextTest.java                CRÉÉ  T6  intégration, par le bus
│   ├── DeleteDocumentCascadeTest.java            MODIF T4  la cascade emporte les extraits
│   └── MarkDocumentProcessingFailedTest.java     RENOMMÉ T5 depuis …ExtractionFailedTest
└── infrastructure/
    ├── ai/
    │   ├── JtokkitTokenCounterTest.java         CRÉÉ  T1  le vrai tokenizer
    │   └── RecursiveChunkerWithJtokkitTest.java CRÉÉ  T3  le découpage à la vraie toise
    ├── persistence/
    │   └── JpaTextChunkRepositoryAdapterTest.java CRÉÉ T4  par le port, vecteur aller-retour
    └── messaging/KnowledgeEventListenerTest.java  MODIF T7  READY, extraits, panne Ollama

frontend/src/
├── components/DocumentStatusTag.vue              MODIF T8  libellé et sévérité de READY
└── views/DesignSystemView.vue                    MODIF T8  READY au catalogue

gradle/libs.versions.toml                         MODIF T1 (jtokkit), T4 (hibernate-vector)
build.gradle.kts                                  MODIF T1, T4
CLAUDE.md                                         MODIF T7  le flux, l'arborescence, la base
```

**Ce que ce plan ne fait pas, et c'est voulu :** aucune recherche vectorielle (RAG-8 écrira
la requête, ce plan n'écrit que l'index), aucun écran des extraits, aucune réextraction, et
aucun renommage de `Document.markExtractionFailed` — la spec ne renomme que la commande et
la famille d'exceptions, et un renommage de plus élargirait le diff sans rien apprendre.

---

## Tâche 1 : Le comptage des tokens passe par un port

`cl100k_base` est le tokenizer d'OpenAI ; `bge-m3` s'appuie sur un sentencepiece
XLM-RoBERTa. On mesure en pieds une étoffe vendue en mètres — sans danger, parce que
`cl100k` **sur-compte** le français et rend donc le plafond conservateur, mais un lecteur
futur a le droit de le savoir. D'où un port, et non un import de jtokkit dans le chunker.

**Fichiers :**
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/port/TokenCounter.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/ai/JtokkitTokenCounter.java`
- Modifier : `gradle/libs.versions.toml`
- Modifier : `build.gradle.kts`
- Test : `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/ai/JtokkitTokenCounterTest.java`

**Interfaces :**
- Consomme : rien.
- Produit :
  - `TokenCounter.count(String text) → int` — interface fonctionnelle, `0` pour `null` ou
    vide, jamais d'exception
  - `JtokkitTokenCounter` — `@Component` **package-private**, implémente `TokenCounter`

- [ ] **Étape 1 : Ajouter jtokkit au version catalog**

Dans `gradle/libs.versions.toml`, section `[versions]`, sous `pdfbox` :

```toml
# Comptage de tokens : jtokkit est la seule implémentation Java des BPE d'OpenAI, et
# `cl100k_base` la toise que le ticket impose. Aucun BOM ne la porte.
jtokkit = "1.1.0"
```

Section `[libraries]`, sous `pdfbox` :

```toml
# Tokenizer : jtokkit rend `cl100k_base` sans réseau ni modèle à télécharger — les tables
# BPE sont dans le jar. Ce n'est PAS le tokenizer de bge-m3 (voir TokenCounter) ; c'est un
# proxy conservateur, et c'est la raison d'être du port.
jtokkit = { module = "com.knuddels:jtokkit", version.ref = "jtokkit" }
```

- [ ] **Étape 2 : Déclarer la dépendance**

Dans `build.gradle.kts`, juste après le bloc des extracteurs (`libs.pdfbox`) :

```kotlin
    // Comptage de tokens pour le découpage. Derrière le port TokenCounter : le domaine
    // compte, il ne sait pas avec quelle toise.
    implementation(libs.jtokkit)
```

- [ ] **Étape 3 : Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/ai/JtokkitTokenCounterTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.port.TokenCounter;

/**
 * Le vrai tokenizer, sans Spring : la classe n'a aucune dépendance à injecter.
 *
 * <p>Les assertions sont des <strong>relations</strong>, jamais des nombres attendus. Un
 * comptage exact figerait la table BPE de jtokkit dans le test : la moindre montée de
 * version le ferait rougir sans qu'aucune règle du projet n'ait bougé. Ce qui compte ici est
 * la propriété sur laquelle le plafond s'appuie — {@code cl100k} sur-compte le français,
 * donc un extrait sous le plafond l'est aussi pour {@code bge-m3}.
 */
class JtokkitTokenCounterTest {

    private final TokenCounter tokenCounter = new JtokkitTokenCounter();

    @Test
    void ne_compte_rien_dans_un_texte_absent_ou_vide() {
        assertThat(tokenCounter.count(null)).isZero();
        assertThat(tokenCounter.count("")).isZero();
    }

    @Test
    void compte_au_moins_un_token_par_mot() {
        assertThat(tokenCounter.count("Bonjour")).isPositive();
    }

    @Test
    void compte_plus_de_tokens_que_de_mots_sur_du_francais_accentue() {
        // La propriété qui rend le plafond conservateur : les mots français accentués se
        // découpent en plusieurs tokens `cl100k`. Un extrait de 800 tokens comptés ici reste
        // très en deçà des 8192 que bge-m3 accepte.
        String texte = "L'élève déchiffrait péniblement les hiéroglyphes gravés sur la stèle funéraire. "
                .repeat(20);
        int mots = texte.strip().split("\\s+").length;

        assertThat(tokenCounter.count(texte)).isGreaterThan(mots);
    }

    @Test
    void compte_davantage_un_texte_plus_long() {
        String phrase = "Le chat dort sur le tapis.";

        assertThat(tokenCounter.count(phrase.repeat(3))).isGreaterThan(tokenCounter.count(phrase));
    }
}
```

- [ ] **Étape 4 : Lancer le test, vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.ai.JtokkitTokenCounterTest"
```

Attendu : ÉCHEC de compilation, `cannot find symbol: class TokenCounter`.

- [ ] **Étape 5 : Écrire le port**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/port/TokenCounter.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.port;

/**
 * Port sortant vers la toise qui mesure un texte en tokens.
 *
 * <p><strong>Pourquoi un port pour trois lignes de jtokkit.</strong> Le ticket impose
 * {@code cl100k_base}, qui est le tokenizer d'OpenAI — et {@code bge-m3} n'en est pas un : il
 * s'appuie sur le sentencepiece XLM-RoBERTa, dont le découpage du français est sensiblement
 * différent. On mesure en pieds une étoffe vendue en mètres. C'est sans danger, parce que
 * {@code cl100k} sur-compte le français : un extrait de 800 « tokens cl100k » reste très en
 * deçà des 8192 que le modèle accepte, et le plafond ne peut donc pas être dépassé par
 * surprise. Mais la cible est un <strong>proxy</strong>, pas une mesure, et deux choses en
 * découlent : le jour où le modèle change, la toise change sans qu'on touche au découpage ;
 * et les tests du découpage prennent un compteur « un mot égale un token », ce qui rend les
 * frontières d'extraits lisibles dans les assertions au lieu d'être des nombres magiques.
 *
 * <p>Interface fonctionnelle de fait : un test la satisfait par une lambda.
 */
public interface TokenCounter {

    /**
     * @return le nombre de tokens du texte ; {@code 0} pour un texte absent ou vide. Ne lève
     *     jamais : compter n'est pas un refus métier, et un compteur qui échouerait ferait
     *     échouer un découpage pour une raison que l'utilisateur ne pourrait pas corriger.
     */
    int count(String text);
}
```

- [ ] **Étape 6 : Écrire l'adapter**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/ai/JtokkitTokenCounter.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.port.TokenCounter;

/**
 * Adapter jtokkit du port {@link TokenCounter}, en {@code cl100k_base}.
 *
 * <p>Aucun réseau, aucun modèle à télécharger : les tables BPE voyagent dans le jar. Le
 * registre est <em>paresseux</em> — il ne charge que l'encodage demandé, là où le registre
 * par défaut les charge tous, dont ceux dont ce projet n'a que faire.
 *
 * <p>L'{@link Encoding} est construit une fois : il est immuable et sûr en accès concurrent,
 * et le construire à chaque appel relirait les tables BPE pour chaque paragraphe d'un
 * document.
 *
 * <p>Package-private : rien au-dehors ne doit dépendre d'autre chose que du port. Voisin de
 * {@code OllamaEmbeddingAdapter} parce que les deux servent le même modèle — l'un le mesure,
 * l'autre l'interroge.
 */
@Component
class JtokkitTokenCounter implements TokenCounter {

    private final Encoding encoding =
            Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    @Override
    public int count(String text) {
        return text == null || text.isEmpty() ? 0 : encoding.countTokens(text);
    }
}
```

- [ ] **Étape 7 : Lancer le test, vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.ai.JtokkitTokenCounterTest"
```

Attendu : SUCCÈS, quatre tests verts.

- [ ] **Étape 8 : Formater et committer**

```bash
make format-back
git add gradle/libs.versions.toml build.gradle.kts \
        src/main/java/xyz/sterenn/secondbrain/knowledge/domain/port/TokenCounter.java \
        src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/ai/JtokkitTokenCounter.java \
        src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/ai/JtokkitTokenCounterTest.java
git commit -m "feat: un port mesure un texte en tokens, jtokkit tient la toise"
```

---

## Tâche 2 : L'extrait est un objet-valeur qui sait se présenter

`Chunk` porte un titre de section et un corps. **Sa position n'est pas un champ** : elle
appartient à la liste, comme celle d'un `TextBlock` appartient à l'`@OrderColumn` de son
extraction. Un extrait sorti de son document reste le même extrait.

**Fichiers :**
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/Chunk.java`
- Test : `src/test/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/ChunkTest.java`

**Interfaces :**
- Consomme : rien de la tâche 1.
- Produit :
  - `new Chunk(String heading, String text)` — record ; `heading` obligatoire mais
    éventuellement vide, `text` obligatoire et non vide une fois dépouillé ; les deux sont
    `strip()`és à la construction
  - `Chunk.heading() → String`, `Chunk.text() → String`
  - `Chunk.contextualised(String filename) → String` — `Document: <nom> — Section: <titre>`,
    deux sauts de ligne, puis le corps ; sans le segment `Section` quand le titre est vide

- [ ] **Étape 1 : Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/ChunkTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class ChunkTest {

    @Test
    void porte_le_titre_de_sa_section_et_son_corps() {
        Chunk extrait = new Chunk("Introduction", "Le corps de la section.");

        assertThat(extrait.heading()).isEqualTo("Introduction");
        assertThat(extrait.text()).isEqualTo("Le corps de la section.");
    }

    @Test
    void accepte_un_extrait_sans_titre() {
        assertThat(new Chunk("", "Un document sans titre.").heading()).isEmpty();
    }

    @Test
    void refuse_un_extrait_sans_corps() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Chunk("Introduction", "   "));
    }

    @Test
    void refuse_un_titre_absent() {
        // Vide, oui ; absent, non. Un consommateur qui préfixe ses extraits n'a pas à
        // distinguer deux formes d'absence — c'est déjà la règle de TextBlock.
        assertThatNullPointerException().isThrownBy(() -> new Chunk(null, "Un corps."));
    }

    @Test
    void deux_extraits_de_meme_contenu_sont_egaux() {
        assertThat(new Chunk("Titre", "Un corps.")).isEqualTo(new Chunk("Titre", "Un corps."));
    }

    @Test
    void se_presente_avec_son_document_et_sa_section() {
        Chunk extrait = new Chunk("Introduction", "Le corps de la section.");

        assertThat(extrait.contextualised("rapport.pdf"))
                .isEqualTo("Document: rapport.pdf — Section: Introduction\n\nLe corps de la section.");
    }

    @Test
    void se_presente_avec_son_seul_document_quand_la_section_n_a_pas_de_titre() {
        Chunk extrait = new Chunk("", "Le corps de la section.");

        assertThat(extrait.contextualised("rapport.pdf"))
                .isEqualTo("Document: rapport.pdf\n\nLe corps de la section.");
    }

    @Test
    void refuse_de_se_presenter_sans_nom_de_document() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Chunk("Introduction", "Un corps.").contextualised(null));
    }
}
```

- [ ] **Étape 2 : Lancer le test, vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.valueobject.ChunkTest"
```

Attendu : ÉCHEC de compilation, `cannot find symbol: class Chunk`.

- [ ] **Étape 3 : Écrire l'objet-valeur**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/Chunk.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Objects;

/**
 * Un extrait de document : le corps d'un morceau de section, et le titre de la section d'où
 * il vient.
 *
 * <p>Objet-valeur, exactement comme {@link ExtractedText} l'est pour l'extraction : la
 * logique pure de découpage produit ceci, et c'est le handler qui en fait des entités
 * {@code TextChunk}. Le découpage n'a pas à savoir qu'il existe une base, et un objet-valeur
 * se compare par ses champs — ce qui rend les assertions de test lisibles.
 *
 * <p><strong>La position n'est pas un champ.</strong> Elle appartient à la liste, comme celle
 * d'un {@link TextBlock} appartient à l'{@code @OrderColumn} de son extraction : un extrait
 * sorti de son document reste le même extrait.
 *
 * <p><strong>Le niveau de titre non plus.</strong> Le préfixe n'en a que faire, et le niveau
 * reste lisible dans l'extraction, qui n'est jamais effacée : reconstruire plus tard un
 * chemin de section (« Chapitre 1 &gt; Introduction ») se fera depuis là, sans avoir à le
 * recopier dans chaque extrait.
 */
public record Chunk(String heading, String text) {

    public Chunk {
        Objects.requireNonNull(heading, "Le titre de section est obligatoire, vide s'il n'y en a pas");
        Objects.requireNonNull(text, "Le texte de l'extrait est obligatoire");
        heading = heading.strip();
        text = text.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Un extrait sans texte n'en est pas un : il ne se construit pas");
        }
    }

    /**
     * Le texte tel qu'il part au service de vectorisation : précédé de ce qui dit d'où il
     * vient.
     *
     * <p><strong>C'est la seule méthode qui connaisse la forme du préfixe</strong>, et elle
     * servira aussi à alimenter le prompt de RAG-9. Ce qui est <em>stocké</em>, en revanche,
     * est le corps nu : un extrait préfixé montré tel quel à l'écran est du balisage sous les
     * yeux, changer la forme du préfixe plus tard ne doit pas demander de réécrire la base,
     * et la provenance est déjà dite par les colonnes {@code heading} et {@code document_id}
     * aussi bien que par une chaîne recopiée.
     *
     * <p>Ce que ça suppose, et qui est vrai aujourd'hui : <strong>aucune route ne renomme un
     * document.</strong> L'identité d'un document est son empreinte, son nom n'est qu'une
     * étiquette. Le jour où un renommage existerait, la chaîne recalculée cesserait de
     * correspondre au vecteur stocké — ce serait à revectoriser.
     */
    public String contextualised(String filename) {
        Objects.requireNonNull(filename, "Le nom du document est obligatoire");
        String prefixe = heading.isEmpty() ? "Document: " + filename : "Document: " + filename + " — Section: " + heading;
        return prefixe + "\n\n" + text;
    }
}
```

- [ ] **Étape 4 : Lancer le test, vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.valueobject.ChunkTest"
```

Attendu : SUCCÈS, huit tests verts.

- [ ] **Étape 5 : Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/Chunk.java \
        src/test/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/ChunkTest.java
git commit -m "feat: un extrait porte sa section et sait dire d'où il vient"
```

---

## Tâche 3 : Le découpage récursif — sections, paragraphes, phrases

Le cœur du ticket, et **de la logique de domaine pure** : aucune dépendance à Spring, aucune
notion de base. Quatre niveaux de repli, dans cet ordre — une section qui tient sous le
plafond donne un extrait ; sinon on découpe aux paragraphes ; un paragraphe seul au-dessus du
plafond descend aux phrases ; une phrase seule au-dessus du plafond est coupée net, faute de
frontière.

**Fichiers :**
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/ChunkingPolicy.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/RecursiveChunker.java`
- Test : `src/test/java/xyz/sterenn/secondbrain/knowledge/domain/RecursiveChunkerTest.java`
- Test : `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/ai/RecursiveChunkerWithJtokkitTest.java`

**Interfaces :**
- Consomme : `TokenCounter.count(String) → int` (tâche 1), `new Chunk(String, String)` (tâche 2).
- Produit :
  - `ChunkingPolicy.TARGET_TOKENS = 600`, `ChunkingPolicy.MAX_TOKENS = 800`,
    `ChunkingPolicy.OVERLAP_TOKENS = 90`
  - `new RecursiveChunker(TokenCounter tokenCounter)`
  - `RecursiveChunker.chunk(ExtractedText text) → List<Chunk>` — jamais vide, jamais
    d'extrait au-dessus du plafond, un extrait au minimum par bloc

> **Deux tests, deux compteurs, et c'est voulu.** `RecursiveChunkerTest` emploie un compteur
> d'essai « un mot égale un token » : les frontières d'extraits y deviennent lisibles dans
> les assertions au lieu d'être des nombres magiques que personne ne saurait recalculer.
> `RecursiveChunkerWithJtokkitTest` refait le tour avec la vraie toise, pour ne pas ne
> vérifier que la doublure. Il vit dans le package de l'adapter parce que
> `JtokkitTokenCounter` est package-private — le rendre public pour un test serait payer le
> test au prix de la règle.

- [ ] **Étape 1 : Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/knowledge/domain/RecursiveChunkerTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.port.TokenCounter;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

/**
 * Le découpage, mesuré avec un compteur d'essai où <strong>un mot vaut un token</strong>.
 *
 * <p>C'est toute la raison d'être du port {@code TokenCounter} : avec cette toise, une phrase
 * de ce test pèse exactement dix tokens, la cible de 600 tombe sur la soixantième et le
 * recouvrement de 90 sur les neuf dernières. Les frontières se lisent, au lieu d'être des
 * nombres qu'il faudrait croire sur parole.
 *
 * <p>Aucun Spring : la classe testée est du domaine pur, et son unique dépendance est une
 * lambda.
 */
class RecursiveChunkerTest {

    /** Un mot, un token. La doublure qui rend les frontières lisibles. */
    private static final TokenCounter UN_MOT_UN_TOKEN =
            texte -> texte == null || texte.isBlank() ? 0 : texte.strip().split("\\s+").length;

    private final RecursiveChunker chunker = new RecursiveChunker(UN_MOT_UN_TOKEN);

    @Test
    void refuse_un_texte_absent() {
        assertThatNullPointerException().isThrownBy(() -> chunker.chunk(null));
    }

    @Test
    void un_document_plus_court_qu_un_extrait_donne_un_seul_extrait() {
        // Le troisième scénario du ticket : une centaine de mots, sans titre.
        ExtractedText texte = ExtractedText.untitled(paragraphe(1, 10));

        assertThat(chunker.chunk(texte)).hasSize(1);
    }

    @Test
    void une_section_sous_le_plafond_donne_un_extrait_meme_au_dessus_de_la_cible() {
        // 70 phrases : 700 tokens, au-dessus de la cible (600) mais sous le plafond (800).
        // On ne coupe pas un bloc déjà valide pour se rapprocher de la cible.
        ExtractedText texte = ExtractedText.untitled(paragraphe(1, 70));

        assertThat(chunker.chunk(texte)).hasSize(1);
    }

    @Test
    void aucun_extrait_ne_depasse_le_plafond() {
        // Le premier scénario du ticket. 200 phrases : 2000 tokens.
        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(paragraphe(1, 200)));

        assertThat(extraits).hasSizeGreaterThan(1);
        assertThat(extraits).allSatisfy(extrait ->
                assertThat(UN_MOT_UN_TOKEN.count(extrait.text())).isLessThanOrEqualTo(ChunkingPolicy.MAX_TOKENS));
    }

    @Test
    void aucun_extrait_ne_commence_ni_ne_finit_au_milieu_d_une_phrase() {
        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(paragraphe(1, 200)));

        assertThat(extraits).allSatisfy(extrait -> {
            assertThat(extrait.text()).endsWith(".");
            assertThat(extrait.text()).startsWith("Phrase numero ");
        });
    }

    @Test
    void deux_extraits_consecutifs_d_une_section_se_recouvrent() {
        // Le deuxième scénario du ticket : une section plus longue qu'un extrait.
        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(paragraphe(1, 200)));

        String premierePhraseDuSecond = extraits.get(1).text().split("(?<=\\.)\\s+")[0];
        assertThat(extraits.get(0).text()).contains(premierePhraseDuSecond);
    }

    @Test
    void le_recouvrement_ne_franchit_pas_une_frontiere_de_section() {
        // Deux titres, deux sections : un recouvrement à cheval ferait mentir le préfixe de
        // l'extrait suivant, qui annoncerait une section dont il ne contient pas le début.
        ExtractedText texte = new ExtractedText(List.of(
                TextBlock.of("Section A", 1, marque("Alpha", 200)),
                TextBlock.of("Section B", 1, marque("Beta", 200))));

        List<Chunk> extraits = chunker.chunk(texte);

        assertThat(extraits)
                .filteredOn(extrait -> extrait.heading().equals("Section B"))
                .allSatisfy(extrait -> assertThat(extrait.text()).doesNotContain("Alpha"));
    }

    @Test
    void chaque_extrait_porte_le_titre_de_la_section_dont_il_vient() {
        // Le quatrième scénario du ticket, côté domaine : lu isolément, un extrait dit de
        // quelle section il provient. Le document, lui, est dit par la ligne qui le range.
        ExtractedText texte = new ExtractedText(List.of(
                TextBlock.of("Introduction", 1, paragraphe(1, 200)), TextBlock.of("Conclusion", 1, paragraphe(1, 5))));

        List<Chunk> extraits = chunker.chunk(texte);

        assertThat(extraits).extracting(Chunk::heading).contains("Introduction", "Conclusion");
        assertThat(extraits).last().satisfies(extrait -> assertThat(extrait.heading()).isEqualTo("Conclusion"));
    }

    @Test
    void decoupe_aux_paragraphes_avant_de_descendre_aux_phrases() {
        // Trois paragraphes de 300 tokens : 900 au total, donc la section se découpe. La
        // frontière de paragraphe survit dans les extraits — c'est le double saut de ligne
        // que TextBlock.normalise garantit.
        String corps = paragraphe(1, 30) + "\n\n" + paragraphe(31, 30) + "\n\n" + paragraphe(61, 30);

        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(corps));

        assertThat(extraits).hasSizeGreaterThan(1);
        assertThat(extraits.get(0).text()).contains("\n\n");
    }

    @Test
    void un_paragraphe_geant_sans_ponctuation_est_coupe_faute_de_frontiere() {
        // Le scénario imposé par le transport : un texte qui n'offre aucune frontière ne peut
        // pas en imposer une. C'est le seul endroit où la promesse « jamais au milieu d'une
        // phrase » cède, et elle y est forcée.
        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled("mot ".repeat(3000)));

        assertThat(extraits).hasSizeGreaterThan(1);
        assertThat(extraits).allSatisfy(extrait ->
                assertThat(UN_MOT_UN_TOKEN.count(extrait.text())).isLessThanOrEqualTo(ChunkingPolicy.MAX_TOKENS));
    }

    @Test
    void le_recouvrement_cede_devant_le_plafond() {
        // 60 phrases (600 tokens), puis une seule phrase de 800 tokens. Reprendre la moindre
        // phrase la ferait passer au-dessus du plafond : le recouvrement est abandonné en
        // entier, et l'extrait est la phrase géante, seule. Sans cette règle, ce serait
        // l'unique extrait hors plafond de tout l'algorithme.
        String phraseGeante = "Mot " + "mot ".repeat(798) + "final.";

        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(paragraphe(1, 60) + " " + phraseGeante));

        assertThat(extraits).hasSize(2);
        assertThat(extraits.get(1).text()).isEqualTo(phraseGeante.strip());
    }

    /** Une phrase de dix mots, numérotée : dix tokens pour le compteur d'essai. */
    private static String phrase(int numero) {
        return "Phrase numero " + numero + " avec quelques mots pour occuper la place.";
    }

    /** Un paragraphe de {@code nombre} phrases, donc de dix fois autant de tokens. */
    private static String paragraphe(int premiere, int nombre) {
        return IntStream.range(0, nombre)
                .mapToObj(index -> phrase(premiere + index))
                .collect(Collectors.joining(" "));
    }

    /** Le même paragraphe, mais dont chaque phrase porte une marque reconnaissable. */
    private static String marque(String marque, int nombre) {
        return IntStream.range(0, nombre)
                .mapToObj(index -> marque + " numero " + index + " avec quelques mots pour occuper la place.")
                .collect(Collectors.joining(" "));
    }
}
```

- [ ] **Étape 2 : Lancer le test, vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.RecursiveChunkerTest"
```

Attendu : ÉCHEC de compilation, `cannot find symbol: class RecursiveChunker`.

- [ ] **Étape 3 : Écrire la règle**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/ChunkingPolicy.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain;

/**
 * Ce que le domaine tient pour un extrait de bonne taille.
 *
 * <p>Règle métier pure, statique, sans dépendance — à la racine du domaine, à côté
 * d'{@link ExtractionPolicy} et d'{@link EmbeddingPolicy} : elle se teste sans Spring.
 *
 * <p><strong>Ces trois nombres sont comptés avec la toise d'un autre.</strong> Le comptage
 * passe par {@code TokenCounter}, dont l'unique adapter emploie {@code cl100k_base}, le
 * tokenizer d'OpenAI — {@code bge-m3} s'appuie sur un sentencepiece XLM-RoBERTa. C'est sans
 * danger parce que {@code cl100k} sur-compte le français : un extrait de 800 tokens comptés
 * ici reste très en deçà des 8192 que le modèle accepte. Mais 600 est un <em>proxy</em>, pas
 * une mesure.
 */
public final class ChunkingPolicy {

    /**
     * Ce vers quoi l'accumulation tend. <strong>Elle gouverne l'accumulation, pas la découpe
     * d'un bloc déjà valide</strong> : une section de 700 tokens n'est pas coupée pour se
     * rapprocher de la cible, elle donne un extrait.
     */
    public static final int TARGET_TOKENS = 600;

    /**
     * Ce qu'aucun extrait ne dépasse — le premier scénario du ticket. Le recouvrement cède
     * devant lui, et une phrase qui le franchit à elle seule est coupée net.
     */
    public static final int MAX_TOKENS = 800;

    /**
     * Ce que deux extraits consécutifs d'une même section partagent, soit 15 % de la cible.
     * Il se prend en <strong>phrases entières</strong> : une fenêtre glissante de tokens
     * reproduirait à la jointure exactement la coupure que le reste de l'algorithme évite.
     */
    public static final int OVERLAP_TOKENS = 90;

    private ChunkingPolicy() {
        // règle métier, pas un objet
    }
}
```

- [ ] **Étape 4 : Écrire le découpage**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/RecursiveChunker.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import xyz.sterenn.secondbrain.knowledge.domain.port.TokenCounter;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

/**
 * Découpe un texte extrait en extraits vectorisables. <strong>Logique de domaine pure</strong> :
 * ni Spring, ni base, ni réseau — sa seule dépendance est le port {@link TokenCounter}, et
 * c'est le handler qui l'instancie.
 *
 * <p>Quatre niveaux de repli, dans cet ordre :
 *
 * <ol>
 *   <li><strong>Une section qui tient sous le plafond donne un extrait.</strong> On ne coupe
 *       pas un bloc de 700 tokens pour se rapprocher de la cible.
 *   <li><strong>Sinon, découpe en paragraphes</strong>, sur la double ligne vide. Ce n'est pas
 *       une heuristique : {@code TextBlock.normalise} garantit qu'une frontière de paragraphe
 *       survit sous la forme d'exactement deux sauts de ligne.
 *   <li><strong>Un paragraphe seul au-dessus du plafond descend aux phrases</strong>, par
 *       {@link BreakIterator} en français — le JDK, zéro dépendance. Une expression régulière
 *       sur {@code [.!?]} couperait « 3.14 », « etc. » et « M. Dupont » ; {@code BreakIterator}
 *       se trompe aussi, moins souvent, et sa panne est bénigne : une fausse frontière produit
 *       un extrait un peu court, jamais un extrait cassé.
 *   <li><strong>Une phrase seule au-dessus du plafond est coupée net</strong>, aux mots puis,
 *       s'il n'y a même plus de mot (un blob sans espace), aux caractères. C'est le seul
 *       endroit où la promesse « jamais au milieu d'une phrase » cède, et elle y est forcée :
 *       un texte qui n'offre aucune frontière ne peut pas en imposer une.
 * </ol>
 *
 * <p><strong>Le recouvrement ne franchit jamais une frontière de section</strong>, et il cède
 * devant le plafond : c'est un confort, le plafond est un invariant. Sans cette dernière
 * règle, un recouvrement suivi d'une longue phrase produirait l'unique extrait hors plafond
 * de tout l'algorithme.
 *
 * <p>Un cas limite tombe tout seul : une section vide ne peut pas arriver ici, {@code
 * TextBlock.of} refusant un corps vide et {@code ExtractedTextBuilder} écartant les sections
 * sans corps.
 */
public final class RecursiveChunker {

    private static final String PARAGRAPH_SEPARATOR = "\n\n";
    private static final String SENTENCE_SEPARATOR = " ";

    /** Deux sauts de ligne ou plus : ce que {@code TextBlock.normalise} laisse d'un paragraphe. */
    private static final String PARAGRAPH_BOUNDARY = "\n{2,}";

    private static final String WHITESPACE = "\\s+";

    private final TokenCounter tokenCounter;

    public RecursiveChunker(TokenCounter tokenCounter) {
        this.tokenCounter = Objects.requireNonNull(tokenCounter, "Le compteur de tokens est obligatoire");
    }

    /**
     * @return au moins un extrait par bloc, dans l'ordre du document, aucun au-dessus du
     *     plafond. Jamais vide : {@link ExtractedText} garantit au moins un bloc.
     */
    public List<Chunk> chunk(ExtractedText text) {
        Objects.requireNonNull(text, "Le texte extrait est obligatoire");
        List<Chunk> extraits = new ArrayList<>();
        for (TextBlock bloc : text.blocks()) {
            extraits.addAll(chunkSection(bloc.getHeading(), bloc.getText()));
        }
        return List.copyOf(extraits);
    }

    private List<Chunk> chunkSection(String heading, String body) {
        if (tokenCounter.count(body) <= ChunkingPolicy.MAX_TOKENS) {
            return List.of(new Chunk(heading, body));
        }
        List<Chunk> extraits = new ArrayList<>();
        String courant = "";
        for (Unit unite : units(body)) {
            if (courant.isEmpty()) {
                courant = unite.text();
                continue;
            }
            String candidat = courant + unite.separator() + unite.text();
            if (tokenCounter.count(candidat) <= ChunkingPolicy.TARGET_TOKENS) {
                courant = candidat;
                continue;
            }
            extraits.add(new Chunk(heading, courant));
            courant = openWithOverlap(courant, unite);
        }
        if (!courant.isEmpty()) {
            extraits.add(new Chunk(heading, courant));
        }
        return extraits;
    }

    /**
     * Ouvre l'extrait suivant sur les dernières phrases du précédent — et en reprend moins,
     * jusqu'à zéro s'il le faut, plutôt que de franchir le plafond.
     */
    private String openWithOverlap(String precedent, Unit suivante) {
        List<String> reprises = new ArrayList<>(trailingSentences(precedent));
        while (!reprises.isEmpty()
                && tokenCounter.count(join(reprises) + suivante.separator() + suivante.text())
                        > ChunkingPolicy.MAX_TOKENS) {
            reprises.removeFirst();
        }
        return reprises.isEmpty() ? suivante.text() : join(reprises) + suivante.separator() + suivante.text();
    }

    /** Les dernières phrases entières qui tiennent dans le recouvrement, dans l'ordre. */
    private List<String> trailingSentences(String texte) {
        List<String> phrases = sentences(texte);
        List<String> reprises = new ArrayList<>();
        int total = 0;
        for (int index = phrases.size() - 1; index >= 0; index--) {
            int cout = tokenCounter.count(phrases.get(index));
            if (total + cout > ChunkingPolicy.OVERLAP_TOKENS) {
                break;
            }
            reprises.addFirst(phrases.get(index));
            total += cout;
        }
        return reprises;
    }

    /**
     * Les morceaux insécables d'une section, chacun avec le séparateur qui le raccroche au
     * précédent : deux sauts de ligne pour un début de paragraphe, une espace pour une phrase
     * au sein d'un paragraphe. Sans ce séparateur porté par le morceau, recoller deux unités
     * effacerait la frontière de paragraphe que l'extraction a pris soin de conserver.
     */
    private List<Unit> units(String body) {
        List<Unit> unites = new ArrayList<>();
        for (String paragraphe : body.split(PARAGRAPH_BOUNDARY)) {
            String bloc = paragraphe.strip();
            if (bloc.isEmpty()) {
                continue;
            }
            if (tokenCounter.count(bloc) <= ChunkingPolicy.MAX_TOKENS) {
                unites.add(new Unit(bloc, PARAGRAPH_SEPARATOR));
                continue;
            }
            String separateur = PARAGRAPH_SEPARATOR;
            for (String phrase : sentences(bloc)) {
                for (String morceau : forceSplit(phrase)) {
                    unites.add(new Unit(morceau, separateur));
                    separateur = SENTENCE_SEPARATOR;
                }
            }
        }
        return unites;
    }

    /** Dernier recours : au mot, puis au caractère pour un mot qui pèse à lui seul plus que le plafond. */
    private List<String> forceSplit(String phrase) {
        if (tokenCounter.count(phrase) <= ChunkingPolicy.MAX_TOKENS) {
            return List.of(phrase);
        }
        List<String> morceaux = new ArrayList<>();
        String courant = "";
        for (String mot : phrase.split(WHITESPACE)) {
            if (tokenCounter.count(mot) > ChunkingPolicy.MAX_TOKENS) {
                if (!courant.isEmpty()) {
                    morceaux.add(courant);
                    courant = "";
                }
                morceaux.addAll(splitOnCharacters(mot));
                continue;
            }
            String candidat = courant.isEmpty() ? mot : courant + SENTENCE_SEPARATOR + mot;
            if (tokenCounter.count(candidat) > ChunkingPolicy.MAX_TOKENS) {
                morceaux.add(courant);
                courant = mot;
            } else {
                courant = candidat;
            }
        }
        if (!courant.isEmpty()) {
            morceaux.add(courant);
        }
        return morceaux;
    }

    /**
     * Un « mot » plus lourd que le plafond — une chaîne encodée, un fichier collé dans un
     * document. Il n'y a plus aucune frontière : on estime la longueur à couper par la
     * densité de tokens du reste, puis on la resserre tant qu'elle dépasse. C'est le seul
     * chemin qui ne préserve rien du tout, et c'est ce qui garantit qu'<em>aucun</em> extrait
     * ne franchit le plafond.
     */
    private List<String> splitOnCharacters(String mot) {
        List<String> morceaux = new ArrayList<>();
        String reste = mot;
        while (tokenCounter.count(reste) > ChunkingPolicy.MAX_TOKENS) {
            int taille = Math.max(1, reste.length() * ChunkingPolicy.MAX_TOKENS / tokenCounter.count(reste));
            while (taille > 1 && tokenCounter.count(reste.substring(0, taille)) > ChunkingPolicy.MAX_TOKENS) {
                taille = taille * 3 / 4;
            }
            morceaux.add(reste.substring(0, taille));
            reste = reste.substring(taille);
        }
        if (!reste.isEmpty()) {
            morceaux.add(reste);
        }
        return morceaux;
    }

    /**
     * Les phrases d'un texte, par le {@link BreakIterator} du JDK en français. Une nouvelle
     * instance à chaque appel : {@code BreakIterator} porte l'état de son parcours, il n'est
     * pas sûr en accès concurrent, et le chunker est instancié une fois pour tout le worker.
     */
    private static List<String> sentences(String texte) {
        BreakIterator frontieres = BreakIterator.getSentenceInstance(Locale.FRENCH);
        frontieres.setText(texte);
        List<String> phrases = new ArrayList<>();
        int debut = frontieres.first();
        for (int fin = frontieres.next(); fin != BreakIterator.DONE; debut = fin, fin = frontieres.next()) {
            String phrase = texte.substring(debut, fin).strip();
            if (!phrase.isEmpty()) {
                phrases.add(phrase);
            }
        }
        return phrases;
    }

    private static String join(List<String> phrases) {
        return String.join(SENTENCE_SEPARATOR, phrases);
    }

    /** Un morceau insécable et ce qui le raccroche au précédent. */
    private record Unit(String text, String separator) {}
}
```

- [ ] **Étape 5 : Lancer le test, vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.RecursiveChunkerTest"
```

Attendu : SUCCÈS, onze tests verts.

- [ ] **Étape 6 : Écrire le test qui emploie la vraie toise**

Créer `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/ai/RecursiveChunkerWithJtokkitTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.ChunkingPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.RecursiveChunker;
import xyz.sterenn.secondbrain.knowledge.domain.port.TokenCounter;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;

/**
 * Le découpage mesuré par la <strong>vraie</strong> toise, pour ne pas ne vérifier que la
 * doublure de {@code RecursiveChunkerTest}.
 *
 * <p>Il vit dans le package de l'adapter et non dans celui du domaine : {@code
 * JtokkitTokenCounter} est package-private, et le rendre public pour un test serait payer le
 * test au prix de la règle.
 */
class RecursiveChunkerWithJtokkitTest {

    private final TokenCounter tokenCounter = new JtokkitTokenCounter();
    private final RecursiveChunker chunker = new RecursiveChunker(tokenCounter);

    @Test
    void aucun_extrait_de_texte_francais_ne_depasse_le_plafond() {
        String texte = IntStream.range(0, 400)
                .mapToObj(index -> "L'élève déchiffrait péniblement les hiéroglyphes gravés sur la stèle numéro "
                        + index + ".")
                .collect(Collectors.joining(" "));

        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(texte));

        assertThat(extraits).hasSizeGreaterThan(1);
        assertThat(extraits).allSatisfy(extrait ->
                assertThat(tokenCounter.count(extrait.text())).isLessThanOrEqualTo(ChunkingPolicy.MAX_TOKENS));
    }

    @Test
    void coupe_au_caractere_un_bloc_sans_espace_ni_ponctuation() {
        // Le cas où il ne reste aucune frontière du tout : un contenu encodé collé dans un
        // document. Le compteur d'essai de RecursiveChunkerTest ne peut pas l'atteindre — un
        // seul « mot » y vaut un seul token.
        String blob = "QWxvcnNRdWVMZURvY3VtZW50TmVQb3J0ZUF1Y3VuZUZyb250aWVyZQ".repeat(400);

        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(blob));

        assertThat(extraits).hasSizeGreaterThan(1);
        assertThat(extraits).allSatisfy(extrait ->
                assertThat(tokenCounter.count(extrait.text())).isLessThanOrEqualTo(ChunkingPolicy.MAX_TOKENS));
    }
}
```

- [ ] **Étape 7 : Lancer les deux tests, vérifier qu'ils passent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.RecursiveChunkerTest" \
           --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.ai.RecursiveChunkerWithJtokkitTest"
```

Attendu : SUCCÈS, treize tests verts.

- [ ] **Étape 8 : Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/knowledge/domain/ChunkingPolicy.java \
        src/main/java/xyz/sterenn/secondbrain/knowledge/domain/RecursiveChunker.java \
        src/test/java/xyz/sterenn/secondbrain/knowledge/domain/RecursiveChunkerTest.java \
        src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/ai/RecursiveChunkerWithJtokkitTest.java
git commit -m "feat: le domaine découpe un texte en sections, paragraphes et phrases"
```

---

## Tâche 4 : L'extrait vectorisé prend sa table

Une table, une ligne par extrait, **le vecteur en colonne** : c'est du un-pour-un, né en même
temps et effacé en même temps. Deux tables imposeraient une jointure sur le chemin chaud de la
recherche que RAG-8 écrira. La table porte le nom de sa **typologie**, comme celles de
l'extraction — ADR-0030.

**Fichiers :**
- Modifier : `gradle/libs.versions.toml`
- Modifier : `build.gradle.kts`
- Créer : `src/main/resources/db/migration/V10__create_knowledge_text_chunks.sql`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/entity/TextChunk.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/port/TextChunkRepository.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/SpringDataTextChunkRepository.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/JpaTextChunkRepositoryAdapter.java`
- Modifier : `src/test/java/xyz/sterenn/secondbrain/knowledge/KnowledgeFixture.java`
- Modifier : `src/test/java/xyz/sterenn/secondbrain/knowledge/application/command/DeleteDocumentCascadeTest.java`
- Test : `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/JpaTextChunkRepositoryAdapterTest.java`

**Interfaces :**
- Consomme : `new Chunk(String, String)` (tâche 2), `Embedding.of(float[])` et
  `EmbeddingPolicy.DIMENSIONS` (déjà livrés).
- Produit :
  - `TextChunk.of(UUID documentId, int position, Chunk chunk, Embedding embedding, Instant createdAt) → TextChunk`
  - `TextChunk.getId() / getDocumentId() / getPosition() / getHeading() / getText() / getEmbedding() / getCreatedAt()`
    et `TextChunk.chunk() → Chunk`
  - `TextChunkRepository.saveAll(List<TextChunk>) → List<TextChunk>`,
    `findByDocumentId(UUID) → List<TextChunk>` (ordonnés par position),
    `deleteByDocumentId(UUID) → void`
  - `KnowledgeFixture.unVecteur(float valeur) → Embedding`

> **Avertissement à lire avant l'étape 5.** La colonne `vector` n'est pas un type que
> Hibernate connaît nativement : c'est `hibernate-vector` qui l'apporte, et
> `ddl-auto: validate` compare au démarrage ce que l'entité déclare à ce que Flyway a créé.
> Si le contexte refuse de démarrer sur
> `Schema-validation: wrong column type encountered in column [embedding]`, le remède est
> **dans l'entité, jamais dans `ddl-auto`** : ajouter `columnDefinition = "vector"` au
> `@Column` du champ `embedding` — c'est le nom que PostgreSQL rend dans ses métadonnées,
> et c'est exactement le motif pour lequel `TextBlock.text` porte déjà
> `columnDefinition = "text"`. Le noter dans le commentaire du champ si le cas se présente.

- [ ] **Étape 1 : Déclarer hibernate-vector**

Dans `gradle/libs.versions.toml`, section `[libraries]`, sous `flyway-postgresql` :

```toml
# Vecteurs : le module qui apprend à Hibernate le type `vector` de pgvector, donc
# @JdbcTypeCode(SqlTypes.VECTOR) sur un float[]. Version alignée par le BOM Spring Boot
# (elle suit hibernate-core) : ne pas la pinner.
hibernate-vector = { module = "org.hibernate.orm:hibernate-vector" }
```

Dans `build.gradle.kts`, dans le bloc « Persistance », après `libs.flyway.postgresql` :

```kotlin
    // Le type `vector` côté Hibernate. Arrivé avec la table des extraits et pas avant :
    // une dépendance sans appelant est du poids mort.
    implementation(libs.hibernate.vector)
```

- [ ] **Étape 2 : Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/JpaTextChunkRepositoryAdapterTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * On teste le <strong>port</strong>, jamais l'adapter. Le montage du propriétaire est celui
 * de {@link JpaTextExtractionRepositoryAdapterTest} — la clé étrangère de
 * {@code knowledge_documents} traverse les deux contextes bornés.
 *
 * <p>Le vecteur n'est pas un détail dans ces assertions : c'est la seule vérification que
 * {@code float[]} atterrit bien dans une colonne {@code vector(1024)} et en revient
 * identique. Un test unitaire de l'entité n'apprendrait rien là-dessus.
 *
 * <p>La cascade depuis {@code knowledge_documents}, elle, est vérifiée par
 * {@code DeleteDocumentCascadeTest}, non transactionnel : ici, Hibernate rendrait les extraits
 * depuis son cache de premier niveau sans jamais interroger la base.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaTextChunkRepositoryAdapterTest {

    @Autowired
    private TextChunkRepository textChunkRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void conserve_les_extraits_dans_l_ordre_avec_leur_titre_et_leur_corps() {
        Document document = unDocumentDepose("mona@exemple.fr");

        textChunkRepository.saveAll(List.of(
                unExtrait(document, 0, "Introduction", "Le premier extrait."),
                unExtrait(document, 1, "Introduction", "Le deuxième extrait."),
                unExtrait(document, 2, "", "Le troisième, sans titre.")));

        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .extracting(TextChunk::getPosition)
                .containsExactly(0, 1, 2);
        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .extracting(TextChunk::getHeading)
                .containsExactly("Introduction", "Introduction", "");
    }

    @Test
    void rend_le_vecteur_tel_qu_il_a_ete_range() {
        Document document = unDocumentDepose("nadir@exemple.fr");
        Embedding vecteur = KnowledgeFixture.unVecteur(0.25f);

        textChunkRepository.saveAll(
                List.of(TextChunk.of(document.getId(), 0, new Chunk("Titre", "Un corps."), vecteur, Instant.now())));

        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .singleElement()
                .satisfies(relu -> assertThat(relu.getEmbedding()).isEqualTo(vecteur));
    }

    @Test
    void rend_l_extrait_du_domaine_tel_qu_il_a_ete_range() {
        Document document = unDocumentDepose("olga@exemple.fr");
        Chunk extrait = new Chunk("Introduction", "Un corps bien à lui.");

        textChunkRepository.saveAll(
                List.of(TextChunk.of(document.getId(), 0, extrait, KnowledgeFixture.unVecteur(0.5f), Instant.now())));

        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .singleElement()
                .satisfies(relu -> assertThat(relu.chunk()).isEqualTo(extrait));
    }

    @Test
    void conserve_un_extrait_bien_plus_long_que_255_caracteres() {
        Document document = unDocumentDepose("pierre@exemple.fr");
        String tresLong = "Un corps qui déborde largement d'une colonne de 255 caractères. ".repeat(50);

        textChunkRepository.saveAll(List.of(
                TextChunk.of(document.getId(), 0, new Chunk("", tresLong), KnowledgeFixture.unVecteur(0.1f), Instant.now())));

        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .singleElement()
                .satisfies(relu -> assertThat(relu.getText()).isEqualTo(tresLong.strip()));
    }

    @Test
    void efface_les_extraits_d_un_document() {
        Document document = unDocumentDepose("quentin@exemple.fr");
        textChunkRepository.saveAll(List.of(unExtrait(document, 0, "Titre", "Un corps.")));

        textChunkRepository.deleteByDocumentId(document.getId());

        assertThat(textChunkRepository.findByDocumentId(document.getId())).isEmpty();
    }

    @Test
    void un_second_jeu_d_extraits_remplace_le_premier_apres_effacement() {
        // AMQP livre au moins une fois, et (document_id, chunk_position) est UNIQUE : sans
        // l'effacement préalable, la seconde écriture se heurterait à la contrainte.
        Document document = unDocumentDepose("rosa@exemple.fr");
        textChunkRepository.saveAll(List.of(unExtrait(document, 0, "Titre", "Première version.")));

        textChunkRepository.deleteByDocumentId(document.getId());
        textChunkRepository.saveAll(List.of(unExtrait(document, 0, "Titre", "Deuxième version.")));

        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .singleElement()
                .satisfies(relu -> assertThat(relu.getText()).isEqualTo("Deuxième version."));
    }

    @Test
    void reste_muet_quand_aucun_extrait_n_a_ete_range() {
        assertThat(textChunkRepository.findByDocumentId(UUID.randomUUID())).isEmpty();
    }

    private static TextChunk unExtrait(Document document, int position, String titre, String corps) {
        return TextChunk.of(
                document.getId(), position, new Chunk(titre, corps), KnowledgeFixture.unVecteur(0.5f), Instant.now());
    }

    private Document unDocumentDepose(String email) {
        UUID proprietaire = userRepository
                .save(User.register(new Email(email), "empreinte"))
                .getId();
        byte[] octets = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        return documentRepository.save(
                Document.upload(proprietaire, "notes.md", DocumentFormat.MARKDOWN, Checksum.of(octets), octets.length));
    }
}
```

- [ ] **Étape 3 : Ajouter le vecteur d'essai à l'outillage**

Dans `src/test/java/xyz/sterenn/secondbrain/knowledge/KnowledgeFixture.java`, ajouter les
imports `java.util.Arrays`, `xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy` et
`xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding`, puis la méthode :

```java
    /**
     * Un vecteur constant de la dimension du modèle.
     *
     * <p>Ce qui est vérifié dans ces tests, c'est la <em>forme</em> — qu'un vecteur parte,
     * revienne et se compare —, jamais son contenu : personne n'appelle Ollama dans la suite
     * de tests.
     */
    public static Embedding unVecteur(float valeur) {
        float[] valeurs = new float[EmbeddingPolicy.DIMENSIONS];
        Arrays.fill(valeurs, valeur);
        return Embedding.of(valeurs);
    }
```

- [ ] **Étape 4 : Lancer le test, vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.persistence.JpaTextChunkRepositoryAdapterTest"
```

Attendu : ÉCHEC de compilation, `cannot find symbol: class TextChunk`.

- [ ] **Étape 5 : Écrire la migration**

Créer `src/main/resources/db/migration/V10__create_knowledge_text_chunks.sql` :

```sql
-- Les extraits vectorisés d'un document : ce que la recherche de RAG-8 interrogera.
--
-- UNE TABLE, LE VECTEUR EN COLONNE. C'est du un-pour-un : un extrait naît avec son vecteur
-- et disparaît avec lui. Deux tables imposeraient une jointure sur le chemin chaud de la
-- recherche. Une table de vecteurs à part, nommée par le modèle qui les a produits, aurait
-- permis d'en comparer deux — c'est de la souplesse payée maintenant pour un besoin qui
-- n'existe pas, et deux modèles se comparent aussi bien sur deux bases.
--
-- LE NOM PORTE LA TYPOLOGIE, pas le mot « document » : `knowledge_text_chunks`, comme
-- `knowledge_text_extractions`. Une typologie sonore découpera en segments datés, une
-- visuelle en régions — ADR-0030.
--
-- LA DIMENSION EST FIGÉE DANS LE TYPE DE LA COLONNE, et elle doit rester égale à
-- EmbeddingPolicy.DIMENSIONS. Passer à un modèle de 768 dimensions demandera une migration
-- et une réindexation complète : c'est déjà vrai de toute façon, les vecteurs de deux
-- modèles ne se comparent pas.
--
-- LA CASCADE, pour la deuxième fois, fait que `DeleteDocumentHandler` ne bouge pas.

CREATE TABLE knowledge_text_chunks (
    id             UUID                     NOT NULL DEFAULT gen_random_uuid(),
    document_id    UUID                     NOT NULL,
    chunk_position INTEGER                  NOT NULL,
    heading        VARCHAR(255)             NOT NULL,
    text           TEXT                     NOT NULL,
    embedding      VECTOR(1024)             NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_knowledge_text_chunks PRIMARY KEY (id),
    -- Le filet de l'effacement-puis-écriture du handler : AMQP livre au moins une fois, et
    -- deux livraisons ne doivent pas doubler les extraits. L'index qui la porte sert aussi
    -- la lecture par document, `document_id` étant sa colonne de tête.
    CONSTRAINT uq_knowledge_text_chunks_position UNIQUE (document_id, chunk_position),
    CONSTRAINT fk_knowledge_text_chunks_document FOREIGN KEY (document_id)
        REFERENCES knowledge_documents (id) ON DELETE CASCADE
);

-- HNSW en cosinus : bge-m3 produit des vecteurs normalisés et s'évalue au cosinus. L'index
-- est écrit ici mais interrogé par personne — c'est RAG-8 qui écrira la requête.
CREATE INDEX idx_knowledge_text_chunks_embedding
    ON knowledge_text_chunks USING hnsw (embedding vector_cosine_ops);
```

- [ ] **Étape 6 : Écrire l'entité**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/entity/TextChunk.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

/**
 * Un extrait vectorisé, rangé sous l'identifiant de son document.
 *
 * <p><strong>Une entité, et non une {@code @ElementCollection} d'un agrégat « découpage »</strong>,
 * là où les blocs d'une {@link TextExtraction} en sont une. La différence est réelle : la
 * recherche vectorielle de RAG-8 rendra des extraits un par un, avec leur score. Ils ont
 * besoin d'une identité ; une collection d'éléments n'en a pas.
 *
 * <p>Le rapport à {@link Chunk} est celui de {@link TextExtraction} à {@code ExtractedText} :
 * la logique pure produit l'objet-valeur, l'entité le range. Le découpage n'a pas à savoir
 * qu'il existe une base.
 *
 * <p><strong>La colonne {@code text} porte le corps nu</strong>, jamais le texte préfixé qui
 * est parti au service de vectorisation ({@code Chunk.contextualised}). Ce qui s'affiche à
 * l'écran reste lisible, et changer la forme du préfixe plus tard ne demandera pas de
 * réécrire la base — seulement de revectoriser. La provenance, elle, est dite par
 * {@code heading} et {@code document_id}.
 *
 * <p>Le vecteur est un {@code float[]} annoté, et non un {@link Embedding} projeté par un
 * converter : {@code hibernate-vector} porte le type {@code vector} de pgvector sur un
 * tableau de flottants, et un {@code AttributeConverter} vers une chaîne obligerait
 * PostgreSQL à un transtypage que le pilote ne fait pas. L'objet-valeur reste la seule porte
 * d'entrée et de sortie : {@link #of} n'accepte qu'un {@link Embedding}, {@link #getEmbedding}
 * n'en rend qu'un — la dimension est donc validée par le domaine avant d'atteindre la colonne.
 */
@Entity
@Table(name = "knowledge_text_chunks")
public class TextChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "document_id", nullable = false, columnDefinition = "uuid")
    private UUID documentId;

    // `position` est un mot-clé SQL qu'Hibernate écrirait sans guillemets : `chunk_position`,
    // comme `block_position` du côté de l'extraction, et pour la même raison.
    @Column(name = "chunk_position", nullable = false)
    private int position;

    // La longueur de TextBlock, parce que c'est de là que vient le titre : les deux colonnes
    // ne peuvent pas diverger si l'une nomme la constante de l'autre.
    @Column(nullable = false, length = TextBlock.MAX_HEADING_LENGTH)
    private String heading;

    // columnDefinition explicite : sans lui, Hibernate attendrait un varchar(255) et
    // `ddl-auto: validate` refuserait de démarrer contre une colonne `text`.
    @Column(nullable = false, columnDefinition = "text")
    private String text;

    // Le type `vector` de pgvector, apporté par hibernate-vector. La longueur est celle du
    // modèle, déclarée une seule fois dans EmbeddingPolicy : la colonne, l'index et le
    // modèle ne peuvent pas se désaligner par distraction.
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EmbeddingPolicy.DIMENSIONS)
    @Column(nullable = false)
    private float[] embedding;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TextChunk() {
        // requis par JPA
    }

    private TextChunk(UUID documentId, int position, String heading, String text, float[] embedding, Instant createdAt) {
        this.documentId = documentId;
        this.position = position;
        this.heading = heading;
        this.text = text;
        this.embedding = embedding;
        this.createdAt = createdAt;
    }

    /**
     * Range un extrait et le vecteur qui en a été tiré.
     *
     * <p>{@link Chunk} garantit déjà qu'un extrait a un corps et un titre — éventuellement
     * vide, jamais absent —, et {@link Embedding} qu'un vecteur a la dimension du modèle : il
     * n'y a rien à revalider ici.
     *
     * @throws IllegalArgumentException si la position est négative — c'est une erreur de
     *     programmation de l'appelant, pas un refus métier
     */
    public static TextChunk of(UUID documentId, int position, Chunk chunk, Embedding embedding, Instant createdAt) {
        Objects.requireNonNull(documentId, "Le document dont cet extrait provient est obligatoire");
        Objects.requireNonNull(chunk, "L'extrait est obligatoire");
        Objects.requireNonNull(embedding, "Le vecteur de l'extrait est obligatoire");
        Objects.requireNonNull(createdAt, "L'instant du découpage est obligatoire");
        if (position < 0) {
            throw new IllegalArgumentException("La position d'un extrait part de zéro, reçue : " + position);
        }
        return new TextChunk(documentId, position, chunk.heading(), chunk.text(), embedding.values(), createdAt);
    }

    /** L'extrait du domaine, tel qu'il a été rangé. */
    public Chunk chunk() {
        return new Chunk(heading, text);
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getPosition() {
        return position;
    }

    public String getHeading() {
        return heading;
    }

    public String getText() {
        return text;
    }

    /** L'objet-valeur, jamais le tableau : c'est lui qui porte la dimension du modèle. */
    public Embedding getEmbedding() {
        return Embedding.of(embedding);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
```

- [ ] **Étape 7 : Écrire le port et son adapter**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/port/TextChunkRepository.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.List;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;

/**
 * Port sortant vers le stockage des extraits vectorisés.
 *
 * <p>Aucune méthode ne porte le propriétaire, comme {@link TextExtractionRepository} : un
 * extrait se lit toujours par l'identifiant de son document, lequel a déjà été chargé par
 * {@code findByIdAndOwnerId}. Le cloisonnement est fait en amont, il n'a pas à l'être deux
 * fois.
 *
 * <p>Aucune méthode de recherche par similarité : ce ticket écrit l'index, il ne l'interroge
 * pas. C'est RAG-8 qui ajoutera la requête, et elle n'a pas à être devinée d'avance.
 */
public interface TextChunkRepository {

    List<TextChunk> saveAll(List<TextChunk> textChunks);

    /** Dans l'ordre du document. */
    List<TextChunk> findByDocumentId(UUID documentId);

    /**
     * Efface les extraits d'un document. Silencieux s'il n'y en a pas.
     *
     * <p>Même raison qu'à l'extraction : AMQP livre <em>au moins</em> une fois, et
     * {@code (document_id, chunk_position)} est {@code UNIQUE}. Sans effacement préalable,
     * une redélivrance ferait échouer l'écriture sur la contrainte, et le document passerait
     * en {@code FAILED} pour un traitement qui avait réussi. RAG-7 s'en servira pour la
     * réextraction, sans avoir à la créer.
     */
    void deleteByDocumentId(UUID documentId);
}
```

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/SpringDataTextChunkRepository.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;

/**
 * Détail Spring Data, volontairement package-private : rien hors de ce package ne doit en
 * dépendre.
 */
interface SpringDataTextChunkRepository extends JpaRepository<TextChunk, UUID> {

    List<TextChunk> findByDocumentIdOrderByPosition(UUID documentId);

    void deleteByDocumentId(UUID documentId);
}
```

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/JpaTextChunkRepositoryAdapter.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;

/** Adapter du port {@link TextChunkRepository}. */
@Component
public class JpaTextChunkRepositoryAdapter implements TextChunkRepository {

    private final SpringDataTextChunkRepository springDataTextChunkRepository;

    JpaTextChunkRepositoryAdapter(SpringDataTextChunkRepository springDataTextChunkRepository) {
        this.springDataTextChunkRepository = springDataTextChunkRepository;
    }

    @Override
    public List<TextChunk> saveAll(List<TextChunk> textChunks) {
        return springDataTextChunkRepository.saveAllAndFlush(textChunks);
    }

    @Override
    public List<TextChunk> findByDocumentId(UUID documentId) {
        return springDataTextChunkRepository.findByDocumentIdOrderByPosition(documentId);
    }

    /**
     * Le flush n'est pas décoratif, et c'est le même piège qu'à l'extraction : le handler
     * efface puis écrit dans la même transaction, et {@code (document_id, chunk_position)}
     * est {@code UNIQUE}. Sans lui, Hibernate ordonnerait les insertions avant les
     * suppressions au moment du vidage, et la contrainte se refermerait sur des lignes que
     * l'on venait justement de retirer.
     */
    @Override
    public void deleteByDocumentId(UUID documentId) {
        springDataTextChunkRepository.deleteByDocumentId(documentId);
        springDataTextChunkRepository.flush();
    }
}
```

- [ ] **Étape 8 : Lancer le test, vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.persistence.JpaTextChunkRepositoryAdapterTest"
```

Attendu : SUCCÈS, sept tests verts. **En cas de refus au démarrage sur le type de la colonne
`embedding`, appliquer le remède de l'avertissement ci-dessus — jamais `ddl-auto`.**

- [ ] **Étape 9 : Vérifier la cascade**

Dans `src/test/java/xyz/sterenn/secondbrain/knowledge/application/command/DeleteDocumentCascadeTest.java`,
ajouter les imports `java.time.Instant`, `xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk`,
`xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository`,
`xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk` et `java.util.List`, le champ
injecté, puis le test :

```java
    @Autowired
    private TextChunkRepository textChunkRepository;

    @Test
    void la_suppression_d_un_document_emporte_ses_extraits() {
        // Les extraits sont écrits par le port et non par une commande : l'indexation n'existe
        // pas encore, et ce qui est vérifié ici est la cascade de la migration V10, pas le
        // chemin qui la remplit.
        Document document = unDocumentDepose();
        textChunkRepository.saveAll(List.of(TextChunk.of(
                document.getId(), 0, new Chunk("Titre", "Un corps."), KnowledgeFixture.unVecteur(0.5f), Instant.now())));
        assertThat(textChunkRepository.findByDocumentId(document.getId())).isNotEmpty();

        commandBus.dispatch(new DeleteDocument(document.getId(), document.getOwnerId()));

        assertThat(textChunkRepository.findByDocumentId(document.getId())).isEmpty();
    }
```

- [ ] **Étape 10 : Lancer les deux tests, vérifier qu'ils passent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.persistence.JpaTextChunkRepositoryAdapterTest" \
           --tests "xyz.sterenn.secondbrain.knowledge.application.command.DeleteDocumentCascadeTest"
```

Attendu : SUCCÈS.

- [ ] **Étape 11 : Formater et committer**

```bash
make format-back
git add gradle/libs.versions.toml build.gradle.kts \
        src/main/resources/db/migration/V10__create_knowledge_text_chunks.sql \
        src/main/java/xyz/sterenn/secondbrain/knowledge/domain/entity/TextChunk.java \
        src/main/java/xyz/sterenn/secondbrain/knowledge/domain/port/TextChunkRepository.java \
        src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/ \
        src/test/java/xyz/sterenn/secondbrain/knowledge/KnowledgeFixture.java \
        src/test/java/xyz/sterenn/secondbrain/knowledge/application/command/DeleteDocumentCascadeTest.java \
        src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/JpaTextChunkRepositoryAdapterTest.java
git commit -m "feat: un extrait vectorisé prend sa table, index HNSW compris"
```

---

## Tâche 5 : L'échec cesse d'être « d'extraction »

`MarkDocumentExtractionFailed` et `DocumentExtractionException` sont nommés pour une phase qui
n'est plus la seule. **Sans ce renommage, une URL Ollama mal saisie s'afficherait avec le
message générique**, indiscernable d'un PDF illisible : `KnowledgeEventListener.motif()` ne
teste que `instanceof DocumentExtractionException`, et `EmbeddingUnavailableException` n'en
descend pas. C'est la contrepartie directe de l'absence de contrôle au démarrage, décidée dans
le premier livrable.

Tâche de refactor pure : **aucun comportement ne change**, et le gate est la suite entière.

**Fichiers :**
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/exception/DocumentProcessingException.java`
- Modifier : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/exception/DocumentExtractionException.java`
- Modifier : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/exception/EmbeddingUnavailableException.java`
- Renommer : `…/application/command/MarkDocumentExtractionFailed.java` → `MarkDocumentProcessingFailed.java`
- Renommer : `…/application/command/MarkDocumentExtractionFailedHandler.java` → `MarkDocumentProcessingFailedHandler.java`
- Modifier : `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/messaging/KnowledgeEventListener.java`
- Renommer : `src/test/java/…/application/command/MarkDocumentExtractionFailedTest.java` → `MarkDocumentProcessingFailedTest.java`
- Test : `src/test/java/xyz/sterenn/secondbrain/knowledge/domain/exception/DocumentProcessingExceptionTest.java`

**Interfaces :**
- Consomme : rien des tâches précédentes.
- Produit :
  - `DocumentProcessingException` — classe **abstraite** publique, `extends RuntimeException`,
    deux constructeurs protégés `(String)` et `(String, Throwable)`
  - `DocumentExtractionException extends DocumentProcessingException` (ses deux filles ne
    bougent pas)
  - `EmbeddingUnavailableException extends DocumentProcessingException`
  - `MarkDocumentProcessingFailed(UUID documentId, UUID ownerId, String reason)` — record,
    remplace `MarkDocumentExtractionFailed` ; `MarkDocumentProcessingFailedHandler` son handler

- [ ] **Étape 1 : Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/knowledge/domain/exception/DocumentProcessingExceptionTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Un test de filiation, et il n'est pas décoratif : {@code KnowledgeEventListener.motif()}
 * décide de montrer ou non un message à l'utilisateur sur un seul {@code instanceof}. Le jour
 * où un refus sortirait de cette famille, son message soigné serait silencieusement remplacé
 * par « Le traitement de ce document a échoué de façon inattendue. » — une panne qui ne se
 * verrait qu'à l'écran d'un utilisateur, jamais dans un test de comportement.
 */
class DocumentProcessingExceptionTest {

    @Test
    void un_refus_d_extraction_est_un_refus_de_traitement() {
        assertThat(new UnreadableDocumentException()).isInstanceOf(DocumentProcessingException.class);
        assertThat(new UnextractableDocumentException()).isInstanceOf(DocumentProcessingException.class);
    }

    @Test
    void un_refus_de_vectorisation_est_un_refus_de_traitement() {
        assertThat(new EmbeddingUnavailableException("Le service de vectorisation est injoignable."))
                .isInstanceOf(DocumentProcessingException.class);
    }

    @Test
    void tout_refus_de_traitement_porte_un_message_affichable() {
        assertThat(new UnextractableDocumentException().getMessage()).isNotBlank();
        assertThat(new UnreadableDocumentException().getMessage()).isNotBlank();
    }
}
```

- [ ] **Étape 2 : Lancer le test, vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentProcessingExceptionTest"
```

Attendu : ÉCHEC de compilation, `cannot find symbol: class DocumentProcessingException`.

- [ ] **Étape 3 : Écrire la mère**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/exception/DocumentProcessingException.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * Mère de tous les refus qui peuvent interrompre le <strong>traitement</strong> d'un document,
 * quelle qu'en soit l'étape — extraction du texte hier, vectorisation aujourd'hui.
 *
 * <p>Elle existe pour une raison précise, et une seule : c'est elle que le consommateur
 * d'événements interroge pour décider si le message d'échec peut être montré à l'utilisateur.
 * Un refus métier porte un message affichable tel quel ; une {@code NullPointerException} n'en
 * porte aucun qu'on puisse afficher. Voir ADR-0028.
 *
 * <p>Elle a remplacé {@link DocumentExtractionException} dans ce rôle quand la vectorisation
 * est arrivée : « extraction » nommait une phase qui n'est plus la seule, et une URL Ollama
 * mal saisie se serait affichée avec le motif générique, indiscernable d'un PDF illisible.
 *
 * <p>{@code RuntimeException} et non checked : c'est ce qui déclenche le rollback promis par
 * le {@code CommandBus}.
 */
public abstract class DocumentProcessingException extends RuntimeException {

    protected DocumentProcessingException(String message) {
        super(message);
    }

    protected DocumentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Étape 4 : Rebrancher les deux familles**

Dans `DocumentExtractionException.java` : remplacer `extends RuntimeException` par
`extends DocumentProcessingException`, et remplacer les deux derniers paragraphes de la
Javadoc par :

```java
/**
 * Mère des deux façons dont l'extraction d'un document peut refuser d'aboutir.
 *
 * <p>Elle reste utile après l'arrivée de {@link DocumentProcessingException} : elle dit
 * <em>quelle</em> étape a refusé, là où sa mère dit seulement qu'un refus est affichable. Ce
 * qui décide de l'affichage, en revanche, c'est la mère — le consommateur d'événements ne
 * teste plus qu'elle.
 *
 * <p>{@code RuntimeException} par sa mère, et non checked : c'est ce qui déclenche le rollback
 * promis par le {@code CommandBus}.
 */
```

Dans `EmbeddingUnavailableException.java` : remplacer `extends RuntimeException` par
`extends DocumentProcessingException`, et remplacer le **dernier** paragraphe de la Javadoc
(celui qui annonce « Elle prendra pour parent {@code DocumentProcessingException} dans le
livrable suivant ») par :

```java
 * <p>{@code RuntimeException} par sa mère, et non checked : c'est ce qui déclenche le rollback
 * promis par le {@code CommandBus}. Et {@link DocumentProcessingException} pour mère, et non
 * {@code DocumentExtractionException} : {@code KnowledgeEventListener.motif()} ne montre que
 * les messages de cette famille, et sans ce lien de parenté le message soigné ci-dessus serait
 * écrasé par le motif générique du listener — exactement ce qu'il existe pour éviter.
```

Les constructeurs des deux classes ne changent pas.

- [ ] **Étape 5 : Renommer la commande et son handler**

```bash
git mv src/main/java/xyz/sterenn/secondbrain/knowledge/application/command/MarkDocumentExtractionFailed.java \
       src/main/java/xyz/sterenn/secondbrain/knowledge/application/command/MarkDocumentProcessingFailed.java
git mv src/main/java/xyz/sterenn/secondbrain/knowledge/application/command/MarkDocumentExtractionFailedHandler.java \
       src/main/java/xyz/sterenn/secondbrain/knowledge/application/command/MarkDocumentProcessingFailedHandler.java
git mv src/test/java/xyz/sterenn/secondbrain/knowledge/application/command/MarkDocumentExtractionFailedTest.java \
       src/test/java/xyz/sterenn/secondbrain/knowledge/application/command/MarkDocumentProcessingFailedTest.java
```

Puis, dans ces trois fichiers, remplacer partout `MarkDocumentExtractionFailed` par
`MarkDocumentProcessingFailed` — déclaration de record, nom de classe du handler, paramètre
générique `CommandHandler<…>`, nom de la classe de test, et les deux `new …` du test.

Dans `MarkDocumentProcessingFailed.java`, remplacer la première ligne de la Javadoc par :

```java
/**
 * Consigner qu'un <strong>traitement</strong> a échoué, et pourquoi. « Traitement » et non
 * « extraction » : la même commande consigne l'échec d'une vectorisation.
 *
```

Le reste de sa Javadoc (la commande à part, la transaction séparée, ADR-0028, le motif
affichable) ne change pas.

- [ ] **Étape 6 : Rebrancher le listener**

Dans `KnowledgeEventListener.java` :
- remplacer l'import `…command.MarkDocumentExtractionFailed` par `…command.MarkDocumentProcessingFailed` ;
- remplacer l'import `…exception.DocumentExtractionException` par `…exception.DocumentProcessingException` ;
- dans `on(DocumentUploaded)`, remplacer `new MarkDocumentExtractionFailed(…)` par
  `new MarkDocumentProcessingFailed(…)` ;
- remplacer la méthode `motif` :

```java
    /**
     * Un refus métier porte un message affichable tel quel ; le reste n'en porte aucun qu'on
     * puisse montrer. Le message d'une {@code NullPointerException} n'a rien à faire sous les
     * yeux de l'utilisateur — il est dans le journal, où il sert.
     *
     * <p>C'est {@link DocumentProcessingException} qui est testée, et non la seule
     * {@code DocumentExtractionException} : un service de vectorisation injoignable doit
     * s'annoncer comme tel, sans quoi une URL mal saisie serait indiscernable d'un PDF
     * illisible.
     */
    private static String motif(RuntimeException echec) {
        return echec instanceof DocumentProcessingException refusMetier ? refusMetier.getMessage() : ECHEC_INATTENDU;
    }
```

- [ ] **Étape 7 : Lancer toute la suite**

```bash
docker compose down
gtest test
```

Attendu : SUCCÈS. C'est le gate de cette tâche : un refactor de renommage se prouve par
l'absence de régression, pas par un test de plus.

- [ ] **Étape 8 : Formater et committer**

```bash
make format-back
git add -A src/main/java/xyz/sterenn/secondbrain/knowledge/ src/test/java/xyz/sterenn/secondbrain/knowledge/
git commit -m "refactor: un échec de traitement n'est plus un échec d'extraction"
```

---

## Tâche 6 : Une commande découpe, vectorise et range

`IndexDocumentText` — « indexer » couvre les trois gestes en un mot, plutôt qu'un nom qui les
énumère. **Tout tient dans la transaction ouverte par le bus**, appels Ollama compris : le
« tout ou rien » est donc gratuit, c'est le rollback, et un Ollama qui tombe au troisième lot
ne laisse aucun extrait derrière lui.

**Fichiers :**
- Modifier : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/DocumentStatus.java`
- Modifier : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/entity/Document.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/event/DocumentTextIndexed.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/application/command/IndexDocumentText.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/application/command/IndexDocumentTextHandler.java`
- Modifier : `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/messaging/KnowledgeMessagingConfiguration.java`
- Modifier : `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/messaging/KnowledgeEventListener.java`
- Créer : `src/test/java/xyz/sterenn/secondbrain/knowledge/ConstantEmbeddingPortConfiguration.java`
- Test : `src/test/java/xyz/sterenn/secondbrain/knowledge/application/command/IndexDocumentTextTest.java`

**Interfaces :**
- Consomme : `RecursiveChunker.chunk(ExtractedText) → List<Chunk>` (tâche 3),
  `TextChunk.of(UUID, int, Chunk, Embedding, Instant)` et `TextChunkRepository` (tâche 4),
  `Chunk.contextualised(String)` (tâche 2), `TokenCounter` (tâche 1), et les déjà livrés
  `EmbeddingPort.embed(List<String>) → List<Embedding>`, `TextExtractionRepository`,
  `DocumentRepository`, `DomainEventPublisher`, `Clock`.
- Produit :
  - `DocumentStatus.READY`
  - `Document.markIndexed() → void`
  - `IndexDocumentText(UUID documentId, UUID ownerId)` — record `Command`
  - `DocumentTextIndexed(UUID documentId, UUID ownerId, int chunkCount, Instant occurredAt)` —
    record `DomainEvent`, clé `knowledge.document-text.indexed`
  - `ConstantEmbeddingPortConfiguration.ConstantEmbeddingPort` — bean `@Primary` de test :
    `textesRecus() → List<String>`, `tombeEnPanne()`, `clear()`

- [ ] **Étape 1 : Écrire la doublure du service de vectorisation**

Créer `src/test/java/xyz/sterenn/secondbrain/knowledge/ConstantEmbeddingPortConfiguration.java` :

```java
package xyz.sterenn.secondbrain.knowledge;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import xyz.sterenn.secondbrain.knowledge.domain.exception.EmbeddingUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.EmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;

/**
 * Remplace le service de vectorisation par un vecteur constant. <strong>Aucun test de la suite
 * n'appelle Ollama</strong> : il faudrait un modèle de 2,2 Go et une machine capable de le
 * servir, pour vérifier une propriété qui n'appartient pas à ce projet.
 *
 * <p>Même dispositif que {@code RecordingNotificationSenderConfiguration} du contexte
 * {@code users} : un bean {@code @Primary} devant l'adapter réel, donc les tests vérifient le
 * <em>port</em>. Elle enregistre au passage les textes reçus, ce qui est la seule façon de
 * constater que ce qui part au modèle est bien le texte <strong>préfixé</strong>.
 *
 * <p>Le bean est partagé par tout le contexte Spring : appeler {@link ConstantEmbeddingPort#clear()}
 * en {@code @BeforeEach}, le rollback de la transaction de test ne le vide pas.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ConstantEmbeddingPortConfiguration {

    @Bean
    @Primary
    public ConstantEmbeddingPort constantEmbeddingPort() {
        return new ConstantEmbeddingPort();
    }

    public static class ConstantEmbeddingPort implements EmbeddingPort {

        private final List<String> recus = new CopyOnWriteArrayList<>();
        private final AtomicBoolean enPanne = new AtomicBoolean(false);

        @Override
        public List<Embedding> embed(List<String> texts) {
            if (enPanne.get()) {
                // Le message nomme la vectorisation, comme celui de l'adapter réel : c'est
                // lui que le test de bout en bout retrouve sur le document en échec.
                throw new EmbeddingUnavailableException(
                        "Le service de vectorisation n'a pas répondu : ce document n'a pas pu être indexé.");
            }
            recus.addAll(texts);
            return texts.stream().map(texte -> KnowledgeFixture.unVecteur(0.5f)).toList();
        }

        /** Les textes tels qu'ils sont partis au modèle — préfixe compris. */
        public List<String> textesRecus() {
            return List.copyOf(recus);
        }

        public void tombeEnPanne() {
            enPanne.set(true);
        }

        public void clear() {
            recus.clear();
            enPanne.set(false);
        }
    }
}
```

- [ ] **Étape 2 : Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/knowledge/application/command/IndexDocumentTextTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.ConstantEmbeddingPortConfiguration;
import xyz.sterenn.secondbrain.knowledge.ConstantEmbeddingPortConfiguration.ConstantEmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.EmbeddingUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * L'indexation par le bus, comme en production — dépôt et extraction compris, sans quoi il n'y
 * aurait rien à découper.
 *
 * <p>{@code @Transactional} : la base est annulée après chaque test, le disque non, d'où le
 * nettoyage en {@code @AfterEach}. Conséquence à connaître : <strong>ce qui est vérifié ici,
 * ce n'est pas le « tout ou rien »</strong> — dans une transaction de test, un rollback du bus
 * ne défait rien de visible. La preuve qu'un Ollama à terre ne laisse aucun extrait derrière
 * lui appartient au test du worker, qui observe de vrais commits.
 */
@Import({TestcontainersConfiguration.class, ConstantEmbeddingPortConfiguration.class})
@SpringBootTest
@Transactional
class IndexDocumentTextTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TextChunkRepository textChunkRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConstantEmbeddingPort embeddingPort;

    @Value("${secondbrain.storage.originals-path}")
    private String cheminDesOriginaux;

    @BeforeEach
    void videLaDoublure() {
        embeddingPort.clear();
    }

    @AfterEach
    void nettoieLesOriginaux() {
        KnowledgeFixture.videLesOriginaux(cheminDesOriginaux);
    }

    @Test
    void range_les_extraits_vectorises_et_marque_le_document_pret() {
        Document document = unDocumentExtrait("structure.md", Fixtures.STRUCTURE_MD);

        commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId()));

        List<TextChunk> extraits = textChunkRepository.findByDocumentId(document.getId());
        assertThat(extraits).isNotEmpty();
        assertThat(extraits).allSatisfy(extrait -> assertThat(
                        extrait.getEmbedding().values())
                .hasSize(EmbeddingPolicy.DIMENSIONS));
        assertThat(relis(document).getStatus()).isEqualTo(DocumentStatus.READY);
    }

    @Test
    void numerote_les_extraits_dans_l_ordre_du_document() {
        Document document = unDocumentExtrait("structure.md", Fixtures.STRUCTURE_MD);

        commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId()));

        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .extracting(TextChunk::getPosition)
                .startsWith(0)
                .isSorted();
    }

    @Test
    void vectorise_un_texte_prefixe_du_nom_du_document_et_de_sa_section() {
        // Le préfixe part au modèle ; la colonne, elle, porte le corps nu. C'est la seule
        // façon de constater les deux d'un coup.
        Document document = unDocumentExtrait("structure.md", Fixtures.STRUCTURE_MD);

        commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId()));

        assertThat(embeddingPort.textesRecus())
                .isNotEmpty()
                .allSatisfy(texte -> assertThat(texte).startsWith("Document: structure.md"));
        assertThat(embeddingPort.textesRecus()).anySatisfy(texte -> assertThat(texte).contains("— Section: "));
        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .allSatisfy(extrait -> assertThat(extrait.getText()).doesNotContain("Document: structure.md"));
    }

    @Test
    void une_seconde_indexation_remplace_les_extraits_sans_les_doubler() {
        Document document = unDocumentExtrait("structure.md", Fixtures.STRUCTURE_MD);
        commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId()));
        int premiers = textChunkRepository.findByDocumentId(document.getId()).size();

        commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId()));

        assertThat(textChunkRepository.findByDocumentId(document.getId())).hasSize(premiers);
    }

    @Test
    void laisse_remonter_le_refus_du_service_de_vectorisation() {
        Document document = unDocumentExtrait("notes.txt", Fixtures.BRUT_TXT);
        embeddingPort.tombeEnPanne();

        // Dernier appel du test : le refus marque la transaction englobante rollback-only.
        assertThatExceptionOfType(EmbeddingUnavailableException.class)
                .isThrownBy(() -> commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId())));
    }

    @Test
    void refuse_un_document_qui_n_appartient_pas_au_demandeur() {
        Document document = unDocumentExtrait("notes.txt", Fixtures.BRUT_TXT);

        // Dernier appel du test, même raison.
        assertThatExceptionOfType(DocumentNotFoundException.class)
                .isThrownBy(() -> commandBus.dispatch(new IndexDocumentText(document.getId(), UUID.randomUUID())));
    }

    /**
     * Dépose un document par le bus puis en extrait le texte. Le <strong>premier</strong>
     * argument est le nom sous lequel il est déposé — c'est lui qui décide du format —, le
     * <strong>second</strong> le nom d'une fixture, dont le contenu est lu.
     */
    private Document unDocumentExtrait(String filename, String fixture) {
        UUID proprietaire = userRepository
                .save(User.register(new Email(UUID.randomUUID() + "@exemple.fr"), "empreinte"))
                .getId();
        byte[] contenu = Fixtures.lire(fixture);
        commandBus.dispatch(new UploadDocument(proprietaire, filename, contenu));
        Document document = documentRepository
                .findByOwnerIdAndChecksum(proprietaire, Checksum.of(contenu))
                .orElseThrow();
        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId()));
        return document;
    }

    private Document relis(Document document) {
        return documentRepository
                .findByIdAndOwnerId(document.getId(), document.getOwnerId())
                .orElseThrow();
    }
}
```

- [ ] **Étape 3 : Lancer le test, vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.application.command.IndexDocumentTextTest"
```

Attendu : ÉCHEC de compilation, `cannot find symbol: class IndexDocumentText`.

- [ ] **Étape 4 : Compléter le cycle de vie du document**

Dans `DocumentStatus.java`, remplacer la Javadoc de l'énumération et ajouter la constante :

```java
/**
 * Étape d'un document dans la chaîne d'ingestion.
 *
 * <p>{@code READY} est l'état d'aboutissement : le texte est extrait, découpé, vectorisé, et
 * le document est interrogeable. Il n'a été déclaré qu'au moment où quelqu'un l'atteint — un
 * état que personne n'atteint fait croire à un cycle de vie qui n'existe pas.
 *
 * <p>{@code EXTRACTED} n'est donc plus un aboutissement mais une étape : un document qui s'y
 * arrête a bien du texte, et rien qui permette de l'interroger.
 *
 * <p>{@code FAILED} n'est pas un état terminal. Une réextraction (RAG-7) en repart, et
 * {@code markTextExtracted} efface alors le motif de l'échec précédent.
 */
public enum DocumentStatus {

    /** Déposé, son fichier d'origine conservé, en attente de traitement. */
    PENDING,

    /** Son texte a été extrait et rangé dans un {@code TextExtraction}. */
    EXTRACTED,

    /** Ses extraits sont découpés, vectorisés et rangés : il est interrogeable. */
    READY,

    /** Le traitement a échoué ; le motif est lisible sur le document. */
    FAILED
}
```

Dans `Document.java`, ajouter après `markTextExtracted()` :

```java
    /**
     * Les extraits de ce document sont découpés, vectorisés et rangés : il est interrogeable.
     *
     * <p>Efface le motif d'un échec précédent, pour la même raison que
     * {@link #markTextExtracted()} : un document réindexé avec succès ne garde pas
     * l'explication de ce qui a raté la fois d'avant.
     *
     * <p>Aucun garde sur l'état de départ, volontairement — même arbitrage qu'à l'extraction.
     */
    public void markIndexed() {
        this.status = DocumentStatus.READY;
        this.errorMessage = null;
    }
```

- [ ] **Étape 5 : Écrire l'événement et le déclarer**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/event/DocumentTextIndexed.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

/**
 * Les extraits d'un document viennent d'être vectorisés et rangés.
 *
 * <p>Comme ses aînés, il porte des identifiants et non l'état : le consommateur relit, et une
 * centaine de vecteurs de mille dimensions n'a rien à faire sur un transport de messages.
 *
 * <p>{@code chunkCount} est la seule donnée non identifiante, pour la même raison que
 * {@code blockCount} l'était : elle rend le journal du worker lisible sans requête.
 *
 * <p>Son nom simple est {@code <Objet><Fait>} : {@code DocumentText} + {@code Indexed}, d'où
 * la clé {@code knowledge.document-text.indexed}, qu'un binding {@code knowledge.#} voit
 * comme tous les autres. <strong>Personne n'en fait rien aujourd'hui</strong> : il est annoncé
 * parce qu'une étape franchie s'annonce, et le seul consommateur le journalise. RAG-8 aura de
 * quoi s'y accrocher.
 */
public record DocumentTextIndexed(UUID documentId, UUID ownerId, int chunkCount, Instant occurredAt)
        implements DomainEvent {

    public DocumentTextIndexed {
        Objects.requireNonNull(documentId, "L'identifiant du document est obligatoire");
        Objects.requireNonNull(ownerId, "Le propriétaire du document est obligatoire");
        Objects.requireNonNull(occurredAt, "L'instant de l'événement est obligatoire");
        if (chunkCount <= 0) {
            throw new IllegalArgumentException("Une indexation sans extrait n'a rien à annoncer");
        }
    }
}
```

Dans `KnowledgeMessagingConfiguration.java`, ajouter l'import et compléter la déclaration :

```java
    @Bean
    public DomainEventRegistration knowledgeDomainEvents() {
        return new DomainEventRegistration(
                List.of(DocumentUploaded.class, DocumentTextExtracted.class, DocumentTextIndexed.class));
    }
```

Dans `KnowledgeEventListener.java`, ajouter l'import de `DocumentTextIndexed` et, après
`on(DocumentTextExtracted)` :

```java
    /**
     * Les extraits d'un document viennent d'être rangés — la fin de la chaîne, pour l'instant.
     *
     * <p>Ce handler ne fait que journaliser, et il doit pourtant exister : un type déclaré dans
     * {@code DomainEventRegistration} mais sans {@code @RabbitHandler} est refusé par Spring
     * AMQP et rejeté comme un type inconnu.
     */
    @RabbitHandler
    public void on(DocumentTextIndexed event) {
        log.info(
                "Événement knowledge.document-text.indexed reçu pour le document {} : {} extraits",
                event.documentId(),
                event.chunkCount());
    }
```

- [ ] **Étape 6 : Écrire la commande et son handler**

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/application/command/IndexDocumentText.java` :

```java
package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

/**
 * Découper le texte extrait d'un document, le vectoriser et le ranger.
 *
 * <p>« Indexer » couvre les trois gestes en un mot, là où un nom qui les énumérerait
 * (« découper puis vectoriser puis… ») deviendrait faux au premier changement d'ordre.
 *
 * <p>Le propriétaire voyage avec le document, comme pour toutes les commandes de ce contexte :
 * le cloisonnement ne se relâche pas parce qu'on est dans un worker.
 */
public record IndexDocumentText(UUID documentId, UUID ownerId) implements Command {}
```

Créer `src/main/java/xyz/sterenn/secondbrain/knowledge/application/command/IndexDocumentTextHandler.java` :

```java
package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.RecursiveChunker;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextExtraction;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentTextIndexed;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.EmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TokenCounter;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.shared.event.DomainEventPublisher;

/**
 * Orchestre l'indexation : relecture du document et de son texte, découpage, vectorisation,
 * remplacement des extraits, changement de statut, puis annonce.
 *
 * <p>Aucun {@code @Transactional} ici — la transaction est ouverte par
 * {@code SpringCommandBus.dispatch}. <strong>Elle englobe les appels au service de
 * vectorisation</strong>, et c'est un choix : le « tout ou rien » qu'exige le ticket est alors
 * gratuit, c'est le rollback, il n'y a rien à construire. Un Ollama qui tombe au troisième lot
 * ne laisse aucun extrait derrière lui, et le document garde son texte extrait.
 *
 * <p><strong>Le prix est assumé : une connexion PostgreSQL tenue quelques dizaines de secondes
 * par document</strong> — un PDF de trente pages fait une centaine d'extraits, soit quatre
 * lots. Sur une application mono-utilisateur dont le worker consomme en séquence, c'est
 * tenable. C'est précisément le genre de chose qu'on « corrige » spontanément faute de savoir
 * qu'elle a été pesée : les deux découpes en commandes chaînées ou en écritures par lot ont
 * été écartées, la première parce qu'elle rendrait un document découpé mais non vectorisé
 * possible, la seconde parce que c'est l'état partiel que le ticket interdit.
 *
 * <p><strong>Vectoriser avant de toucher à la base.</strong> Transactionnellement c'est
 * indifférent, mais ça se lit mieux, et c'est l'ordre du handler d'extraction : on obtient ce
 * dont on a besoin, puis on écrit.
 *
 * <p><strong>L'effacement avant l'écriture</strong> répond à la redélivrance AMQP, comme à
 * l'extraction : {@code (document_id, chunk_position)} est {@code UNIQUE}.
 *
 * <p>Le {@link RecursiveChunker} est construit ici plutôt qu'injecté : c'est une classe du
 * domaine, elle n'a pas à porter d'annotation Spring, et sa seule dépendance est un port dont
 * ce handler dispose.
 */
@Component
public class IndexDocumentTextHandler implements CommandHandler<IndexDocumentText> {

    private final DocumentRepository documentRepository;
    private final TextExtractionRepository textExtractionRepository;
    private final TextChunkRepository textChunkRepository;
    private final EmbeddingPort embeddingPort;
    private final RecursiveChunker chunker;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public IndexDocumentTextHandler(
            DocumentRepository documentRepository,
            TextExtractionRepository textExtractionRepository,
            TextChunkRepository textChunkRepository,
            EmbeddingPort embeddingPort,
            TokenCounter tokenCounter,
            DomainEventPublisher domainEventPublisher,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.textExtractionRepository = textExtractionRepository;
        this.textChunkRepository = textChunkRepository;
        this.embeddingPort = embeddingPort;
        this.chunker = new RecursiveChunker(tokenCounter);
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    @Override
    public void handle(IndexDocumentText command) {
        Document document = documentRepository
                .findByIdAndOwnerId(command.documentId(), command.ownerId())
                .orElseThrow(DocumentNotFoundException::new);

        TextExtraction extraction = textExtractionRepository
                .findByDocumentId(document.getId())
                // Une anomalie, pas un refus métier : l'événement annonce un texte extrait, et
                // il n'y en a pas. Le consommateur montrera donc le motif générique, ce qui est
                // juste — l'utilisateur n'y peut rien.
                .orElseThrow(() -> new IllegalStateException(
                        "Le document " + document.getId() + " est annoncé extrait mais ne porte aucun texte"));

        List<Chunk> extraits = chunker.chunk(extraction.text());
        // Le port garantit autant de vecteurs que de textes, et dans le même ordre : c'est tout
        // son contrat, et c'est ce qui permet d'apparier par l'indice ci-dessous.
        List<Embedding> vecteurs = embeddingPort.embed(extraits.stream()
                .map(extrait -> extrait.contextualised(document.getFilename()))
                .toList());

        Instant maintenant = clock.instant();
        textChunkRepository.deleteByDocumentId(document.getId());
        textChunkRepository.saveAll(IntStream.range(0, extraits.size())
                .mapToObj(position -> TextChunk.of(
                        document.getId(), position, extraits.get(position), vecteurs.get(position), maintenant))
                .toList());

        document.markIndexed();
        documentRepository.save(document);

        domainEventPublisher.publish(
                new DocumentTextIndexed(document.getId(), document.getOwnerId(), extraits.size(), maintenant));
    }
}
```

- [ ] **Étape 7 : Lancer le test, vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.application.command.IndexDocumentTextTest"
```

Attendu : SUCCÈS, six tests verts.

- [ ] **Étape 8 : Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/knowledge/ \
        src/test/java/xyz/sterenn/secondbrain/knowledge/ConstantEmbeddingPortConfiguration.java \
        src/test/java/xyz/sterenn/secondbrain/knowledge/application/command/IndexDocumentTextTest.java
git commit -m "feat: une commande découpe, vectorise et range les extraits d'un document"
```

---

## Tâche 7 : Le worker enchaîne, et l'échec de vectorisation se voit

La ligne de journal que la Javadoc de `KnowledgeEventListener` annonce depuis deux tickets
devient un dispatch. **C'est ici que la chaîne se ferme** — et ici seulement que le
« tout ou rien » se constate, parce que le worker observe de vrais commits.

**Fichiers :**
- Modifier : `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/messaging/KnowledgeEventListener.java`
- Modifier : `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/messaging/KnowledgeEventListenerTest.java`
- Modifier : `CLAUDE.md`

**Interfaces :**
- Consomme : `IndexDocumentText(UUID, UUID)` et `MarkDocumentProcessingFailed(UUID, UUID, String)`
  (tâches 6 et 5), `ConstantEmbeddingPortConfiguration` (tâche 6), `TextChunkRepository` (tâche 4).
- Produit : rien de nouveau pour les tâches suivantes — la chaîne complète
  `dépôt → extraction → indexation → READY`.

- [ ] **Étape 1 : Écrire les tests qui échouent**

Dans `KnowledgeEventListenerTest.java` :

1. ajouter aux imports `xyz.sterenn.secondbrain.knowledge.ConstantEmbeddingPortConfiguration`,
   `xyz.sterenn.secondbrain.knowledge.ConstantEmbeddingPortConfiguration.ConstantEmbeddingPort`
   et `xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository` ;
2. remplacer `@Import(TestcontainersConfiguration.class)` par
   `@Import({TestcontainersConfiguration.class, ConstantEmbeddingPortConfiguration.class})` ;
3. ajouter les deux champs injectés :

```java
    @Autowired
    private TextChunkRepository textChunkRepository;

    @Autowired
    private ConstantEmbeddingPort embeddingPort;
```

4. compléter le nettoyage existant — la doublure est un bean partagé, son état survit à tout :

```java
    @AfterEach
    void efface_ce_qui_a_ete_commite() {
        // La clé étrangère en cascade emporte les documents, leurs textes et leurs extraits
        // avec le compte ; le disque, lui, ne participe à aucune transaction (ADR-0020).
        comptesCrees.forEach(email -> jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", email));
        comptesCrees.clear();
        KnowledgeFixture.videLesOriginaux(cheminDesOriginaux);
        embeddingPort.clear();
    }
```

5. **remplacer** le test `extrait_le_texte_du_document_dont_le_depot_est_annonce` — le worker
   ne s'arrête plus à `EXTRACTED`, et l'attendre là serait une course perdue d'avance :

```java
    @Test
    void indexe_le_document_dont_le_depot_est_annonce_et_le_declare_pret() {
        // La chaîne entière, telle qu'elle tourne en production : le dépôt annonce, le worker
        // extrait, l'extraction annonce, le worker découpe et vectorise. Deux messages, deux
        // transactions, un seul statut à l'arrivée.
        Document document = unDocumentDepose("structure.md", Fixtures.STRUCTURE_MD);

        publie(document);

        await().atMost(DELAI).untilAsserted(() -> {
            assertThat(textExtractionRepository.findByDocumentId(document.getId()))
                    .get()
                    .satisfies(texte -> assertThat(texte.getBlocks()).isNotEmpty());
            assertThat(textChunkRepository.findByDocumentId(document.getId())).isNotEmpty();
            assertThat(statutDe(document)).isEqualTo(DocumentStatus.READY);
        });
    }
```

6. **remplacer** le test `ne_double_pas_le_texte_quand_l_evenement_est_livre_deux_fois` :

```java
    @Test
    void ne_double_ni_le_texte_ni_les_extraits_quand_l_evenement_est_livre_deux_fois() {
        Document document = unDocumentDepose("structure.md", Fixtures.STRUCTURE_MD);
        publie(document);
        await().atMost(DELAI).untilAsserted(() -> assertThat(statutDe(document)).isEqualTo(DocumentStatus.READY));
        int extraits = textChunkRepository.findByDocumentId(document.getId()).size();

        publie(document);

        await().during(Duration.ofSeconds(2)).atMost(DELAI).untilAsserted(() -> {
            assertThat(statutDe(document)).isEqualTo(DocumentStatus.READY);
            assertThat(textExtractionRepository.findByDocumentId(document.getId()))
                    .isPresent();
            assertThat(textChunkRepository.findByDocumentId(document.getId())).hasSize(extraits);
        });
    }
```

7. ajouter le test de la panne de vectorisation :

```java
    @Test
    void marque_le_document_en_echec_quand_la_vectorisation_ne_repond_pas() {
        // Le seul endroit où le « tout ou rien » se constate : ici, les transactions sont
        // réellement commitées ou réellement annulées. L'extraction, elle, a commité avant —
        // le document garde donc son texte, et l'écran de détail montre ce qui a marché et où
        // ça a cassé.
        Document document = unDocumentDepose("structure.md", Fixtures.STRUCTURE_MD);
        embeddingPort.tombeEnPanne();

        publie(document);

        await().atMost(DELAI).untilAsserted(() -> {
            assertThat(statutDe(document)).isEqualTo(DocumentStatus.FAILED);
            assertThat(motifDe(document)).contains("vectorisation");
        });
        assertThat(textChunkRepository.findByDocumentId(document.getId())).isEmpty();
        assertThat(textExtractionRepository.findByDocumentId(document.getId())).isPresent();
    }
```

- [ ] **Étape 2 : Lancer le test, vérifier qu'il échoue**

```bash
docker compose down
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.messaging.KnowledgeEventListenerTest"
```

Attendu : ÉCHEC. Le document s'arrête à `EXTRACTED`, aucun extrait n'est écrit, et la panne de
vectorisation ne produit aucun `FAILED` — le listener journalise toujours.

- [ ] **Étape 3 : Remplacer la ligne de journal par un dispatch**

Dans `KnowledgeEventListener.java`, ajouter l'import `…command.IndexDocumentText` et remplacer
entièrement la méthode `on(DocumentTextExtracted)` :

```java
    /**
     * Le texte d'un document vient d'être extrait : on le découpe et on le vectorise.
     *
     * <p>Même dispositif qu'au dépôt, et pour la même raison (ADR-0028) : le bus annule sa
     * transaction sur la moindre exception, donc l'échec se consigne depuis une
     * <em>seconde</em> commande, dans une transaction à elle. Sans quoi le document resterait
     * éternellement en {@code EXTRACTED}, sans que rien ne dise pourquoi.
     *
     * <p>C'est aussi ce qui rend le « tout ou rien » gratuit : la transaction annulée emporte
     * les extraits déjà écrits, et le document garde son texte extrait. L'écran de détail
     * montre alors ce qui a marché et où ça a cassé.
     */
    @RabbitHandler
    public void on(DocumentTextExtracted event) {
        try {
            commandBus.dispatch(new IndexDocumentText(event.documentId(), event.ownerId()));
        } catch (RuntimeException echec) {
            log.error("Indexation du document {} en échec", event.documentId(), echec);
            commandBus.dispatch(new MarkDocumentProcessingFailed(event.documentId(), event.ownerId(), motif(echec)));
        }
    }
```

Et, dans la Javadoc de la classe, retirer la phrase devenue fausse — celle qui annonçait que
« RAG-5 remplacera cette ligne par un dispatch » a disparu avec la méthode ; vérifier qu'aucune
autre ne subsiste.

- [ ] **Étape 4 : Lancer le test, vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.messaging.KnowledgeEventListenerTest"
```

Attendu : SUCCÈS. Ce test est le plus lent de la suite : deux messages, deux transactions, et
des attentes de vingt secondes.

- [ ] **Étape 5 : Lancer toute la suite**

```bash
gtest test
```

Attendu : SUCCÈS. C'est le moment où l'on constate que rien d'autre n'attendait `EXTRACTED`
comme état d'arrivée.

- [ ] **Étape 6 : Documenter le flux dans `CLAUDE.md`**

Quatre endroits, et rien d'autre :

1. **L'arborescence du contexte `knowledge`** — compléter les lignes existantes :

```
│   ├── domain/
│   │   ├── ExtractionPolicy plancher de caractères sous lequel un document est inexploitable
│   │   ├── EmbeddingPolicy  dimension du vecteur, contrat entre le modèle, la colonne et l'index
│   │   ├── ChunkingPolicy   cible, plafond et recouvrement d'un extrait, en tokens
│   │   ├── RecursiveChunker le découpage lui-même : sections, paragraphes, phrases
│   │   ├── entity/          Document, TextExtraction (le texte extrait, agrégat à part),
│   │   │                    TextChunk (un extrait et son vecteur)
│   │   ├── valueobject/     … Chunk (un extrait, avant qu'il soit rangé)
│   │   ├── port/            DocumentRepository, DocumentStorage, TextExtractionRepository,
│   │   │                    DocumentTextExtractor, EmbeddingPort, TokenCounter,
│   │   │                    TextChunkRepository
│   │   ├── exception/       … DocumentProcessingException (mère de tous les refus de
│   │   │                    traitement, c'est elle que le worker interroge)
│   │   └── event/           DocumentUploaded, DocumentTextExtracted, DocumentTextIndexed
│   ├── application/
│   │   ├── command/         UploadDocument, DeleteDocument, ExtractDocumentText,
│   │   │                    IndexDocumentText, MarkDocumentProcessingFailed
```

et, dans `infrastructure/ai/`, ajouter `JtokkitTokenCounter` à côté d'`OllamaEmbeddingAdapter`.

2. **Une section de flux**, juste après « Le flux de l'extraction du texte » :

```markdown
### Le flux du découpage et de la vectorisation

Le worker reçoit `DocumentTextExtracted` et dispatche `IndexDocumentText`, qui relit le
document et son texte, découpe, vectorise, remplace les extraits, pose `READY` et annonce
`DocumentTextIndexed`.

**Le découpage est une logique de domaine pure** — `RecursiveChunker`, aux côtés des trois
policies — et quatre niveaux de repli : une section sous le plafond donne un extrait ; sinon
on coupe aux paragraphes (la double ligne vide que `TextBlock.normalise` garantit), puis aux
phrases (`BreakIterator`, le JDK), puis net, faute de frontière. Deux extraits consécutifs
d'une même section se recouvrent d'environ 90 tokens repris **en phrases entières** ; le
recouvrement ne franchit jamais une frontière de section, et il cède devant le plafond — c'est
un confort, le plafond est un invariant.

**Le comptage passe par un port** (`TokenCounter`, adapter jtokkit en `cl100k_base`) parce que
c'est la toise d'un autre : `bge-m3` s'appuie sur un sentencepiece XLM-RoBERTa. C'est sans
danger — `cl100k` sur-compte le français, donc le plafond est conservateur — mais 600 est un
proxy, pas une mesure. Les tests du découpage prennent un compteur « un mot égale un token »,
ce qui rend les frontières lisibles dans les assertions.

**Ce qui part au modèle est préfixé, ce qui est stocké ne l'est pas.**
`Chunk.contextualised(filename)` rend `Document: rapport.pdf — Section: Introduction` suivi du
corps, et c'est la seule méthode qui connaisse cette forme ; la colonne `text` porte le corps
nu. Changer la forme du préfixe ne demandera donc pas de réécrire la base, seulement de
revectoriser — et l'écran reste lisible. Ce que ça suppose et qui est vrai : aucune route ne
renomme un document.

**Tout tient dans la transaction du bus, appels Ollama compris.** Le « tout ou rien » est
gratuit : c'est le rollback. Un Ollama à terre ne laisse aucun extrait derrière lui, le
document passe `FAILED` en gardant son texte extrait, et le motif nomme la vectorisation —
c'est à ça que sert `DocumentProcessingException`, mère commune des refus d'extraction et de
vectorisation, seule famille dont le listener montre les messages. Le prix est une connexion
PostgreSQL tenue quelques dizaines de secondes par document : pesé, et tenable pour une
application mono-utilisateur dont le worker consomme en séquence.
```

3. **La section « Persistance »**, après le paragraphe sur les deux tables du texte extrait :

```markdown
Les extraits vectorisés vivent dans une troisième table, `knowledge_text_chunks` : une ligne
par extrait, son vecteur en colonne (`vector(1024)`), `UNIQUE (document_id, chunk_position)`
et un index HNSW en `vector_cosine_ops` que **personne n'interroge encore** — RAG-8 écrira la
requête. La dimension est figée dans le type de la colonne, et doit rester égale à
`EmbeddingPolicy.DIMENSIONS`. Elle cascade elle aussi à la suppression du document : c'est la
deuxième fois qu'un ticket ajoute des tables sans toucher à `DeleteDocumentHandler`.
```

4. **La section « Stack et versions »** — ajouter à la ligne du back, après `PDFBox` :
   `· jtokkit (comptage de tokens) · hibernate-vector`.

- [ ] **Étape 7 : Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/messaging/KnowledgeEventListener.java \
        src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/messaging/KnowledgeEventListenerTest.java \
        CLAUDE.md
git commit -m "feat: le worker découpe et vectorise ce qu'il vient d'extraire"
```

---

## Tâche 8 : Le front apprend un statut de plus

Sans lui, les deux écrans afficheraient le code brut `READY` — `DocumentStatusTag` rend
`LABELS[status] ?? status`. C'est tout ce que le front a à apprendre : **montrer les extraits
est hors périmètre**, ce sont des artefacts machine, et c'est RAG-8 qui aura une raison de les
exposer, avec des scores de similarité à côté.

**Fichiers :**
- Modifier : `frontend/src/components/DocumentStatusTag.vue`
- Modifier : `frontend/src/views/DesignSystemView.vue`

**Interfaces :**
- Consomme : le code `READY` sérialisé par l'API (tâche 6).
- Produit : rien pour d'autres tâches.

- [ ] **Étape 1 : Ajouter le libellé et la sévérité**

Dans `frontend/src/components/DocumentStatusTag.vue`, compléter les deux tables :

```js
const LABELS = {
  PENDING: 'En attente de traitement',
  EXTRACTED: 'Texte extrait',
  READY: 'Prêt à être interrogé',
  FAILED: 'Traitement en échec',
}

// La sévérité est une décision de rendu, pas une donnée : « en attente » n'est ni un
// succès ni une erreur. `EXTRACTED` passe de `success` à `info` : ce n'est plus un
// aboutissement mais une étape, et c'est `READY` qui porte désormais le vert.
const SEVERITIES = {
  PENDING: 'secondary',
  EXTRACTED: 'info',
  READY: 'success',
  FAILED: 'danger',
}
```

- [ ] **Étape 2 : Compléter le catalogue**

Dans `frontend/src/views/DesignSystemView.vue`, ajouter `READY` à la liste des statuts — un
composant partagé y apparaît dans **chacun** de ses états, dans le même commit :

```js
const DOCUMENT_STATUSES = ['PENDING', 'EXTRACTED', 'READY', 'FAILED']
```

- [ ] **Étape 3 : Formater, construire et regarder**

```bash
make format-front
gfront npm run build
```

Attendu : SUCCÈS. Le build est le seul contrôle qui compile les templates — aucun test ne les
rend (ADR-0016).

Puis, la pile démarrée (`docker compose up -d`), ouvrir <http://localhost:8080/design-system>
et vérifier de l'œil que les **quatre** statuts s'affichent avec leur libellé français et
quatre couleurs distinctes. C'est le contrôle de rendu du projet ; il se fait là.

- [ ] **Étape 4 : Committer**

```bash
git add frontend/src/components/DocumentStatusTag.vue frontend/src/views/DesignSystemView.vue
git commit -m "feat: un document prêt à être interrogé se lit dans la liste"
```

---

## Contrôle final

Une fois les huit tâches passées, avant d'ouvrir la pull request :

- [ ] **La suite entière, des deux côtés**

```bash
docker compose down
make check
```

Attendu : SUCCÈS — formatage vérifié et tests verts, back et front.

- [ ] **Le parcours réel, sur la pile**

```bash
docker compose up -d --build
docker compose logs -f worker
```

Déposer un PDF ou un Markdown depuis <http://localhost:8080/documents>, puis constater dans les
logs les deux événements (`knowledge.document-text.extracted` puis
`knowledge.document-text.indexed`), et à l'écran le statut « Prêt à être interrogé ». Enfin,
vérifier que les vecteurs sont bien là :

```bash
docker compose exec db psql -U second_brain -d second_brain \
  -c "SELECT document_id, count(*) AS extraits FROM knowledge_text_chunks GROUP BY document_id;"
```

**Au premier démarrage, le modèle se télécharge encore** (2,2 Go) : un document traité pendant
ce temps échoue avec un motif qui nomme la vectorisation — c'est le comportement attendu, pas
un bug. `docker compose logs -f ollama-pull` dit où en est le téléchargement.

- [ ] **Ce que ce plan laisse volontairement ouvert**

À ne pas « corriger » en cours de route, chacun a son ticket : la recherche vectorielle et
l'écran des extraits (RAG-8), la réextraction d'un document modifié (RAG-7, qui trouvera
`deleteByDocumentId` déjà écrit des deux côtés), la reprise après redémarrage et la file
d'attente persistante (exclues depuis RAG-6), le découpage sémantique et la taille adaptative
selon le format (exclus par le ticket). Et le cas du PDF, dont les frontières de paragraphe
sont perdues à l'extraction : une section y arrive comme un seul paragraphe et sera découpée à
la phrase — dégradé, pas faux, et annoncé depuis ADR-0027.
