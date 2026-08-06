# Création de compte — architecture hexagonale + CQRS — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permettre à un visiteur anonyme de créer un compte (email + mot de passe) via un formulaire HTML, en posant au passage l'architecture hexagonale + CQRS qui servira de modèle aux features suivantes.

**Architecture:** Trois couches par bounded context (`users/domain`, `users/application`, `users/infrastructure`) plus un package `shared/bus` transverse. Le domaine porte les entités, les value objects et les **ports** (interfaces) ; l'infrastructure porte les **adapters** (JPA, hachage, web). L'application ne contient que des commandes, des queries et leurs handlers, invoqués via deux bus synchrones. **Le `CommandBus` porte la transaction** : `dispatch` est annoté `@Transactional`, donc tout le handler s'exécute dans une seule transaction SQL et un échec annule l'ensemble.

**Tech Stack:** Java 25, Spring Boot 4.0.7 (MVC, Data JPA, Security, Validation), Thymeleaf, Flyway, PostgreSQL 17, JUnit 5 + Testcontainers.

**Ticket source:** [En tant qu'utilisateur je crée mon compte](https://app.notion.com/p/3b2215c5e46e80c7ae04c6d1e5efed7b) (Kanban → Product)

---

## Global Constraints

- **Java 25**, **Spring Boot 4.0.7**. Ne pas changer ces versions.
- **Flyway est maître du schéma.** `ddl-auto: validate` : toute évolution passe par un nouveau `src/main/resources/db/migration/V<n>__<nom>.sql`. **Ne jamais supprimer ni modifier une migration déjà appliquée** — pour retirer une table, ajouter une migration qui la `DROP`.
- **Ne pas pinner les versions Spring** dans `gradle/libs.versions.toml` : elles viennent du BOM. Les starters s'ajoutent sans version.
- **Sens des dépendances :** `infrastructure` → `application` → `domain`. Le domaine n'importe **jamais** `xyz.sterenn.secondbrain.users.infrastructure` ni `org.springframework.*`. Seule exception actée : le domaine porte les annotations `jakarta.persistence` (choix « entité de domaine annotée JPA », voir Écarts assumés).
- **Aucun `@Transactional` sur les handlers.** La transaction appartient au bus. En annoter un le ferait proxifier en JDK proxy, ce qui casserait la résolution de son type générique au démarrage.
- **Commentaires, messages d'erreur et libellés UI en français.**
- **Pattern de test d'intégration imposé** : `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`. Ne pas introduire `@DataJpaTest` (il remplace la datasource et casse Flyway + Testcontainers). Tout ce qui peut être testé sans Spring l'est en test unitaire pur.
- **Messages de commit** : préfixe conventionnel minuscule (`feat:`, `refactor:`, `conf:`), description en français. Un commit par tâche.
- **Table `users_users`** : nom repris verbatim du ticket, ne pas le « corriger ».
- **Politique de mot de passe** : longueur ≥ 12 et ≤ 128, plus une blocklist de mots de passe courants. Pas de règle de composition (reco NIST SP 800-63B).

### Imports des annotations de test

Spring Boot 4 a redécoupé ses modules et déplacé plusieurs annotations de test par rapport à Boot 3. Le plan écrit `org.springframework.test.web.servlet.autoconfigure.AutoConfigureMockMvc`. **Si le compilateur ne résout pas cet import**, ne pas modifier le test : localiser la classe et corriger la ligne `import`.

```bash
docker run --rm -v second-brain-gradle-home:/home/gradle/.gradle gradle:jdk25 \
  sh -c 'for j in $(find /home/gradle/.gradle -name "*.jar" | grep -Ei "spring.*(test|boot)"); do
           unzip -l "$j" 2>/dev/null | grep -q AutoConfigureMockMvc.class && echo "$j" && \
           unzip -l "$j" | grep AutoConfigureMockMvc.class; done'
```

Candidats connus, par ordre de probabilité : `org.springframework.test.web.servlet.autoconfigure`, `org.springframework.boot.webmvc.test.autoconfigure`, `org.springframework.boot.test.autoconfigure.web.servlet` (chemin Boot 3). Même méthode pour toute autre annotation introuvable.

### Lancer les tests

Il n'y a **aucun JDK sur la machine hôte** — tout passe par Docker. Définir cette fonction une fois, au début de la session :

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

- `--network host` est **obligatoire** : Testcontainers démarre PostgreSQL en conteneur frère et s'y connecte via `localhost:<port mappé>`. Sans partage du namespace réseau de l'hôte, la connexion échoue.
- Le volume nommé conserve le cache Gradle et le JDK 25 téléchargé par la toolchain : le premier lancement est long, les suivants sont rapides.

---

## File Structure

```
src/main/java/xyz/sterenn/secondbrain/
├── SecondBrainApplication.java
├── config/
│   ├── OpenApiConfig.java                          (inchangé)
│   └── SecurityConfig.java                         (modifié — plus d'auth)
├── shared/bus/                                     ← transverse, sans dépendance métier
│   ├── Command.java                                marqueur
│   ├── CommandHandler.java                         port entrant générique
│   ├── CommandBus.java                             contrat de dispatch
│   ├── SpringCommandBus.java                       routage + @Transactional
│   ├── Query.java                                  marqueur porteur du type de retour
│   ├── QueryHandler.java
│   ├── QueryBus.java
│   ├── SpringQueryBus.java                         routage + @Transactional(readOnly)
│   ├── HandlerNotFoundException.java
│   └── BusConfiguration.java                       déclare les deux beans
└── users/
    ├── domain/                                     ← aucune dépendance sortante
    │   ├── User.java                               entité (agrégat)
    │   ├── Email.java                              value object
    │   ├── EmailAttributeConverter.java            projection JPA du VO
    │   ├── PasswordPolicy.java                     règle métier pure
    │   ├── InvalidEmailException.java
    │   ├── WeakPasswordException.java
    │   ├── EmailAlreadyUsedException.java
    │   ├── UserRepository.java                     PORT sortant
    │   └── PasswordHasher.java                     PORT sortant
    ├── application/
    │   ├── command/
    │   │   ├── RegisterUser.java
    │   │   └── RegisterUserHandler.java
    │   └── query/
    │       ├── FindUserByEmail.java
    │       ├── FindUserByEmailHandler.java
    │       └── UserView.java                       modèle de lecture
    └── infrastructure/
        ├── persistence/
        │   ├── SpringDataUserRepository.java       détail Spring Data
        │   └── JpaUserRepositoryAdapter.java       ADAPTER du port UserRepository
        ├── security/
        │   └── BCryptPasswordHasher.java           ADAPTER du port PasswordHasher
        └── web/
            ├── RegistrationController.java         ADAPTER entrant
            └── RegistrationForm.java

src/main/resources/
├── templates/register.html
└── db/migration/
    ├── V1__init.sql                                (conservé, déjà appliqué)
    ├── V2__drop_note.sql                           (créé — retire la démo)
    └── V3__create_users_users.sql                  (créé)
```

Supprimés en tâche 1 : `note/Note.java`, `note/NoteRepository.java`, `note/NoteController.java`.

## Écarts assumés

À signaler en revue, aucune action requise :

1. **Le domaine porte les annotations JPA.** `User` est un `@Entity` situé dans `domain/`. Choix explicite de minimalisme : pas de classe `UserEntity` miroir ni de mapper. L'hexagone fuit sur ce point précis, les ports et le sens des dépendances tiennent partout ailleurs.
2. **Le Gherkin dit « depuis la page de login ».** La page de login est hors-périmètre ; le bouton « Créer mon compte » vit sur `/register` sans lien amont.
3. **CSRF reste désactivé et la session `STATELESS`.** L'activer impose une session HTTP, ce qui relève du ticket login. Un commentaire de dette est posé dans `SecurityConfig`.
4. **La query `FindUserByEmail` n'est consommée par aucun écran.** Le ticket n'a pas besoin de lecture ; elle existe pour que le query bus soit livré testé plutôt qu'en code mort, et sert de gabarit au ticket login.
5. **BCrypt ignore les octets au-delà du 72e** alors que la politique autorise 128 caractères. Comportement standard, commenté dans le code.

---

### Task 1: Nettoyer — retirer l'authentification et la feature de démo

Le ticket demande de supprimer le HTTP Basic de départ ; la décision a été prise de retirer aussi la feature `note`, pour que le repo ne contienne qu'une seule architecture. Cette tâche vient en premier : tout le reste suppose un accès public et un `src/` propre.

**Files:**
- Modify: `src/main/java/xyz/sterenn/secondbrain/config/SecurityConfig.java`
- Modify: `src/main/resources/application.yml` (bloc `spring.security`)
- Modify: `compose.yaml`, `.env.example` (variables `APP_ADMIN_*`)
- Delete: `src/main/java/xyz/sterenn/secondbrain/note/Note.java`, `NoteRepository.java`, `NoteController.java`
- Create: `src/main/resources/db/migration/V2__drop_note.sql`
- Modify: `README.md`
- Test: `src/test/java/xyz/sterenn/secondbrain/config/SecurityConfigTest.java`

**Interfaces:**
- Consumes: rien.
- Produces: une application sans authentification et sans package `note`. Toutes les tâches suivantes en dépendent.

- [ ] **Step 1: Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/config/SecurityConfigTest.java` :

```java
package xyz.sterenn.secondbrain.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.autoconfigure.AutoConfigureMockMvc;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aucune_authentification_n_est_exigee() throws Exception {
        // Une URL inconnue doit répondre 404 et non 401 : la preuve qu'aucun
        // filtre d'authentification ne s'interpose avant le routage.
        mockMvc.perform(get("/une-url-inexistante"))
            .andExpect(status().isNotFound());
    }

    @Test
    void la_documentation_openapi_reste_accessible() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Lancer le test pour vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.config.SecurityConfigTest"
```

Attendu : ÉCHEC sur `aucune_authentification_n_est_exigee`, `Status expected:<404> but was:<401>`.

- [ ] **Step 3: Retirer HTTP Basic**

Remplacer intégralement `src/main/java/xyz/sterenn/secondbrain/config/SecurityConfig.java` :

```java
package xyz.sterenn.secondbrain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration de sécurité.
 *
 * <p>L'authentification HTTP Basic de départ (utilisateur admin en dur) a été retirée :
 * la création de compte doit être accessible à un visiteur anonyme et il n'existe pas
 * encore de mécanisme de remplacement. Tout est donc public.
 *
 * <p>TODO : le ticket « login » introduira l'authentification par session, et avec elle
 * la réactivation de CSRF et une vraie politique d'autorisation. CSRF reste désactivé et
 * la session STATELESS d'ici là, faute de session HTTP à protéger.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
```

Au démarrage, Spring Boot loguera toujours `Using generated security password: <uuid>` : c'est l'auto-configuration `UserDetailsService` par défaut, et cet utilisateur ne sert à rien puisque aucune requête n'exige d'authentification. Ne pas chercher à l'exclure — le nom de la classe d'auto-config a changé en Spring Boot 4 et une exclusion erronée casse le démarrage.

- [ ] **Step 4: Retirer la configuration de l'utilisateur admin**

Dans `src/main/resources/application.yml`, supprimer ce bloc et lui seul :

```yaml
  security:
    user:
      # Utilisateur par défaut pour l'authentification HTTP Basic (à remplacer).
      name: ${APP_ADMIN_USER:admin}
      password: ${APP_ADMIN_PASSWORD:admin}
```

Dans `compose.yaml`, supprimer les deux lignes du service `app` :

```yaml
      APP_ADMIN_USER: ${APP_ADMIN_USER:-admin}
      APP_ADMIN_PASSWORD: ${APP_ADMIN_PASSWORD:-admin}
```

Dans `.env.example`, supprimer les trois dernières lignes :

```
# Identifiants HTTP Basic de l'API (profil de départ)
APP_ADMIN_USER=admin
APP_ADMIN_PASSWORD=admin
```

`.env` n'est pas versionné ; y laisser les variables orphelines est sans effet, les retirer aussi si le fichier existe.

- [ ] **Step 5: Lancer le test pour vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.config.SecurityConfigTest"
```

Attendu : SUCCÈS, 2 tests.

- [ ] **Step 6: Supprimer la feature de démo `note`**

```bash
git rm -r src/main/java/xyz/sterenn/secondbrain/note
```

Créer `src/main/resources/db/migration/V2__drop_note.sql` :

```sql
-- Retire la table de démonstration `note` : la feature applicative a été supprimée
-- au profit de l'architecture hexagonale du bounded context `users`.
-- V1__init.sql est conservé tel quel — une migration déjà appliquée ne se supprime pas.

DROP TABLE IF EXISTS note;
```

- [ ] **Step 7: Lancer toute la suite**

```bash
gtest test
```

Attendu : SUCCÈS, 3 tests (`SecondBrainApplicationTests` 1 + `SecurityConfigTest` 2). Aucune erreur de compilation liée à `note`.

- [ ] **Step 8: Mettre à jour le README**

Dans le tableau « Stack », remplacer :

```
| Sécurité | Spring Security (HTTP Basic de départ) |
```

par :

```
| Sécurité | Spring Security (aucune authentification pour l'instant) |
```

Remplacer intégralement la section `### Identifiants de l'API` (titre compris) par :

```markdown
### Authentification

Aucune. L'authentification HTTP Basic de départ a été retirée : tous les endpoints
sont publics en attendant le ticket « login ».
```

Supprimer intégralement la section `### Endpoints de démo` et son bloc de code : la feature `note` n'existe plus.

Dans la section « Structure », remplacer le bloc de code par :

```
src/main/java/xyz/sterenn/secondbrain/
├── SecondBrainApplication.java
└── config/            # SecurityConfig, OpenApiConfig
src/main/resources/
├── application.yml            # config commune (pilotée par variables d'env)
├── application-dev.yml        # profil dev
└── db/migration/              # migrations Flyway
```

(Ce bloc sera complété au fil des tâches suivantes.)

- [ ] **Step 9: Commit**

```bash
git add -A src/main src/test README.md compose.yaml .env.example
git commit -m "refactor: retire l'authentification HTTP Basic et la feature de démo note"
```

---

### Task 2: Bus de commandes et de queries

Le socle CQRS : deux bus synchrones qui routent un message vers son unique handler. `CommandBus.dispatch` est annoté `@Transactional` — c'est **là** que vit la transaction SQL exigée par la demande.

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/shared/bus/Command.java`
- Create: `.../shared/bus/CommandHandler.java`
- Create: `.../shared/bus/CommandBus.java`
- Create: `.../shared/bus/SpringCommandBus.java`
- Create: `.../shared/bus/Query.java`
- Create: `.../shared/bus/QueryHandler.java`
- Create: `.../shared/bus/QueryBus.java`
- Create: `.../shared/bus/SpringQueryBus.java`
- Create: `.../shared/bus/HandlerNotFoundException.java`
- Create: `.../shared/bus/BusConfiguration.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/shared/bus/SpringCommandBusTest.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/shared/bus/SpringQueryBusTest.java`

**Interfaces:**
- Consumes: rien.
- Produces :
  - `interface Command {}` — marqueur.
  - `interface CommandHandler<C extends Command> { void handle(C command); }`
  - `interface CommandBus { void dispatch(Command command); }`
  - `interface Query<R> {}` — marqueur porteur du type de retour.
  - `interface QueryHandler<Q extends Query<R>, R> { R handle(Q query); }`
  - `interface QueryBus { <R> R ask(Query<R> query); }`
  - `HandlerNotFoundException extends RuntimeException`
  - Beans `CommandBus` et `QueryBus` déclarés par `BusConfiguration`.
  - Les tâches 5, 6 et 7 en dépendent.

Les tests de cette tâche sont **unitaires purs** : les bus s'instancient avec `new`. Le comportement transactionnel, qui exige le proxy Spring, est vérifié en tâche 5.

- [ ] **Step 1: Écrire les tests qui échouent**

Créer `src/test/java/xyz/sterenn/secondbrain/shared/bus/SpringCommandBusTest.java` :

```java
package xyz.sterenn.secondbrain.shared.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Test unitaire : le bus est instancié directement, sans contexte Spring. Le
 * comportement transactionnel (qui exige le proxy) est couvert par
 * {@code RegisterUserCommandTransactionTest}.
 */
class SpringCommandBusTest {

    record Saluer(String nom) implements Command {
    }

    record Partir() implements Command {
    }

    static class SaluerHandler implements CommandHandler<Saluer> {
        String recu;

        @Override
        public void handle(Saluer command) {
            this.recu = command.nom();
        }
    }

    static class PartirHandler implements CommandHandler<Partir> {
        boolean appele;

        @Override
        public void handle(Partir command) {
            this.appele = true;
        }
    }

    @Test
    void route_la_commande_vers_son_seul_handler() {
        SaluerHandler saluer = new SaluerHandler();
        PartirHandler partir = new PartirHandler();
        CommandBus bus = new SpringCommandBus(List.of(saluer, partir));

        bus.dispatch(new Saluer("Rémy"));

        assertThat(saluer.recu).isEqualTo("Rémy");
        assertThat(partir.appele).isFalse();
    }

    @Test
    void echoue_si_aucun_handler_ne_traite_la_commande() {
        CommandBus bus = new SpringCommandBus(List.of());

        assertThatThrownBy(() -> bus.dispatch(new Saluer("Rémy")))
            .isInstanceOf(HandlerNotFoundException.class)
            .hasMessageContaining("Saluer");
    }

    @Test
    void echoue_au_demarrage_si_deux_handlers_visent_la_meme_commande() {
        assertThatThrownBy(() -> new SpringCommandBus(List.of(new SaluerHandler(), new SaluerHandler())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Saluer");
    }
}
```

Créer `src/test/java/xyz/sterenn/secondbrain/shared/bus/SpringQueryBusTest.java` :

```java
package xyz.sterenn.secondbrain.shared.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringQueryBusTest {

    record CompterLettres(String mot) implements Query<Integer> {
    }

    static class CompterLettresHandler implements QueryHandler<CompterLettres, Integer> {
        @Override
        public Integer handle(CompterLettres query) {
            return query.mot().length();
        }
    }

    @Test
    void route_la_query_et_renvoie_son_resultat() {
        QueryBus bus = new SpringQueryBus(List.of(new CompterLettresHandler()));

        int resultat = bus.ask(new CompterLettres("bonjour"));

        assertThat(resultat).isEqualTo(7);
    }

    @Test
    void echoue_si_aucun_handler_ne_traite_la_query() {
        QueryBus bus = new SpringQueryBus(List.of());

        assertThatThrownBy(() -> bus.ask(new CompterLettres("bonjour")))
            .isInstanceOf(HandlerNotFoundException.class)
            .hasMessageContaining("CompterLettres");
    }
}
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.shared.bus.*"
```

Attendu : ÉCHEC à la compilation, `cannot find symbol: class Command`, `class SpringCommandBus`, etc.

- [ ] **Step 3: Écrire les contrats côté commande**

Créer `src/main/java/xyz/sterenn/secondbrain/shared/bus/Command.java` :

```java
package xyz.sterenn.secondbrain.shared.bus;

/**
 * Intention de modifier l'état du système. Une commande ne retourne rien : toute
 * lecture passe par une {@link Query}. À implémenter par des records immuables.
 */
public interface Command {
}
```

Créer `.../shared/bus/CommandHandler.java` :

```java
package xyz.sterenn.secondbrain.shared.bus;

/**
 * Traite une et une seule commande. Un handler est un bean Spring sans état.
 *
 * <p><strong>Ne jamais annoter un handler avec {@code @Transactional}</strong> : la
 * transaction est portée par {@link CommandBus#dispatch}, et la proxification du
 * handler empêcherait la résolution de son type générique au démarrage.
 */
public interface CommandHandler<C extends Command> {

    void handle(C command);
}
```

Créer `.../shared/bus/CommandBus.java` :

```java
package xyz.sterenn.secondbrain.shared.bus;

/**
 * Achemine une commande vers son handler, de façon synchrone et transactionnelle.
 */
public interface CommandBus {

    /**
     * @throws HandlerNotFoundException si aucun handler n'est enregistré pour ce type
     */
    void dispatch(Command command);
}
```

- [ ] **Step 4: Écrire les contrats côté query**

Créer `.../shared/bus/Query.java` :

```java
package xyz.sterenn.secondbrain.shared.bus;

/**
 * Demande de lecture. Le paramètre {@code R} porte le type de retour, ce qui permet
 * à {@link QueryBus#ask} d'être typé sans cast côté appelant.
 *
 * @param <R> type du résultat
 */
public interface Query<R> {
}
```

Créer `.../shared/bus/QueryHandler.java` :

```java
package xyz.sterenn.secondbrain.shared.bus;

/**
 * Traite une et une seule query. Comme les {@link CommandHandler}, ne doit pas être
 * annoté {@code @Transactional} : {@link QueryBus#ask} ouvre déjà une transaction
 * en lecture seule.
 */
public interface QueryHandler<Q extends Query<R>, R> {

    R handle(Q query);
}
```

Créer `.../shared/bus/QueryBus.java` :

```java
package xyz.sterenn.secondbrain.shared.bus;

/**
 * Achemine une query vers son handler, de façon synchrone.
 */
public interface QueryBus {

    /**
     * @throws HandlerNotFoundException si aucun handler n'est enregistré pour ce type
     */
    <R> R ask(Query<R> query);
}
```

Créer `.../shared/bus/HandlerNotFoundException.java` :

```java
package xyz.sterenn.secondbrain.shared.bus;

/**
 * Aucun handler n'est enregistré pour le message dispatché. Erreur de câblage,
 * pas erreur métier.
 */
public class HandlerNotFoundException extends RuntimeException {

    public HandlerNotFoundException(Class<?> messageType) {
        super("Aucun handler enregistré pour " + messageType.getSimpleName());
    }
}
```

- [ ] **Step 5: Écrire les deux bus**

Créer `.../shared/bus/SpringCommandBus.java` :

```java
package xyz.sterenn.secondbrain.shared.bus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.GenericTypeResolver;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bus synchrone : la table de routage est construite une fois au démarrage, en
 * résolvant le paramètre générique de chaque handler.
 *
 * <p><strong>La transaction SQL vit ici.</strong> {@code dispatch} est annoté
 * {@code @Transactional} : tout ce que le handler déclenche — lectures, écritures,
 * appels à d'autres composants — s'exécute dans une seule transaction, et la moindre
 * {@link RuntimeException} annule l'ensemble. (Rappel Spring : une exception
 * <em>checked</em> ne déclenche pas de rollback par défaut ; les exceptions métier du
 * projet héritent donc toutes de {@code RuntimeException}.)
 */
public class SpringCommandBus implements CommandBus {

    private final Map<Class<?>, CommandHandler<?>> handlers = new HashMap<>();

    public SpringCommandBus(List<CommandHandler<?>> discoveredHandlers) {
        for (CommandHandler<?> handler : discoveredHandlers) {
            Class<?> commandType = commandTypeOf(handler);
            CommandHandler<?> previous = handlers.put(commandType, handler);
            if (previous != null) {
                throw new IllegalStateException(
                    "Deux handlers déclarés pour la commande " + commandType.getSimpleName()
                        + " : " + previous.getClass().getName() + " et " + handler.getClass().getName());
            }
        }
    }

    // Type brut assumé : la table de routage est hétérogène, et le typage est garanti
    // par construction (la clé est la classe de commande que le handler déclare traiter).
    @Override
    @Transactional
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void dispatch(Command command) {
        CommandHandler handler = handlers.get(command.getClass());
        if (handler == null) {
            throw new HandlerNotFoundException(command.getClass());
        }
        handler.handle(command);
    }

    private static Class<?> commandTypeOf(CommandHandler<?> handler) {
        Class<?>[] arguments =
            GenericTypeResolver.resolveTypeArguments(handler.getClass(), CommandHandler.class);
        if (arguments == null || arguments.length != 1) {
            throw new IllegalStateException(
                handler.getClass().getName() + " doit implémenter CommandHandler avec un type concret");
        }
        return arguments[0];
    }
}
```

Créer `.../shared/bus/SpringQueryBus.java` :

```java
package xyz.sterenn.secondbrain.shared.bus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.GenericTypeResolver;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pendant lecture de {@link SpringCommandBus}. La transaction est ouverte en lecture
 * seule : Hibernate peut sauter le dirty checking, et une écriture accidentelle depuis
 * une query échoue au lieu de passer inaperçue.
 */
public class SpringQueryBus implements QueryBus {

    private final Map<Class<?>, QueryHandler<?, ?>> handlers = new HashMap<>();

    public SpringQueryBus(List<QueryHandler<?, ?>> discoveredHandlers) {
        for (QueryHandler<?, ?> handler : discoveredHandlers) {
            Class<?> queryType = queryTypeOf(handler);
            QueryHandler<?, ?> previous = handlers.put(queryType, handler);
            if (previous != null) {
                throw new IllegalStateException(
                    "Deux handlers déclarés pour la query " + queryType.getSimpleName()
                        + " : " + previous.getClass().getName() + " et " + handler.getClass().getName());
            }
        }
    }

    // Même parti pris de type brut que SpringCommandBus.
    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <R> R ask(Query<R> query) {
        QueryHandler handler = handlers.get(query.getClass());
        if (handler == null) {
            throw new HandlerNotFoundException(query.getClass());
        }
        return (R) handler.handle(query);
    }

    private static Class<?> queryTypeOf(QueryHandler<?, ?> handler) {
        Class<?>[] arguments =
            GenericTypeResolver.resolveTypeArguments(handler.getClass(), QueryHandler.class);
        if (arguments == null || arguments.length != 2) {
            throw new IllegalStateException(
                handler.getClass().getName() + " doit implémenter QueryHandler avec des types concrets");
        }
        return arguments[0];
    }
}
```

- [ ] **Step 6: Déclarer les beans**

Créer `.../shared/bus/BusConfiguration.java` :

```java
package xyz.sterenn.secondbrain.shared.bus;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Câblage des deux bus.
 *
 * <p>Les handlers sont collectés via {@link ObjectProvider} et non via un
 * {@code List<...>} injecté : une liste requise vide fait échouer le démarrage du
 * contexte, ce qui interdirait de démarrer l'application tant qu'aucun handler n'existe.
 *
 * <p>Les bus sont déclarés en {@code @Bean} plutôt qu'en {@code @Component} pour que
 * leur constructeur reste utilisable tel quel dans les tests unitaires.
 */
@Configuration
public class BusConfiguration {

    @Bean
    public CommandBus commandBus(ObjectProvider<CommandHandler<?>> handlers) {
        return new SpringCommandBus(handlers.stream().toList());
    }

    @Bean
    public QueryBus queryBus(ObjectProvider<QueryHandler<?, ?>> handlers) {
        return new SpringQueryBus(handlers.stream().toList());
    }
}
```

- [ ] **Step 7: Lancer les tests pour vérifier qu'ils passent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.shared.bus.*"
```

Attendu : SUCCÈS, 5 tests.

- [ ] **Step 8: Vérifier que le contexte démarre sans aucun handler**

```bash
gtest test --tests "xyz.sterenn.secondbrain.SecondBrainApplicationTests"
```

Attendu : SUCCÈS. C'est le contrôle qui valide le choix d'`ObjectProvider` : à ce stade, zéro handler existe.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/xyz/sterenn/secondbrain/shared \
        src/test/java/xyz/sterenn/secondbrain/shared
git commit -m "feat: ajoute un bus de commandes transactionnel et un bus de queries"
```

---

### Task 3: Domaine `users`

Le cœur métier : le value object `Email`, l'agrégat `User`, la règle de robustesse du mot de passe, les exceptions métier et les deux ports sortants. Aucun import Spring ici.

**Files:**
- Create: `src/main/resources/db/migration/V3__create_users_users.sql`
- Create: `src/main/java/xyz/sterenn/secondbrain/users/domain/Email.java`
- Create: `.../users/domain/EmailAttributeConverter.java`
- Create: `.../users/domain/InvalidEmailException.java`
- Create: `.../users/domain/User.java`
- Create: `.../users/domain/PasswordPolicy.java`
- Create: `.../users/domain/WeakPasswordException.java`
- Create: `.../users/domain/EmailAlreadyUsedException.java`
- Create: `.../users/domain/UserRepository.java`
- Create: `.../users/domain/PasswordHasher.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/domain/EmailTest.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/domain/PasswordPolicyTest.java`

**Interfaces:**
- Consumes: rien.
- Produces :
  - `record Email(String value)` — normalise (trim + minuscules) et valide dans son constructeur compact ; lève `InvalidEmailException`. Constante `Email.MAX_LENGTH = 320`.
  - `User.register(Email email, String passwordHash): User` — fabrique statique, positionne `verified = false`. Accesseurs `getId(): UUID`, `getEmail(): Email`, `getPasswordHash(): String`, `isVerified(): boolean`, `getCreatedAt(): Instant`.
  - `PasswordPolicy.isAcceptable(String rawPassword): boolean` (statique) + `MIN_LENGTH = 12`, `MAX_LENGTH = 128`.
  - `interface UserRepository { boolean existsByEmail(Email email); User save(User user); Optional<User> findByEmail(Email email); }`
  - `interface PasswordHasher { String hash(String rawPassword); boolean matches(String rawPassword, String hash); }`
  - `InvalidEmailException`, `WeakPasswordException`, `EmailAlreadyUsedException`, toutes `extends RuntimeException` avec un `getMessage()` affichable à l'utilisateur.
  - Les tâches 4, 5, 6 et 7 en dépendent.

- [ ] **Step 1: Écrire les tests qui échouent**

Créer `src/test/java/xyz/sterenn/secondbrain/users/domain/EmailTest.java` :

```java
package xyz.sterenn.secondbrain.users.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailTest {

    @Test
    void normalise_la_casse_et_les_espaces() {
        assertThat(new Email("  Alice@Example.COM  ").value()).isEqualTo("alice@example.com");
    }

    @Test
    void deux_emails_normalises_identiques_sont_egaux() {
        assertThat(new Email("Alice@Example.com")).isEqualTo(new Email("alice@example.com"));
    }

    @Test
    void refuse_un_email_sans_arobase() {
        assertThatThrownBy(() -> new Email("pas-un-email"))
            .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void refuse_un_email_sans_domaine_de_premier_niveau() {
        assertThatThrownBy(() -> new Email("alice@example"))
            .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void refuse_un_email_vide() {
        assertThatThrownBy(() -> new Email("   "))
            .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void refuse_un_email_null() {
        assertThatThrownBy(() -> new Email(null))
            .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void refuse_un_email_trop_long() {
        String trop_long = "a".repeat(310) + "@example.com";
        assertThatThrownBy(() -> new Email(trop_long))
            .isInstanceOf(InvalidEmailException.class);
    }
}
```

Créer `src/test/java/xyz/sterenn/secondbrain/users/domain/PasswordPolicyTest.java` :

```java
package xyz.sterenn.secondbrain.users.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void accepte_un_mot_de_passe_de_douze_caracteres() {
        assertThat(PasswordPolicy.isAcceptable("chevalpile42")).isTrue();
    }

    @Test
    void refuse_un_mot_de_passe_trop_court() {
        assertThat(PasswordPolicy.isAcceptable("chevalpile4")).isFalse();
    }

    @Test
    void accepte_un_mot_de_passe_a_la_longueur_maximale() {
        // 10 + 118 = 128 caractères
        assertThat(PasswordPolicy.isAcceptable("chevalpile" + "9".repeat(118))).isTrue();
    }

    @Test
    void refuse_un_mot_de_passe_trop_long() {
        // 10 + 119 = 129 caractères
        assertThat(PasswordPolicy.isAcceptable("chevalpile" + "9".repeat(119))).isFalse();
    }

    @Test
    void refuse_un_mot_de_passe_de_la_blocklist() {
        assertThat(PasswordPolicy.isAcceptable("motdepasse12")).isFalse();
    }

    @Test
    void refuse_un_mot_de_passe_de_la_blocklist_quelle_que_soit_la_casse() {
        assertThat(PasswordPolicy.isAcceptable("MotDePasse12")).isFalse();
    }

    @Test
    void refuse_un_mot_de_passe_null() {
        assertThat(PasswordPolicy.isAcceptable(null)).isFalse();
    }
}
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.domain.*"
```

Attendu : ÉCHEC à la compilation, `cannot find symbol: class Email`, `class PasswordPolicy`.

- [ ] **Step 3: Écrire le value object `Email`**

Créer `.../users/domain/InvalidEmailException.java` :

```java
package xyz.sterenn.secondbrain.users.domain;

/**
 * L'email soumis n'a pas une forme exploitable. Le message est destiné à être
 * affiché tel quel sous le champ email du formulaire.
 */
public class InvalidEmailException extends RuntimeException {

    public InvalidEmailException(String message) {
        super(message);
    }
}
```

Créer `.../users/domain/Email.java` :

```java
package xyz.sterenn.secondbrain.users.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Adresse email d'un compte, toujours normalisée : impossible de construire un
 * {@code Email} invalide, et deux écritures d'une même adresse sont égales.
 *
 * <p>La validation reste volontairement grossière : une expression régulière ne
 * décide pas de la validité réelle d'une adresse. La confirmation par email, prévue
 * dans un ticket dédié, est le seul contrôle qui compte.
 */
public record Email(String value) {

    /** 64 (partie locale) + 1 (@) + 255 (domaine), maximum de la RFC 5321. */
    public static final int MAX_LENGTH = 320;

    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        if (value == null) {
            throw new InvalidEmailException("L'email est obligatoire");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH) {
            throw new InvalidEmailException("L'email ne peut pas dépasser " + MAX_LENGTH + " caractères");
        }
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidEmailException("Format d'email invalide");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
```

Créer `.../users/domain/EmailAttributeConverter.java` :

```java
package xyz.sterenn.secondbrain.users.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Projette le value object {@link Email} sur une colonne texte. Placé dans le domaine
 * par cohérence avec {@link User}, qui porte déjà les annotations JPA (voir la note
 * « entité de domaine annotée JPA » du plan d'architecture).
 */
@Converter
public class EmailAttributeConverter implements AttributeConverter<Email, String> {

    @Override
    public String convertToDatabaseColumn(Email attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public Email convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new Email(dbData);
    }
}
```

- [ ] **Step 4: Écrire la politique de mot de passe**

Créer `.../users/domain/PasswordPolicy.java` :

```java
package xyz.sterenn.secondbrain.users.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Règle de robustesse du mot de passe, alignée sur NIST SP 800-63B : une longueur
 * minimale et un refus des mots de passe les plus courants, mais aucune règle de
 * composition (majuscule / chiffre / caractère spécial), qui pousse les utilisateurs
 * vers des variantes prévisibles sans gain réel.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    /**
     * Mots de passe refusés d'office, en minuscules. Volontairement courte : une
     * blocklist sérieuse (type Have I Been Pwned) fera l'objet d'un ticket dédié.
     * N'y mettre que des entrées d'au moins {@value #MIN_LENGTH} caractères — en deçà,
     * le contrôle de longueur les rejette déjà.
     */
    private static final Set<String> BLOCKLIST = Set.of(
        "password1234",
        "passwordpassword",
        "motdepasse12",
        "motdepasse123",
        "123456789012",
        "1234567890123",
        "azertyuiopqs",
        "qwertyuiopas",
        "administrator",
        "secondbrain1"
    );

    private PasswordPolicy() {
        // classe utilitaire
    }

    /**
     * @param rawPassword mot de passe en clair, éventuellement {@code null}
     * @return {@code true} si le mot de passe satisfait la politique
     */
    public static boolean isAcceptable(String rawPassword) {
        if (rawPassword == null) {
            return false;
        }
        if (rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            return false;
        }
        return !BLOCKLIST.contains(rawPassword.toLowerCase(Locale.ROOT));
    }
}
```

- [ ] **Step 5: Lancer les tests unitaires du domaine**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.domain.*"
```

Attendu : SUCCÈS, 14 tests (7 `EmailTest` + 7 `PasswordPolicyTest`).

- [ ] **Step 6: Écrire la migration**

Créer `src/main/resources/db/migration/V3__create_users_users.sql` :

```sql
-- Comptes utilisateurs. Le ticket impose le minimum d'information : ni nom, ni profil.
-- `verified` reste à false tant que l'email n'est pas confirmé — la validation de
-- compte fera l'objet d'un ticket dédié.
--
-- L'email est normalisé (trim + minuscules) par le value object Email avant d'atteindre
-- la base : une contrainte UNIQUE simple suffit donc à garantir l'unicité fonctionnelle.

CREATE TABLE users_users (
    id            UUID                     NOT NULL DEFAULT gen_random_uuid(),
    email         VARCHAR(320)             NOT NULL,
    password_hash VARCHAR(255)             NOT NULL,
    verified      BOOLEAN                  NOT NULL DEFAULT false,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_users_users PRIMARY KEY (id),
    CONSTRAINT uq_users_users_email UNIQUE (email)
);
```

- [ ] **Step 7: Écrire l'agrégat `User`**

Créer `.../users/domain/User.java` :

```java
package xyz.sterenn.secondbrain.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Compte utilisateur.
 *
 * <p>Le constructeur est privé : un compte ne se crée que par {@link #register}, ce qui
 * garantit l'invariant « un compte naît non vérifié ». {@code passwordHash} ne contient
 * jamais le mot de passe en clair.
 *
 * <p>Les annotations JPA dans le domaine sont un écart assumé au profit du minimalisme
 * (pas de classe miroir ni de mapper) — voir le plan d'architecture.
 */
@Entity
@Table(name = "users_users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    // length explicite : Hibernate tourne en ddl-auto=validate et compare les
    // métadonnées de l'entité au schéma créé par Flyway.
    @Convert(converter = EmailAttributeConverter.class)
    @Column(nullable = false, unique = true, length = Email.MAX_LENGTH)
    private Email email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean verified;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
        // requis par JPA
    }

    private User(Email email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.verified = false;
    }

    /**
     * Crée un compte nouvellement inscrit, dans l'état non vérifié.
     */
    public static User register(Email email, String passwordHash) {
        return new User(email, passwordHash);
    }

    public UUID getId() {
        return id;
    }

    public Email getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isVerified() {
        return verified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
```

- [ ] **Step 8: Écrire les exceptions métier restantes et les ports**

Créer `.../users/domain/WeakPasswordException.java` :

```java
package xyz.sterenn.secondbrain.users.domain;

/**
 * Le mot de passe soumis ne satisfait pas {@link PasswordPolicy}. Le message énonce
 * la règle pour que l'utilisateur puisse corriger sans deviner.
 */
public class WeakPasswordException extends RuntimeException {

    public WeakPasswordException() {
        super("Le mot de passe doit contenir entre " + PasswordPolicy.MIN_LENGTH
            + " et " + PasswordPolicy.MAX_LENGTH
            + " caractères et ne pas figurer parmi les mots de passe les plus courants");
    }
}
```

Créer `.../users/domain/EmailAlreadyUsedException.java` :

```java
package xyz.sterenn.secondbrain.users.domain;

/**
 * L'email soumis correspond déjà à un compte existant.
 */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(Email email) {
        super("Un compte existe déjà pour l'email " + email.value());
    }
}
```

Créer `.../users/domain/UserRepository.java` :

```java
package xyz.sterenn.secondbrain.users.domain;

import java.util.Optional;

/**
 * Port sortant vers le stockage des comptes. Le domaine énonce ce dont il a besoin ;
 * l'implémentation vit dans {@code users.infrastructure.persistence}.
 */
public interface UserRepository {

    boolean existsByEmail(Email email);

    /**
     * @throws EmailAlreadyUsedException si la contrainte d'unicité est violée à
     *         l'écriture — l'adapter traduit l'erreur technique en erreur métier
     */
    User save(User user);

    Optional<User> findByEmail(Email email);
}
```

Créer `.../users/domain/PasswordHasher.java` :

```java
package xyz.sterenn.secondbrain.users.domain;

/**
 * Port sortant vers l'algorithme de hachage. Le domaine ignore lequel est utilisé.
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
```

- [ ] **Step 9: Lancer toute la suite**

```bash
gtest test
```

Attendu : SUCCÈS. Le domaine compile ; aucun test d'intégration ne touche encore `users_users`, mais `SecondBrainApplicationTests` valide que Flyway applique V2 et V3 et qu'Hibernate valide le mapping de `User`. Si Hibernate remonte `Schema-validation: wrong column type`, corriger la migration — jamais `ddl-auto`.

- [ ] **Step 10: Commit**

```bash
git add src/main/resources/db/migration/V3__create_users_users.sql \
        src/main/java/xyz/sterenn/secondbrain/users/domain \
        src/test/java/xyz/sterenn/secondbrain/users/domain
git commit -m "feat: ajoute le domaine users (Email, User, politique de mot de passe, ports)"
```

---

### Task 4: Adapters d'infrastructure

Branche les deux ports du domaine sur du concret : Spring Data JPA d'un côté, l'encodeur de Spring Security de l'autre.

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/persistence/SpringDataUserRepository.java`
- Create: `.../users/infrastructure/persistence/JpaUserRepositoryAdapter.java`
- Create: `.../users/infrastructure/security/BCryptPasswordHasher.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/persistence/JpaUserRepositoryAdapterTest.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/security/BCryptPasswordHasherTest.java`

**Interfaces:**
- Consumes: `User`, `Email`, `UserRepository`, `PasswordHasher`, `EmailAlreadyUsedException` (tâche 3).
- Produces: un bean `UserRepository` et un bean `PasswordHasher` dans le contexte. Les tâches 5, 6 et 7 les consomment via les ports, jamais via ces classes.

- [ ] **Step 1: Écrire les tests qui échouent**

Créer `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/persistence/JpaUserRepositoryAdapterTest.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.users.domain.Email;
import xyz.sterenn.secondbrain.users.domain.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.User;
import xyz.sterenn.secondbrain.users.domain.UserRepository;

/**
 * {@code @Transactional} fait rouler chaque test en arrière : la PostgreSQL
 * Testcontainers est partagée par toute la suite.
 *
 * <p>Le test injecte le <em>port</em> {@link UserRepository}, pas l'adapter : c'est le
 * contrat du domaine qui est vérifié.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaUserRepositoryAdapterTest {

    @Autowired
    private UserRepository users;

    @Test
    void persiste_un_compte_dans_un_etat_non_verifie() {
        User saved = users.save(User.register(new Email("alice@example.com"), "empreinte"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo(new Email("alice@example.com"));
        assertThat(saved.getPasswordHash()).isEqualTo("empreinte");
        assertThat(saved.isVerified()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void detecte_un_email_deja_pris() {
        users.save(User.register(new Email("bob@example.com"), "empreinte"));

        assertThat(users.existsByEmail(new Email("bob@example.com"))).isTrue();
        assertThat(users.existsByEmail(new Email("carol@example.com"))).isFalse();
    }

    @Test
    void retrouve_un_compte_par_son_email() {
        users.save(User.register(new Email("dave@example.com"), "empreinte"));

        assertThat(users.findByEmail(new Email("dave@example.com")))
            .isPresent()
            .hasValueSatisfying(user -> assertThat(user.isVerified()).isFalse());
        assertThat(users.findByEmail(new Email("inconnu@example.com"))).isEmpty();
    }

    @Test
    void traduit_la_violation_d_unicite_en_erreur_metier() {
        users.save(User.register(new Email("erin@example.com"), "empreinte"));

        assertThatThrownBy(() -> users.save(User.register(new Email("erin@example.com"), "autre")))
            .isInstanceOf(EmailAlreadyUsedException.class);
    }
}
```

Créer `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/security/BCryptPasswordHasherTest.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.users.domain.PasswordHasher;

class BCryptPasswordHasherTest {

    private final PasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void produit_une_empreinte_prefixee_de_l_algorithme() {
        String empreinte = hasher.hash("chevalpile42");

        assertThat(empreinte).startsWith("{bcrypt}$2a$");
        assertThat(empreinte).isNotEqualTo("chevalpile42");
    }

    @Test
    void reconnait_le_mot_de_passe_d_origine() {
        String empreinte = hasher.hash("chevalpile42");

        assertThat(hasher.matches("chevalpile42", empreinte)).isTrue();
        assertThat(hasher.matches("autrechose42", empreinte)).isFalse();
    }
}
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.infrastructure.*"
```

Attendu : ÉCHEC à la compilation, `cannot find symbol: class BCryptPasswordHasher`, et `NoSuchBeanDefinitionException: UserRepository` si la compilation passe.

- [ ] **Step 3: Écrire l'adapter de persistance**

Créer `.../users/infrastructure/persistence/SpringDataUserRepository.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.sterenn.secondbrain.users.domain.Email;
import xyz.sterenn.secondbrain.users.domain.User;

/**
 * Détail Spring Data, volontairement package-private : rien hors de ce package ne doit
 * en dépendre. Les requêtes dérivées acceptent un {@link Email} — le paramètre traverse
 * l'{@code EmailAttributeConverter} comme la colonne.
 */
interface SpringDataUserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(Email email);

    Optional<User> findByEmail(Email email);
}
```

Créer `.../users/infrastructure/persistence/JpaUserRepositoryAdapter.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.users.domain.Email;
import xyz.sterenn.secondbrain.users.domain.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.User;
import xyz.sterenn.secondbrain.users.domain.UserRepository;

/**
 * Adapter du port {@link UserRepository}. Son autre rôle est de traduire les erreurs
 * techniques en erreurs métier, pour qu'aucune exception Spring ne remonte à
 * l'application ni au domaine.
 */
@Component
public class JpaUserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository jpa;

    JpaUserRepositoryAdapter(SpringDataUserRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpa.existsByEmail(email);
    }

    @Override
    public User save(User user) {
        try {
            // saveAndFlush : sans flush explicite, la violation d'unicité ne surviendrait
            // qu'au commit, hors de portée du try/catch.
            return jpa.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException(user.getEmail());
        }
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpa.findByEmail(email);
    }
}
```

- [ ] **Step 4: Écrire l'adapter de hachage**

Créer `.../users/infrastructure/security/BCryptPasswordHasher.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.security;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.users.domain.PasswordHasher;

/**
 * Adapter du port {@link PasswordHasher}, adossé à l'encodeur délégant de Spring
 * Security : les empreintes sont préfixées de l'algorithme ({@code {bcrypt}...}), ce
 * qui permettra d'en changer sans invalider les mots de passe existants.
 *
 * <p>BCrypt ignore les octets au-delà du 72e : deux mots de passe très longs partageant
 * leurs 72 premiers octets sont équivalents. Comportement standard, acceptable au regard
 * du minimum de 12 caractères imposé par la politique.
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        return encoder.matches(rawPassword, hash);
    }
}
```

- [ ] **Step 5: Lancer les tests pour vérifier qu'ils passent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.infrastructure.*"
```

Attendu : SUCCÈS, 6 tests (4 + 2).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/xyz/sterenn/secondbrain/users/infrastructure \
        src/test/java/xyz/sterenn/secondbrain/users/infrastructure
git commit -m "feat: branche les ports users sur JPA et sur l'encodeur Spring Security"
```

---

### Task 5: Commande `RegisterUser`

Les trois scénarios Gherkin, côté écriture. Le handler orchestre le domaine, sans jamais toucher à Spring ni à la transaction — celle-ci est ouverte par le bus, ce que cette tâche vérifie explicitement.

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/users/application/command/RegisterUser.java`
- Create: `.../users/application/command/RegisterUserHandler.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/application/command/RegisterUserHandlerTest.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/application/command/CommandBusTransactionTest.java`

**Interfaces:**
- Consumes: `Command`, `CommandHandler`, `CommandBus` (tâche 2) ; `Email`, `User`, `UserRepository`, `PasswordHasher`, `PasswordPolicy` et les trois exceptions métier (tâche 3).
- Produces: `record RegisterUser(String email, String rawPassword) implements Command`. La tâche 7 la dispatche.

**Ordre de validation** (un seul motif d'erreur remonte à la fois) : format de l'email → robustesse du mot de passe → unicité de l'email. Les deux premiers contrôles sont locaux, le troisième coûte un aller-retour base.

- [ ] **Step 1: Écrire les tests qui échouent**

Créer `src/test/java/xyz/sterenn/secondbrain/users/application/command/RegisterUserHandlerTest.java` :

```java
package xyz.sterenn.secondbrain.users.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.domain.Email;
import xyz.sterenn.secondbrain.users.domain.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.InvalidEmailException;
import xyz.sterenn.secondbrain.users.domain.PasswordHasher;
import xyz.sterenn.secondbrain.users.domain.User;
import xyz.sterenn.secondbrain.users.domain.UserRepository;
import xyz.sterenn.secondbrain.users.domain.WeakPasswordException;

/**
 * La commande est toujours dispatchée par le bus, jamais appelée en direct : c'est le
 * chemin réel de production.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class RegisterUserHandlerTest {

    private static final String MOT_DE_PASSE_VALIDE = "chevalpile42";

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordHasher passwordHasher;

    @Test
    void cree_un_compte_non_verifie_avec_un_mot_de_passe_hache() {
        commandBus.dispatch(new RegisterUser("alice@example.com", MOT_DE_PASSE_VALIDE));

        User created = users.findByEmail(new Email("alice@example.com")).orElseThrow();
        assertThat(created.getId()).isNotNull();
        assertThat(created.isVerified()).isFalse();
        assertThat(created.getPasswordHash()).isNotEqualTo(MOT_DE_PASSE_VALIDE);
        assertThat(passwordHasher.matches(MOT_DE_PASSE_VALIDE, created.getPasswordHash())).isTrue();
    }

    @Test
    void normalise_l_email_avant_de_le_stocker() {
        commandBus.dispatch(new RegisterUser("  Bob@Example.COM  ", MOT_DE_PASSE_VALIDE));

        assertThat(users.existsByEmail(new Email("bob@example.com"))).isTrue();
    }

    @Test
    void refuse_un_email_deja_utilise() {
        commandBus.dispatch(new RegisterUser("carol@example.com", MOT_DE_PASSE_VALIDE));

        assertThatThrownBy(() ->
            commandBus.dispatch(new RegisterUser("CAROL@Example.com", MOT_DE_PASSE_VALIDE)))
            .isInstanceOf(EmailAlreadyUsedException.class)
            .hasMessageContaining("carol@example.com");
    }

    @Test
    void refuse_un_mot_de_passe_trop_faible_sans_creer_de_compte() {
        assertThatThrownBy(() -> commandBus.dispatch(new RegisterUser("dave@example.com", "court")))
            .isInstanceOf(WeakPasswordException.class);

        assertThat(users.existsByEmail(new Email("dave@example.com"))).isFalse();
    }

    @Test
    void refuse_un_email_mal_forme() {
        assertThatThrownBy(() -> commandBus.dispatch(new RegisterUser("pas-un-email", MOT_DE_PASSE_VALIDE)))
            .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void valide_le_mot_de_passe_avant_l_unicite_de_l_email() {
        commandBus.dispatch(new RegisterUser("erin@example.com", MOT_DE_PASSE_VALIDE));

        assertThatThrownBy(() -> commandBus.dispatch(new RegisterUser("erin@example.com", "court")))
            .isInstanceOf(WeakPasswordException.class);
    }
}
```

Créer `src/test/java/xyz/sterenn/secondbrain/users/application/command/CommandBusTransactionTest.java` :

```java
package xyz.sterenn.secondbrain.users.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.Command;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.users.domain.Email;
import xyz.sterenn.secondbrain.users.domain.User;
import xyz.sterenn.secondbrain.users.domain.UserRepository;

/**
 * Vérifie l'exigence centrale du CommandBus : tout le handler s'exécute dans une seule
 * transaction SQL, et un échec en cours de route annule ce qui a déjà été écrit.
 *
 * <p>Ce test ne porte volontairement <strong>pas</strong> {@code @Transactional} : une
 * transaction de test englobante masquerait le rollback qu'on cherche à observer. Le
 * nettoyage est donc explicite.
 */
@Import({TestcontainersConfiguration.class, CommandBusTransactionTest.HandlerDeTest.class})
@SpringBootTest
class CommandBusTransactionTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Filet de sécurité : si le rollback ne fonctionnait pas, la ligne survivrait et
     * ferait échouer les autres tests de la suite. Le nettoyage est explicite puisque
     * ce test refuse la transaction englobante de {@code @Transactional}.
     */
    @AfterEach
    void nettoyer() {
        jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", "frank@example.com");
    }

    @Test
    void annule_les_ecritures_quand_le_handler_echoue() {
        assertThatThrownBy(() -> commandBus.dispatch(new EchouerApresEcriture("frank@example.com")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("échec volontaire");

        assertThat(users.existsByEmail(new Email("frank@example.com"))).isFalse();
    }

    record EchouerApresEcriture(String email) implements Command {
    }

    /**
     * Écrit puis lève une {@link RuntimeException} : seules les exceptions non checked
     * déclenchent un rollback avec les réglages Spring par défaut.
     */
    static class EchouerApresEcritureHandler implements CommandHandler<EchouerApresEcriture> {

        private final UserRepository users;

        EchouerApresEcritureHandler(UserRepository users) {
            this.users = users;
        }

        @Override
        public void handle(EchouerApresEcriture command) {
            users.save(User.register(new Email(command.email()), "empreinte"));
            throw new IllegalStateException("échec volontaire");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HandlerDeTest {

        @Bean
        EchouerApresEcritureHandler echouerApresEcritureHandler(UserRepository users) {
            return new EchouerApresEcritureHandler(users);
        }
    }
}
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.application.command.*"
```

Attendu : ÉCHEC à la compilation, `cannot find symbol: class RegisterUser`.

- [ ] **Step 3: Écrire la commande**

Créer `.../users/application/command/RegisterUser.java` :

```java
package xyz.sterenn.secondbrain.users.application.command;

import xyz.sterenn.secondbrain.shared.bus.Command;

/**
 * Inscription d'un nouveau compte.
 *
 * <p>Les champs sont des {@code String} bruts, tels que saisis : c'est le handler qui
 * les convertit en value objects du domaine. Une commande transporte l'intention, elle
 * ne la valide pas.
 *
 * @param email       email saisi, non normalisé
 * @param rawPassword mot de passe en clair — ne jamais logguer une instance de cette commande
 */
public record RegisterUser(String email, String rawPassword) implements Command {
}
```

- [ ] **Step 4: Écrire le handler**

Créer `.../users/application/command/RegisterUserHandler.java` :

```java
package xyz.sterenn.secondbrain.users.application.command;

import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.users.domain.Email;
import xyz.sterenn.secondbrain.users.domain.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.PasswordHasher;
import xyz.sterenn.secondbrain.users.domain.PasswordPolicy;
import xyz.sterenn.secondbrain.users.domain.User;
import xyz.sterenn.secondbrain.users.domain.UserRepository;
import xyz.sterenn.secondbrain.users.domain.WeakPasswordException;

/**
 * Orchestre l'inscription : conversion en value objects, contrôles métier, écriture.
 *
 * <p>Aucun {@code @Transactional} ici — la transaction est ouverte par
 * {@code SpringCommandBus.dispatch} et couvre tout ce qui suit.
 */
@Component
public class RegisterUserHandler implements CommandHandler<RegisterUser> {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;

    public RegisterUserHandler(UserRepository users, PasswordHasher passwordHasher) {
        this.users = users;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void handle(RegisterUser command) {
        // Le constructeur d'Email normalise et lève InvalidEmailException si besoin.
        Email email = new Email(command.email());

        // Contrôles locaux d'abord, aller-retour base ensuite.
        if (!PasswordPolicy.isAcceptable(command.rawPassword())) {
            throw new WeakPasswordException();
        }
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }

        users.save(User.register(email, passwordHasher.hash(command.rawPassword())));
    }
}
```

- [ ] **Step 5: Lancer les tests pour vérifier qu'ils passent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.application.command.*"
```

Attendu : SUCCÈS, 7 tests (6 `RegisterUserHandlerTest` + 1 `CommandBusTransactionTest`).

Si `annule_les_ecritures_quand_le_handler_echoue` échoue, c'est que le bus n'est pas proxifié : vérifier que `CommandBus` est bien injecté via son interface et que `SpringCommandBus` est déclaré par `BusConfiguration` et non instancié à la main.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/xyz/sterenn/secondbrain/users/application \
        src/test/java/xyz/sterenn/secondbrain/users/application
git commit -m "feat: ajoute la commande RegisterUser et son handler transactionnel"
```

---

### Task 6: Query `FindUserByEmail`

Le pendant lecture. Le ticket n'en a pas besoin — cette query existe pour que le `QueryBus` soit livré exercé et testé, et pour servir de gabarit au ticket login.

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/users/application/query/UserView.java`
- Create: `.../users/application/query/FindUserByEmail.java`
- Create: `.../users/application/query/FindUserByEmailHandler.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/application/query/FindUserByEmailHandlerTest.java`

**Interfaces:**
- Consumes: `Query`, `QueryHandler`, `QueryBus` (tâche 2) ; `Email`, `UserRepository` (tâche 3) ; `RegisterUser` (tâche 5, pour alimenter le test).
- Produces: `record UserView(UUID id, String email, boolean verified, Instant createdAt)` et `record FindUserByEmail(String email) implements Query<Optional<UserView>>`.

- [ ] **Step 1: Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/users/application/query/FindUserByEmailHandlerTest.java` :

```java
package xyz.sterenn.secondbrain.users.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.users.application.command.RegisterUser;
import xyz.sterenn.secondbrain.users.domain.InvalidEmailException;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class FindUserByEmailHandlerTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private QueryBus queryBus;

    @Test
    void renvoie_la_vue_d_un_compte_existant() {
        commandBus.dispatch(new RegisterUser("grace@example.com", "chevalpile42"));

        Optional<UserView> vue = queryBus.ask(new FindUserByEmail("GRACE@Example.com"));

        assertThat(vue).isPresent();
        assertThat(vue.get().email()).isEqualTo("grace@example.com");
        assertThat(vue.get().verified()).isFalse();
        assertThat(vue.get().id()).isNotNull();
        assertThat(vue.get().createdAt()).isNotNull();
    }

    @Test
    void renvoie_vide_pour_un_email_inconnu() {
        assertThat(queryBus.ask(new FindUserByEmail("inconnu@example.com"))).isEmpty();
    }

    @Test
    void refuse_un_email_mal_forme() {
        assertThatThrownBy(() -> queryBus.ask(new FindUserByEmail("pas-un-email")))
            .isInstanceOf(InvalidEmailException.class);
    }
}
```

- [ ] **Step 2: Lancer le test pour vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.application.query.*"
```

Attendu : ÉCHEC à la compilation, `cannot find symbol: class FindUserByEmail`, `class UserView`.

- [ ] **Step 3: Écrire le modèle de lecture et la query**

Créer `.../users/application/query/UserView.java` :

```java
package xyz.sterenn.secondbrain.users.application.query;

import java.time.Instant;
import java.util.UUID;

/**
 * Projection de lecture d'un compte. Distincte de l'agrégat {@code User} : elle
 * n'expose jamais l'empreinte du mot de passe et peut évoluer au rythme des écrans,
 * sans toucher au domaine.
 */
public record UserView(UUID id, String email, boolean verified, Instant createdAt) {
}
```

Créer `.../users/application/query/FindUserByEmail.java` :

```java
package xyz.sterenn.secondbrain.users.application.query;

import java.util.Optional;
import xyz.sterenn.secondbrain.shared.bus.Query;

/**
 * Recherche d'un compte par son email. Renvoie un {@link Optional} vide plutôt qu'une
 * exception : l'absence de compte est un résultat, pas une erreur.
 *
 * @param email email saisi, non normalisé
 */
public record FindUserByEmail(String email) implements Query<Optional<UserView>> {
}
```

- [ ] **Step 4: Écrire le handler**

Créer `.../users/application/query/FindUserByEmailHandler.java` :

```java
package xyz.sterenn.secondbrain.users.application.query;

import java.util.Optional;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.shared.bus.QueryHandler;
import xyz.sterenn.secondbrain.users.domain.Email;
import xyz.sterenn.secondbrain.users.domain.User;
import xyz.sterenn.secondbrain.users.domain.UserRepository;

/**
 * Aucun {@code @Transactional} ici : {@code SpringQueryBus.ask} ouvre déjà une
 * transaction en lecture seule.
 */
@Component
public class FindUserByEmailHandler implements QueryHandler<FindUserByEmail, Optional<UserView>> {

    private final UserRepository users;

    public FindUserByEmailHandler(UserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<UserView> handle(FindUserByEmail query) {
        return users.findByEmail(new Email(query.email())).map(FindUserByEmailHandler::toView);
    }

    private static UserView toView(User user) {
        return new UserView(user.getId(), user.getEmail().value(), user.isVerified(), user.getCreatedAt());
    }
}
```

- [ ] **Step 5: Lancer le test pour vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.application.query.*"
```

Attendu : SUCCÈS, 3 tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/xyz/sterenn/secondbrain/users/application/query \
        src/test/java/xyz/sterenn/secondbrain/users/application/query
git commit -m "feat: ajoute la query FindUserByEmail et son handler"
```

---

### Task 7: Formulaire HTML d'inscription

L'adapter entrant : il traduit un POST de formulaire en `RegisterUser`, dispatche, et retraduit les exceptions métier en erreurs de champ. Aucune logique métier ici.

**Files:**
- Modify: `build.gradle.kts` (starter Thymeleaf)
- Create: `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/RegistrationForm.java`
- Create: `.../users/infrastructure/web/RegistrationController.java`
- Create: `src/main/resources/templates/register.html`
- Modify: `README.md`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/web/RegistrationControllerTest.java`

**Interfaces:**
- Consumes: `CommandBus` (tâche 2) ; `RegisterUser` (tâche 5) ; `InvalidEmailException`, `WeakPasswordException`, `EmailAlreadyUsedException` (tâche 3).
- Produces: `GET /register` (vue `register`, attribut de modèle `registrationForm`) et `POST /register` (`email` + `password` en `application/x-www-form-urlencoded`).

- [ ] **Step 1: Ajouter la dépendance Thymeleaf**

Dans `build.gradle.kts`, sous le commentaire `// Web / REST`, ajouter une ligne après le starter `validation` :

```kotlin
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
```

Pas d'entrée dans `libs.versions.toml` : la version vient du BOM Spring Boot.

- [ ] **Step 2: Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/web/RegistrationControllerTest.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.users.application.query.FindUserByEmail;
import xyz.sterenn.secondbrain.users.application.query.UserView;

/**
 * Couvre les trois scénarios Gherkin du ticket au niveau HTTP.
 * CSRF est désactivé côté application : aucun jeton à fournir.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrationControllerTest {

    private static final String MOT_DE_PASSE_VALIDE = "chevalpile42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QueryBus queryBus;

    @Test
    void affiche_le_formulaire_a_un_visiteur_anonyme() throws Exception {
        mockMvc.perform(get("/register"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeExists("registrationForm"));
    }

    @Test
    void cree_le_compte_et_redirige_en_cas_de_succes() throws Exception {
        mockMvc.perform(post("/register")
                .param("email", "alice@example.com")
                .param("password", MOT_DE_PASSE_VALIDE))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/register?success"));

        Optional<UserView> vue = queryBus.ask(new FindUserByEmail("alice@example.com"));
        assertThat(vue).isPresent();
        assertThat(vue.get().verified()).isFalse();
    }

    @Test
    void reaffiche_le_formulaire_avec_une_erreur_si_l_email_est_deja_utilise() throws Exception {
        mockMvc.perform(post("/register")
            .param("email", "bob@example.com")
            .param("password", MOT_DE_PASSE_VALIDE));

        mockMvc.perform(post("/register")
                .param("email", "bob@example.com")
                .param("password", MOT_DE_PASSE_VALIDE))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeHasFieldErrors("registrationForm", "email"));
    }

    @Test
    void reaffiche_le_formulaire_avec_une_erreur_si_le_mot_de_passe_est_faible() throws Exception {
        mockMvc.perform(post("/register")
                .param("email", "carol@example.com")
                .param("password", "court"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeHasFieldErrors("registrationForm", "password"));

        assertThat(queryBus.ask(new FindUserByEmail("carol@example.com"))).isEmpty();
    }

    @Test
    void reaffiche_le_formulaire_avec_une_erreur_si_l_email_est_mal_forme() throws Exception {
        mockMvc.perform(post("/register")
                .param("email", "pas-un-email")
                .param("password", MOT_DE_PASSE_VALIDE))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeHasFieldErrors("registrationForm", "email"));
    }

    @Test
    void reaffiche_le_formulaire_avec_une_erreur_si_un_champ_est_vide() throws Exception {
        mockMvc.perform(post("/register")
                .param("email", "")
                .param("password", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeHasFieldErrors("registrationForm", "email", "password"));
    }
}
```

- [ ] **Step 3: Lancer le test pour vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.infrastructure.web.*"
```

Attendu : ÉCHEC — `affiche_le_formulaire_a_un_visiteur_anonyme` renvoie 404, le contrôleur n'existe pas.

- [ ] **Step 4: Écrire le bean de formulaire**

Créer `.../users/infrastructure/web/RegistrationForm.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Support de liaison du formulaire d'inscription.
 *
 * <p>Classe mutable à accesseurs JavaBean, et non un record : {@code th:field} lit la
 * valeur via {@code BeanWrapper}, qui exige {@code getEmail()} et non {@code email()}.
 *
 * <p>La validation portée ici se limite à « le champ est rempli ». Le format de l'email
 * est l'affaire du value object {@code Email}, la robustesse du mot de passe celle de
 * {@code PasswordPolicy} : dupliquer ces règles ici les ferait diverger.
 */
public class RegistrationForm {

    @NotBlank(message = "L'email est obligatoire")
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
```

- [ ] **Step 5: Écrire le contrôleur**

Créer `.../users/infrastructure/web/RegistrationController.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.application.command.RegisterUser;
import xyz.sterenn.secondbrain.users.domain.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.InvalidEmailException;
import xyz.sterenn.secondbrain.users.domain.WeakPasswordException;

/**
 * Adapter entrant : traduit un formulaire HTML en commande, puis les exceptions métier
 * en erreurs de champ. Aucune règle métier ne vit ici.
 */
@Controller
@RequestMapping("/register")
public class RegistrationController {

    private final CommandBus commandBus;

    public RegistrationController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("registrationForm", new RegistrationForm());
        return "register";
    }

    @PostMapping
    public String register(@Valid @ModelAttribute RegistrationForm registrationForm,
                           BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            commandBus.dispatch(
                new RegisterUser(registrationForm.getEmail(), registrationForm.getPassword()));
        } catch (InvalidEmailException | EmailAlreadyUsedException e) {
            bindingResult.rejectValue("email", "email.invalide", e.getMessage());
            return "register";
        } catch (WeakPasswordException e) {
            bindingResult.rejectValue("password", "password.faible", e.getMessage());
            return "register";
        }

        // Redirect-after-post : un rafraîchissement ne renvoie pas le formulaire.
        return "redirect:/register?success";
    }
}
```

Le nom d'attribut de modèle déduit de `@ModelAttribute RegistrationForm registrationForm` est `registrationForm` : il doit coïncider avec celui posé par `showForm` et avec `th:object` dans le template.

- [ ] **Step 6: Écrire le template**

Créer `src/main/resources/templates/register.html` :

```html
<!DOCTYPE html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Créer mon compte</title>
</head>
<body>
<h1>Créer mon compte</h1>

<p th:if="${param.success}">
    Votre compte a été créé. Il reste à vérifier.
</p>

<form th:action="@{/register}" th:object="${registrationForm}" method="post">
    <p>
        <label for="email">Email</label><br>
        <input type="text" id="email" th:field="*{email}">
        <span th:if="${#fields.hasErrors('email')}" th:errors="*{email}"></span>
    </p>
    <p>
        <label for="password">Mot de passe</label><br>
        <!-- Thymeleaf ne réémet jamais la valeur d'un input de type password. -->
        <input type="password" id="password" th:field="*{password}">
        <span th:if="${#fields.hasErrors('password')}" th:errors="*{password}"></span>
    </p>
    <p>
        <button type="submit">Créer mon compte</button>
    </p>
</form>
</body>
</html>
```

`type="text"` et non `type="email"` sur le champ email, et pas d'attribut `required` : la validation du navigateur court-circuiterait les tests des cas d'erreur côté serveur, qui sont ceux que le ticket demande de garantir.

- [ ] **Step 7: Lancer les tests pour vérifier qu'ils passent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.infrastructure.web.*"
```

Attendu : SUCCÈS, 6 tests.

- [ ] **Step 8: Lancer toute la suite**

```bash
gtest build
```

Attendu : SUCCÈS. Récapitulatif : 1 (`SecondBrainApplicationTests`) + 2 (`SecurityConfigTest`) + 5 (bus) + 14 (domaine) + 6 (infrastructure) + 7 (commande) + 3 (query) + 6 (web) = **44 tests**.

- [ ] **Step 9: Vérifier à la main dans le navigateur**

```bash
docker compose up --build -d
docker compose logs -f app   # attendre "Started SecondBrainApplication"
```

Ouvrir http://localhost:8080/register et dérouler les trois scénarios du ticket :
1. `alice@example.com` / `chevalpile42` → message de succès.
2. Le même email à nouveau → « Un compte existe déjà pour l'email alice@example.com ».
3. `bob@example.com` / `court` → message sur la longueur du mot de passe.

Contrôler la ligne en base via Adminer (http://localhost:8081, serveur `db`, base/user/mdp `second_brain`), table `users_users` : `verified` vaut `false` et `password_hash` commence par `{bcrypt}$2a$`. Vérifier au passage que la table `note` a disparu (migration V2).

Puis `docker compose down`.

- [ ] **Step 10: Mettre à jour le README**

Dans le tableau des URLs de « Démarrage rapide », ajouter après `| API | http://localhost:8080 |` :

```
| Création de compte | http://localhost:8080/register |
```

Remplacer intégralement le bloc de code de la section « Structure » par :

```
src/main/java/xyz/sterenn/secondbrain/
├── SecondBrainApplication.java
├── config/                     # SecurityConfig, OpenApiConfig
├── shared/bus/                 # CommandBus (transactionnel) et QueryBus
└── users/                      # bounded context, en 3 couches
    ├── domain/                 # User, Email, PasswordPolicy, ports
    ├── application/            # command/ et query/ + leurs handlers
    └── infrastructure/         # adapters : persistence/, security/, web/
src/main/resources/
├── application.yml             # config commune (pilotée par variables d'env)
├── application-dev.yml         # profil dev
├── templates/register.html     # formulaire d'inscription (Thymeleaf)
└── db/migration/               # migrations Flyway
```

Ajouter ensuite une section, juste après « Structure » :

```markdown
## Architecture

Hexagonale, un dossier par couche et par bounded context :

- **domain** — entités, value objects, règles métier et **ports** (interfaces). Ne dépend
  de rien d'autre que du JDK.
- **application** — une commande ou une query par intention, avec son handler. Aucune
  logique métier : le handler orchestre le domaine.
- **infrastructure** — les **adapters** qui implémentent les ports (JPA, hachage) et les
  adapters entrants (contrôleurs web).

CQRS minimal : `CommandBus.dispatch` pour écrire, `QueryBus.ask` pour lire. Les deux sont
synchrones et routent vers un handler unique, résolu au démarrage par son type générique.

**La transaction SQL est portée par le `CommandBus`** : `dispatch` est `@Transactional`,
donc tout le handler s'exécute dans une seule transaction et la moindre exception annule
l'ensemble. Corollaire : **ne jamais annoter un handler avec `@Transactional`**.
```

- [ ] **Step 11: Commit**

```bash
git add build.gradle.kts \
        src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web \
        src/main/resources/templates/register.html \
        src/test/java/xyz/sterenn/secondbrain/users/infrastructure/web \
        README.md
git commit -m "feat: ajoute le formulaire HTML de création de compte"
```

---

## Couverture du ticket

| Attendu du ticket | Où |
|---|---|
| Supprimer l'auth HTTP Basic si besoin | Tâche 1 |
| Scénario nominal : compte créé avec email + mot de passe | Tâche 5 (`cree_un_compte_non_verifie_avec_un_mot_de_passe_hache`), tâche 7 (`cree_le_compte_et_redirige_en_cas_de_succes`) |
| Bouton « Créer mon compte » | Tâche 7, `register.html` |
| Scénario email déjà utilisé → erreur, pas de compte | Tâche 4 (`traduit_la_violation_d_unicite_en_erreur_metier`), tâche 5 (`refuse_un_email_deja_utilise`), tâche 7 |
| Scénario mot de passe trop faible → erreur, pas de compte | Tâche 3 (7 tests), tâche 5 (`refuse_un_mot_de_passe_trop_faible_sans_creer_de_compte`), tâche 7 |
| Réussi si : compte créé en état non vérifié | Tâche 3 (invariant dans `User.register`), tâche 4, tâche 5, vérification manuelle tâche 7 step 9 |
| Minimum d'info dans `users_users` | Tâche 3, migration V3 : 5 colonnes |
| Hors-périmètre : social connect, login, validation de compte | Non implémentés |
| Hors-périmètre : UI HTML basique | Thymeleaf sans CSS ni JS |

## Couverture de la demande d'architecture

| Attendu | Où |
|---|---|
| Dossier `application` (command, query, handlers) | `users/application/{command,query}/` — tâches 5 et 6 |
| Dossier `domain` (entities, value objects, ports) | `users/domain/` — tâche 3 |
| Dossier `infrastructure` (adapters et autres) | `users/infrastructure/{persistence,security,web}/` — tâches 4 et 7 |
| Command bus synchrone | `SpringCommandBus` — tâche 2 |
| Query bus synchrone | `SpringQueryBus` — tâche 2 |
| Toute la commande dans une transaction SQL | `@Transactional` sur `dispatch`, prouvé par `CommandBusTransactionTest` — tâches 2 et 5 |
| Implémentation minimaliste | Aucune dépendance ajoutée hors Thymeleaf ; les bus tiennent en deux classes de ~40 lignes |
