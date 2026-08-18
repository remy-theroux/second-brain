# Migration du parcours public vers le front Vue — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Faire vivre l'intégralité du parcours utilisateur — accueil, inscription, vérification, connexion, espace connecté — dans l'application Vue, derrière une origine unique, l'application Java n'exposant plus que des routes d'API et une redirection.

**Architecture:** Hexagonale par bounded context, CQRS sur deux bus synchrones. Cette migration ne touche **que** `users/infrastructure/web`, `config/SecurityConfig`, le front et l'outillage. Le domaine, l'application, la persistance, la sécurité et l'adapter email ne changent pas d'une ligne — c'est le contrôle qui vaut vérification de l'architecture.

**Tech Stack:** Java 25 · Spring Boot 4.0.7 (MVC, Security, OAuth2 Resource Server, Validation) · Vue 3 · Vite · pinia · vue-router · Vitest · Traefik v3 · nginx · Docker Compose.

**Spec:** `docs/superpowers/specs/2026-08-18-migration-front-vue-design.md`

## Global Constraints

- **Aucun JDK, aucun Gradle, aucun Node sur la machine hôte.** Définir `gtest` et `gfront` (voir `CLAUDE.md`, section « Commandes ») une fois par session avant toute commande.
- **Tout est en français** : commentaires, Javadoc, messages d'exception, libellés d'interface, noms de méthodes de test, messages de commit. Les noms de classes, méthodes de production, packages, fonctions et variables JS restent en anglais.
- **Ne pas changer les versions** (`build.gradle.kts`, `gradle/libs.versions.toml`, `frontend/package.json`). Spring Boot 4 a redécoupé ses modules : `@AutoConfigureMockMvc` vit dans `org.springframework.boot.webmvc.test.autoconfigure`, les annotations Jackson restent sous `com.fasterxml.jackson.annotation`.
- **Jamais de `@Transactional` sur un handler** : la transaction appartient au bus.
- **Une classe de contrôleur ne porte qu'un seul mapping**, nommée par l'intention de la route.
- **Un contrôleur ne contient aucune règle métier** : il valide la présence des champs, dispatche, et traduit les exceptions métier.
- **Pattern d'intégration imposé** : `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`. Ne pas introduire `@DataJpaTest`.
- **Noms de méthodes de test en français avec underscores**, assertions AssertJ côté Java.
- **Front** : JavaScript (pas TypeScript), aucun CSS, `<script setup>`, `ref` plutôt que `reactive`, alias `@` → `frontend/src`, `src/api/` seul module qui appelle `fetch`.
- **Un commit par tâche**, préfixe conventionnel en minuscule (`feat:`, `fix:`, `refactor:`, `conf:`, `test:`, `docs:`), tests verts.

---

## Structure des fichiers

**Backend**

| Fichier | Responsabilité |
|---|---|
| `users/infrastructure/web/RegistrationRequest.java` (créé) | Corps JSON de l'inscription, record validé `@NotBlank`, `toString()` masqué |
| `users/infrastructure/web/ValidationErrorResponse.java` (créé) | Corps `422` : la carte champ → message |
| `users/infrastructure/web/ErrorResponse.java` (créé) | Corps `503` : un message global |
| `users/infrastructure/web/RegisterUserController.java` (modifié) | `POST /api/registrations` |
| `users/infrastructure/web/VerifyAccountController.java` (modifié) | `GET /verification` → `302` |
| `config/SecurityConfig.java` (modifié) | `/api/registrations` déclarée publique |
| `users/infrastructure/web/RegistrationForm.java` (supprimé) | — |
| `users/infrastructure/web/ShowRegistrationFormController.java` (supprimé) | — |
| `shared/web/ShowHomeController.java` (supprimé) | — |
| `src/main/resources/templates/` (supprimé) | — |

**Frontend**

| Fichier | Responsabilité |
|---|---|
| `src/api/client.js` (modifié) | `register()` + `ValidationError` |
| `src/api/client.spec.js` (créé) | Traduction des réponses d'erreur |
| `src/views/RegisterView.vue` (créé) | Écran d'inscription |
| `src/views/LoginView.vue` (modifié) | Bandeau de vérification, lien vers l'inscription |
| `src/router/index.js` (modifié) | Route `/register`, `meta.guestOnly` |
| `vite.config.js` (modifié) | Proxy retiré, `hmr.clientPort` |
| `Dockerfile`, `nginx.conf`, `.dockerignore` (créés) | Image de production autonome |

**Outillage** : `compose.yaml` (service `proxy`, labels, ports retirés), `.env.example` (`HTTP_PORT`), `build.gradle.kts` (starter Thymeleaf retiré), `README.md`, `CLAUDE.md`, `.claude/rules/frontend.md`.

---

### Task 1: L'inscription devient une route d'API

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/RegistrationRequest.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/ValidationErrorResponse.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/ErrorResponse.java`
- Modify: `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/RegisterUserController.java`
- Modify: `src/main/java/xyz/sterenn/secondbrain/config/SecurityConfig.java`
- Delete: `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/RegistrationForm.java`
- Delete: `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/ShowRegistrationFormController.java`
- Delete: `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/web/ShowRegistrationFormControllerTest.java`
- Delete: `src/main/resources/templates/register.html`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/web/RegisterUserControllerTest.java` (réécrit)
- Test: `src/test/java/xyz/sterenn/secondbrain/config/SecurityConfigTest.java` (un test ajouté)
- Test: `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/web/VerifyAccountControllerTest.java` (helper `inscrit` adapté)

**Interfaces:**
- Consumes: `CommandBus.dispatch(Command)`, `RegisterUser(String email, String rawPassword)`, les exceptions `InvalidEmailException`, `EmailAlreadyUsedException`, `WeakPasswordException`, `org.springframework.mail.MailException`.
- Produces: `POST /api/registrations`, corps `{"email": String, "password": String}` → `201` vide · `422 {"errors": {champ: message}}` · `503 {"message": String}`. Le front (tâche 4) et le test de vérification (tâche 2) en dépendent.

- [ ] **Step 1: Réécrire le test du contrôleur d'inscription**

Remplacer intégralement `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/web/RegisterUserControllerTest.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.application.query.FindUserByEmail;
import xyz.sterenn.secondbrain.users.application.query.UserView;
import xyz.sterenn.secondbrain.users.domain.port.NotificationSender;

/**
 * Couvre les scénarios d'écriture du parcours d'inscription au niveau HTTP, désormais en
 * JSON. CSRF est désactivé côté application : aucun jeton à fournir.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegisterUserControllerTest {

    private static final String MOT_DE_PASSE_VALIDE = "chevalpile42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QueryBus queryBus;

    private static String corps(String email, String motDePasse) {
        return """
            {"email": "%s", "password": "%s"}
            """.formatted(email, motDePasse);
    }

    @Test
    void cree_le_compte_et_repond_201_en_cas_de_succes() throws Exception {
        mockMvc.perform(post("/api/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps("alice@example.com", MOT_DE_PASSE_VALIDE)))
            .andExpect(status().isCreated());

        Optional<UserView> vue = queryBus.ask(new FindUserByEmail("alice@example.com"));
        assertThat(vue).isPresent();
        assertThat(vue.get().verified()).isFalse();
    }

    @Test
    void refuse_un_email_deja_utilise_avec_une_erreur_sur_le_champ_email() throws Exception {
        mockMvc.perform(post("/api/registrations")
            .contentType(MediaType.APPLICATION_JSON)
            .content(corps("bob@example.com", MOT_DE_PASSE_VALIDE)));

        mockMvc.perform(post("/api/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps("bob@example.com", MOT_DE_PASSE_VALIDE)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors.email").exists())
            .andExpect(jsonPath("$.errors.password").doesNotExist());
    }

    @Test
    void refuse_un_mot_de_passe_faible_avec_une_erreur_sur_le_champ_password() throws Exception {
        mockMvc.perform(post("/api/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps("carol@example.com", "court")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors.password").exists())
            .andExpect(jsonPath("$.errors.email").doesNotExist());

        assertThat(queryBus.ask(new FindUserByEmail("carol@example.com"))).isEmpty();
    }

    @Test
    void refuse_un_email_mal_forme_avec_une_erreur_sur_le_champ_email() throws Exception {
        mockMvc.perform(post("/api/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps("pas-un-email", MOT_DE_PASSE_VALIDE)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void refuse_les_champs_vides_en_nommant_les_deux() throws Exception {
        mockMvc.perform(post("/api/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps("", "")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors.email").exists())
            .andExpect(jsonPath("$.errors.password").exists());
    }

    /**
     * Isolée dans un contexte Spring distinct : le canal de notification y échoue
     * systématiquement, ce qui entrerait en conflit avec le
     * {@link RecordingNotificationSenderConfiguration} de la classe englobante si les deux
     * définissaient chacune un {@code NotificationSender} {@code @Primary} dans le même
     * contexte. {@code @NestedTestConfiguration(OVERRIDE)} fait qu'aucune configuration de
     * {@link RegisterUserControllerTest} n'est héritée ici.
     *
     * <p>Volontairement <strong>sans</strong> {@code @Transactional} : le rollback observé
     * ici est déclenché par {@code SpringCommandBus.dispatch}, une transaction imbriquée
     * dans celle du test. Tant que la transaction englobante du test n'a pas elle-même
     * terminé, ce rollback interne n'est que marqué, pas exécuté : une requête dans le même
     * test verrait encore la ligne. Le nettoyage est donc explicite (voir
     * {@code CommandBusTransactionTest}).
     */
    @Nested
    @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
    @Import({TestcontainersConfiguration.class, QuandLenvoiEchoue.EchecEnvoiConfiguration.class})
    @SpringBootTest
    @AutoConfigureMockMvc
    class QuandLenvoiEchoue {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private QueryBus queryBus;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        /**
         * Filet de sécurité : si le rollback ne fonctionnait pas, la ligne survivrait et
         * pourrait perturber d'autres tests de la suite.
         */
        @AfterEach
        void nettoyer() {
            jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", "erin@example.com");
        }

        @Test
        void repond_503_sans_erreur_de_champ_et_annule_la_creation_du_compte() throws Exception {
            mockMvc.perform(post("/api/registrations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corps("erin@example.com", MOT_DE_PASSE_VALIDE)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors").doesNotExist());

            // Le rollback de SpringCommandBus doit avoir annulé l'inscription entière.
            assertThat(queryBus.ask(new FindUserByEmail("erin@example.com"))).isEmpty();
        }

        @TestConfiguration(proxyBeanMethods = false)
        static class EchecEnvoiConfiguration {

            @Bean
            @Primary
            NotificationSender notificationSender() {
                return notification -> {
                    throw new MailSendException("échec d'envoi simulé pour le test");
                };
            }
        }
    }
}
```

- [ ] **Step 2: Adapter le helper du test de vérification**

`VerifyAccountControllerTest` crée ses comptes en passant par la route d'inscription : elle change de forme, le helper doit suivre. Ses assertions sur la vue restent inchangées — c'est la tâche 2 qui les reprendra.

Dans `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/web/VerifyAccountControllerTest.java`, remplacer la méthode `inscrit` :

```java
    private VerificationNotification inscrit(String email) throws Exception {
        mockMvc.perform(post("/api/registrations")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email": "%s", "password": "%s"}
                """.formatted(email, MOT_DE_PASSE_VALIDE)));
        return notifications.derniere();
    }
```

Ajouter l'import `org.springframework.http.MediaType`.

- [ ] **Step 3: Lancer les tests pour les voir échouer**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.infrastructure.web.RegisterUserControllerTest"
```

Attendu : ÉCHEC — `404` sur `/api/registrations`… ou plutôt `401`, la route étant inconnue sous `/api` où le refus est le défaut. Les deux confirment que la route n'existe pas encore.

- [ ] **Step 4: Créer le record de requête**

`src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/RegistrationRequest.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Corps JSON de la demande d'inscription.
 *
 * <p>Record et non JavaBean : l'ancien {@code RegistrationForm} n'avait d'accesseurs que
 * parce que {@code th:field} lit la valeur via {@code BeanWrapper}. Thymeleaf disparu,
 * la raison disparaît avec lui.
 *
 * <p>La validation portée ici se limite à « le champ est rempli ». Le format de l'email
 * est l'affaire du value object {@code Email}, la robustesse du mot de passe celle de
 * {@code PasswordPolicy} : dupliquer ces règles ici les ferait diverger.
 *
 * <p>{@link #toString()} est redéfini pour ne jamais exposer le mot de passe en clair,
 * comme sur la commande {@code RegisterUser}.
 *
 * @param email    email saisi, non normalisé
 * @param password mot de passe en clair
 */
public record RegistrationRequest(
    @NotBlank(message = "L'email est obligatoire") String email,
    @NotBlank(message = "Le mot de passe est obligatoire") String password
) {

    @Override
    public String toString() {
        return "RegistrationRequest[email=" + email + ", password=***]";
    }
}
```

- [ ] **Step 5: Créer les deux records de réponse**

`src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/ValidationErrorResponse.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.web;

import java.util.Map;

/**
 * Corps d'un refus de saisie : un message par champ fautif, affichable tel quel sous le
 * champ concerné.
 *
 * <p>C'est la forme d'erreur <em>de ce projet</em>. {@code /api/token} ne la suit pas et
 * répond {@code {error, error_description}} : cette forme-là lui est imposée par RFC 6749,
 * dont il imite le {@code password grant}. Toute route future suit celle-ci.
 *
 * @param errors nom du champ → message de refus
 */
public record ValidationErrorResponse(Map<String, String> errors) {
}
```

`src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/ErrorResponse.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.web;

/**
 * Corps d'un refus qui ne vise aucun champ : la saisie était bonne, c'est le traitement
 * qui n'a pas abouti.
 *
 * @param message message affichable tel quel
 */
public record ErrorResponse(String message) {
}
```

- [ ] **Step 6: Réécrire le contrôleur d'inscription**

Remplacer intégralement `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/RegisterUserController.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.web;

import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.application.command.RegisterUser;
import xyz.sterenn.secondbrain.users.domain.exception.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidEmailException;
import xyz.sterenn.secondbrain.users.domain.exception.WeakPasswordException;

/**
 * Adapter entrant : traduit une demande JSON en commande, puis les exceptions métier en
 * erreurs de champ. Aucune règle métier ne vit ici.
 *
 * <p>Le {@link BindingResult} est déclaré en paramètre à dessein : sa présence empêche
 * Spring de lever {@code MethodArgumentNotValidException}, donc la traduction des refus
 * reste dans ce contrôleur plutôt que de partir dans un {@code @RestControllerAdvice}
 * qui vaudrait pour tout le contexte.
 *
 * <p>Le {@code 201} n'a ni corps ni en-tête {@code Location} : le compte créé n'est
 * lisible par personne tant qu'il n'est pas vérifié et qu'aucun jeton n'a été délivré.
 */
@RestController
public class RegisterUserController {

    private final CommandBus commandBus;

    public RegisterUserController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @PostMapping("/api/registrations")
    public ResponseEntity<Object> register(
            @Valid @RequestBody RegistrationRequest registrationRequest,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasErrors()) {
            return unprocessable(champsFautifs(bindingResult));
        }

        try {
            commandBus.dispatch(
                new RegisterUser(registrationRequest.email(), registrationRequest.password()));
        } catch (InvalidEmailException | EmailAlreadyUsedException e) {
            return unprocessable(Map.of("email", e.getMessage()));
        } catch (WeakPasswordException e) {
            return unprocessable(Map.of("password", e.getMessage()));
        } catch (MailException e) {
            // Le rollback a déjà eu lieu côté SpringCommandBus : aucune faute de champ ici,
            // c'est le canal de notification qui a échoué, pas la saisie de l'utilisateur.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(
                "Votre compte n'a pas pu être créé : l'email de vérification n'a pas pu être "
                    + "envoyé. Réessayez dans quelques instants."));
        }

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private static ResponseEntity<Object> unprocessable(Map<String, String> errors) {
        return ResponseEntity.unprocessableEntity().body(new ValidationErrorResponse(errors));
    }

    /**
     * {@code LinkedHashMap} et non {@code Map.of} : l'ordre de déclaration des champs est
     * conservé, ce qui rend la réponse stable d'une exécution à l'autre.
     */
    private static Map<String, String> champsFautifs(BindingResult bindingResult) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError erreur : bindingResult.getFieldErrors()) {
            errors.putIfAbsent(erreur.getField(), erreur.getDefaultMessage());
        }
        return errors;
    }
}
```

- [ ] **Step 7: Déclarer la route publique dans `SecurityConfig`**

Dans `src/main/java/xyz/sterenn/secondbrain/config/SecurityConfig.java`, ajouter la règle **avant** `requestMatchers("/api/**").authenticated()` :

```java
                // Créer un compte doit rester accessible à un visiteur anonyme : sans
                // cette ligne, le refus par défaut sous /api rendrait l'inscription
                // impossible à qui n'a pas déjà de compte.
                .requestMatchers(HttpMethod.POST, "/api/registrations").permitAll()
```

Ajouter l'import `org.springframework.http.HttpMethod`. Mettre à jour la Javadoc de classe : la phrase « Seule `/api/token` y déroge » devient « Seules `/api/token` et `POST /api/registrations` y dérogent ».

- [ ] **Step 8: Supprimer le formulaire et son contrôleur**

```bash
git rm src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/RegistrationForm.java \
       src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/ShowRegistrationFormController.java \
       src/test/java/xyz/sterenn/secondbrain/users/infrastructure/web/ShowRegistrationFormControllerTest.java \
       src/main/resources/templates/register.html
```

- [ ] **Step 9: Ajouter le test de sécurité de la nouvelle route**

Dans `src/test/java/xyz/sterenn/secondbrain/config/SecurityConfigTest.java` :

```java
    @Test
    void laisse_la_creation_de_compte_ouverte() throws Exception {
        // Sans compte, impossible d'obtenir un jeton : l'inscription doit rester anonyme.
        // Un corps vide suffit — c'est le 401 qu'on cherche à écarter, pas le 422.
        mockMvc.perform(post("/api/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnprocessableEntity());
    }
```

- [ ] **Step 10: Lancer les tests jusqu'au vert**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.infrastructure.web.*" \
           --tests "xyz.sterenn.secondbrain.config.SecurityConfigTest"
```

Attendu : SUCCÈS. `VerifyAccountControllerTest` doit passer sans modification autre que son helper.

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat: expose l'inscription en JSON sur /api/registrations"
```

---

### Task 2: La vérification redirige vers le front

**Files:**
- Modify: `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/VerifyAccountController.java`
- Delete: `src/main/resources/templates/verification.html`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/web/VerifyAccountControllerTest.java`

**Interfaces:**
- Consumes: `CommandBus.dispatch(Command)`, `VerifyAccount(String compte, String jeton)`, les exceptions `InvalidVerificationLinkException`, `ExpiredVerificationLinkException`, `AlreadyUsedVerificationLinkException`. Route `POST /api/registrations` de la tâche 1 pour le montage des cas.
- Produces: `GET /verification?compte=&jeton=` → `302` avec `Location: /login?verification=<code>`, où `<code>` vaut `ok`, `lien-invalide`, `lien-expire` ou `lien-deja-utilise`. La tâche 5 (LoginView) en dépend et doit employer exactement ces quatre valeurs.

- [ ] **Step 1: Réécrire les attentes du test**

Dans `VerifyAccountControllerTest`, remplacer les cinq méthodes de test (le helper `inscrit` adapté en tâche 1 ne change pas) :

```java
    @Test
    void verifie_le_compte_et_redirige_vers_la_connexion_quand_je_suis_le_lien_recu()
            throws Exception {
        VerificationNotification notification = inscrit("alice@example.com");

        mockMvc.perform(get("/verification")
                .param("compte", notification.accountId().toString())
                .param("jeton", notification.rawToken().value()))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/login?verification=ok"));

        assertThat(userRepository.findById(notification.accountId()).orElseThrow().isVerified())
            .isTrue();
    }

    @Test
    void refuse_un_lien_falsifie() throws Exception {
        VerificationNotification notification = inscrit("bob@example.com");

        mockMvc.perform(get("/verification")
                .param("compte", notification.accountId().toString())
                .param("jeton", "un-autre-jeton"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/login?verification=lien-invalide"));

        assertThat(userRepository.findById(notification.accountId()).orElseThrow().isVerified())
            .isFalse();
    }

    @Test
    void refuse_un_lien_dont_le_compte_est_inconnu_avec_le_meme_code_qu_un_lien_falsifie()
            throws Exception {
        VerificationNotification notification = inscrit("carol@example.com");

        // Un code distinct ferait de cette route un oracle d'existence de compte.
        mockMvc.perform(get("/verification")
                .param("compte", UUID.randomUUID().toString())
                .param("jeton", notification.rawToken().value()))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/login?verification=lien-invalide"));
    }

    @Test
    void refuse_un_lien_deja_utilise_et_le_dit() throws Exception {
        VerificationNotification notification = inscrit("dave@example.com");
        mockMvc.perform(get("/verification")
            .param("compte", notification.accountId().toString())
            .param("jeton", notification.rawToken().value()));

        mockMvc.perform(get("/verification")
                .param("compte", notification.accountId().toString())
                .param("jeton", notification.rawToken().value()))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/login?verification=lien-deja-utilise"));
    }

    @Test
    void refuse_un_lien_sans_parametre() throws Exception {
        mockMvc.perform(get("/verification"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/login?verification=lien-invalide"));
    }
```

Remplacer les imports `model` et `view` de `MockMvcResultMatchers` par `redirectedUrl`.

- [ ] **Step 2: Lancer le test pour le voir échouer**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.infrastructure.web.VerifyAccountControllerTest"
```

Attendu : ÉCHEC — `Status expected:<302> but was:<200>`.

- [ ] **Step 3: Réécrire le contrôleur de vérification**

Remplacer intégralement `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/VerifyAccountController.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.web;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.application.command.VerifyAccount;
import xyz.sterenn.secondbrain.users.domain.exception.AlreadyUsedVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.exception.ExpiredVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidVerificationLinkException;

/**
 * Adapter entrant de la route de vérification. Elle est <strong>hors de {@code /api}</strong>
 * et le reste : le lien part par email, il doit fonctionner dans n'importe quel client mail,
 * sans JavaScript et sans que le front soit en ligne. C'est la seule action du back qui ne
 * soit pas derrière l'API.
 *
 * <p>Le résultat voyage en <em>code</em> et non en message : c'est le front qui porte la
 * rédaction. Faire voyager le message lui-même le collerait dans l'historique du navigateur
 * et dans les logs d'accès du proxy, comme le fait déjà le jeton, pour aucun gain.
 *
 * <p>L'en-tête {@code Location} est <strong>relatif</strong> : le navigateur le résout contre
 * l'origine de la requête. L'application n'a donc aucune URL de front à connaître, et
 * l'origine unique garantie par le reverse proxy suffit.
 *
 * <p>Les paramètres sont optionnels et vides par défaut : un lien tronqué doit donner le même
 * refus qu'un lien falsifié, pas une erreur 400.
 */
@RestController
public class VerifyAccountController {

    private static final String LOGIN_PATH = "/login?verification=";

    private final CommandBus commandBus;

    public VerifyAccountController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @GetMapping("/verification")
    public ResponseEntity<Void> verify(
            @RequestParam(name = "compte", defaultValue = "") String compte,
            @RequestParam(name = "jeton", defaultValue = "") String jeton
    ) {
        String code;
        try {
            commandBus.dispatch(new VerifyAccount(compte, jeton));
            code = "ok";
        } catch (InvalidVerificationLinkException e) {
            // Les trois causes — UUID illisible, compte inconnu, jeton faux — partagent ce
            // code comme elles partagent un seul message : les distinguer ferait de cette
            // route un oracle d'existence de compte.
            code = "lien-invalide";
        } catch (ExpiredVerificationLinkException e) {
            code = "lien-expire";
        } catch (AlreadyUsedVerificationLinkException e) {
            code = "lien-deja-utilise";
        }

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(LOGIN_PATH + code))
            .build();
    }
}
```

- [ ] **Step 4: Supprimer le template de vérification**

```bash
git rm src/main/resources/templates/verification.html
```

- [ ] **Step 5: Lancer le test jusqu'au vert**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.infrastructure.web.VerifyAccountControllerTest"
```

Attendu : SUCCÈS, les cinq méthodes.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: redirige la vérification de compte vers l'écran de connexion"
```

---

### Task 3: Thymeleaf quitte le dépôt

**Files:**
- Delete: `src/main/java/xyz/sterenn/secondbrain/shared/web/ShowHomeController.java`
- Delete: `src/test/java/xyz/sterenn/secondbrain/shared/web/ShowHomeControllerTest.java`
- Delete: `src/main/resources/templates/home.html`
- Modify: `build.gradle.kts`

**Interfaces:**
- Consumes: rien.
- Produces: plus aucune route servie par vue côté Java. `GET /` répond `404` — le reverse proxy (tâche 7) n'enverra jamais ce chemin à l'application.

- [ ] **Step 1: Supprimer la page d'accueil et son test**

```bash
git rm src/main/java/xyz/sterenn/secondbrain/shared/web/ShowHomeController.java \
       src/test/java/xyz/sterenn/secondbrain/shared/web/ShowHomeControllerTest.java \
       src/main/resources/templates/home.html
```

Le répertoire `src/main/resources/templates/` doit maintenant être vide et disparaître avec le dernier fichier.

- [ ] **Step 2: Retirer le starter Thymeleaf**

Dans `build.gradle.kts`, supprimer la ligne :

```kotlin
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
```

et remplacer le commentaire `// Web / REST` par :

```kotlin
    // Web / REST — aucune vue rendue côté serveur : le front Vue est un projet séparé,
    // et la seule route non-API (GET /verification) répond par une redirection.
```

- [ ] **Step 3: Lancer la suite complète**

```bash
gtest build
```

Attendu : SUCCÈS. `SecondBrainApplicationTests` charge le contexte sans résolveur de vues ; `SecurityConfigTest.n_exige_aucune_authentification_sur_les_routes_publiques` continue d'obtenir un `404` sur une URL inconnue.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: retire Thymeleaf, l'application Java n'expose plus que des routes d'API"
```

---

### Task 4: Le front sait créer un compte

**Files:**
- Modify: `frontend/src/api/client.js`
- Test: `frontend/src/api/client.spec.js` (créé)

**Interfaces:**
- Consumes: `POST /api/registrations` de la tâche 1.
- Produces: `register(email, password)` — résout sans valeur en cas de succès, lève `ValidationError` (portant `errors`, une carte champ → message) sur `422`, lève `Error` avec le message du serveur sur toute autre panne. `RegisterView` (tâche 5) en dépend.

- [ ] **Step 1: Écrire le test**

Créer `frontend/src/api/client.spec.js` :

```js
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { register, ValidationError } from '@/api/client'

// Réponse minimale : seuls le statut et le corps JSON comptent pour ce module.
function reponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  }
}

describe("création de compte", () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('poste la saisie en JSON sur la route des inscriptions', async () => {
    fetch.mockResolvedValue(reponse(201, null))

    await register('alice@example.com', 'chevalpile42')

    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('/api/registrations')
    expect(options.method).toBe('POST')
    expect(options.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(options.body)).toEqual({
      email: 'alice@example.com',
      password: 'chevalpile42',
    })
  })

  it('traduit un 422 en erreurs par champ', async () => {
    fetch.mockResolvedValue(
      reponse(422, { errors: { email: "L'email n'est pas valide." } }),
    )

    await expect(register('pas-un-email', 'chevalpile42')).rejects.toThrow(ValidationError)

    try {
      await register('pas-un-email', 'chevalpile42')
    } catch (error) {
      expect(error.errors).toEqual({ email: "L'email n'est pas valide." })
    }
  })

  it('traduit un 503 en message global', async () => {
    fetch.mockResolvedValue(reponse(503, { message: "L'email n'a pas pu être envoyé." }))

    await expect(register('alice@example.com', 'chevalpile42')).rejects.toThrow(
      "L'email n'a pas pu être envoyé.",
    )
  })

  it("ne remplace pas l'échec par une erreur de syntaxe quand le corps n'est pas du JSON", async () => {
    // Un proxy en panne rend du HTML : le parsing échoue, mais l'utilisateur doit lire
    // un message utile, pas « Unexpected token < ».
    fetch.mockResolvedValue({
      ok: false,
      status: 502,
      json: () => Promise.reject(new SyntaxError('Unexpected token <')),
    })

    await expect(register('alice@example.com', 'chevalpile42')).rejects.toThrow(
      "Votre compte n'a pas pu être créé.",
    )
  })
})
```

- [ ] **Step 2: Lancer le test pour le voir échouer**

```bash
gfront npx vitest run src/api/client.spec.js
```

Attendu : ÉCHEC — `register` et `ValidationError` ne sont pas exportés par `@/api/client`.

- [ ] **Step 3: Implémenter dans le client HTTP**

Ajouter dans `frontend/src/api/client.js`, après la classe `UnauthorizedError` :

```js
/** La saisie a été refusée champ par champ : `errors` associe un nom de champ à son message. */
export class ValidationError extends Error {
  constructor(errors) {
    super('La saisie a été refusée.')
    this.name = 'ValidationError'
    this.errors = errors
  }
}

/**
 * Crée un compte. Ne rend rien en cas de succès : le serveur répond 201 sans corps,
 * puisque rien du compte créé n'est lisible tant qu'il n'est pas vérifié.
 */
export async function register(email, password) {
  const response = await fetch('/api/registrations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })

  if (response.ok) {
    return
  }

  // Le corps n'est pas garanti d'être du JSON (proxy en panne, 502 HTML…) : un parsing
  // qui échoue ne doit pas remplacer le message métier par une erreur de syntaxe.
  const payload = await response.json().catch(() => null)

  if (response.status === 422) {
    throw new ValidationError(payload?.errors ?? {})
  }
  throw new Error(payload?.message ?? "Votre compte n'a pas pu être créé.")
}
```

- [ ] **Step 4: Lancer le test jusqu'au vert**

```bash
gfront npx vitest run src/api/client.spec.js
```

Attendu : SUCCÈS, quatre tests.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/client.js frontend/src/api/client.spec.js
git commit -m "feat: ajoute l'appel de création de compte au client HTTP du front"
```

---

### Task 5: Les écrans d'inscription et de connexion

**Files:**
- Create: `frontend/src/views/RegisterView.vue`
- Modify: `frontend/src/views/LoginView.vue`
- Modify: `frontend/src/router/index.js`
- Test: `frontend/src/router/index.spec.js`

**Interfaces:**
- Consumes: `register(email, password)` et `ValidationError` de la tâche 4 ; les quatre codes de la tâche 2 (`ok`, `lien-invalide`, `lien-expire`, `lien-deja-utilise`).
- Produces: routes nommées `register` (chemin `/register`) et `login` (chemin `/login`), toutes deux portant `meta: { guestOnly: true }`.

- [ ] **Step 1: Écrire le test du garde**

Ajouter dans `frontend/src/router/index.spec.js`, à l'intérieur du `describe` existant :

```js
  it("renvoie de l'inscription vers l'espace connecté quand on est déjà connecté", async () => {
    authenticate()
    const router = createTestRouter()

    await router.push('/register')

    expect(router.currentRoute.value.name).toBe('home')
  })

  it("laisse un visiteur anonyme atteindre l'inscription", async () => {
    const router = createTestRouter()

    await router.push('/register')

    expect(router.currentRoute.value.name).toBe('register')
  })
```

- [ ] **Step 2: Lancer le test pour le voir échouer**

```bash
gfront npx vitest run src/router/index.spec.js
```

Attendu : ÉCHEC — la route `/register` n'existe pas, `currentRoute.value.name` est `undefined`.

- [ ] **Step 3: Créer l'écran d'inscription**

Créer `frontend/src/views/RegisterView.vue` :

```vue
<script setup>
import { ref } from 'vue'
import { register, ValidationError } from '@/api/client'

const email = ref('')
const password = ref('')
const fieldErrors = ref({})
const errorMessage = ref('')
const registered = ref(false)

async function submit() {
  fieldErrors.value = {}
  errorMessage.value = ''
  try {
    await register(email.value, password.value)
    registered.value = true
  } catch (error) {
    // Les messages viennent du serveur et sont affichables tels quels.
    if (error instanceof ValidationError) {
      fieldErrors.value = error.errors
      return
    }
    errorMessage.value = error.message
  }
}
</script>

<template>
  <main>
    <h1>Créer mon compte</h1>

    <p v-if="registered" role="status">
      Votre compte est créé. Un lien de vérification vient de vous être envoyé par email.
    </p>

    <template v-else>
      <p v-if="errorMessage" role="alert">{{ errorMessage }}</p>

      <form @submit.prevent="submit">
        <p>
          <label for="email">Email</label><br>
          <input id="email" v-model="email" type="email" autocomplete="username">
          <span v-if="fieldErrors.email" role="alert">{{ fieldErrors.email }}</span>
        </p>
        <p>
          <label for="password">Mot de passe</label><br>
          <input id="password" v-model="password" type="password" autocomplete="new-password">
          <span v-if="fieldErrors.password" role="alert">{{ fieldErrors.password }}</span>
        </p>
        <p>
          <button type="submit">Créer mon compte</button>
        </p>
      </form>
    </template>

    <p>
      <RouterLink :to="{ name: 'login' }">J'ai déjà un compte</RouterLink>
    </p>
  </main>
</template>
```

- [ ] **Step 4: Déclarer la route et généraliser le garde**

Dans `frontend/src/router/index.js`, remplacer le tableau `routes` et le corps du garde :

```js
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import HomeView from '@/views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'

export const routes = [
  // La racine ne porte rien : elle mène à l'espace connecté, qui renverra vers le login
  // si le jeton n'est plus valable. Il n'existe plus de page publique d'accueil.
  { path: '/', redirect: { name: 'home' } },
  { path: '/login', name: 'login', component: LoginView, meta: { guestOnly: true } },
  { path: '/register', name: 'register', component: RegisterView, meta: { guestOnly: true } },
  { path: '/home', name: 'home', component: HomeView, meta: { requiresAuth: true } },
]

/**
 * Garde d'authentification, exportée pour être testable hors du routeur applicatif.
 * Elle ne fait que lire l'état local : le serveur reste seul juge, et son refus est
 * traité au premier appel authentifié.
 */
export function authenticationGuard(to) {
  const auth = useAuthStore()

  if (to.meta.requiresAuth && !auth.isAuthenticated()) {
    return { name: 'login' }
  }
  // `guestOnly` plutôt qu'une comparaison sur le nom de la route : une page réservée aux
  // visiteurs anonymes se déclare, elle ne s'énumère pas dans le garde.
  if (to.meta.guestOnly && auth.isAuthenticated()) {
    return { name: 'home' }
  }
  return true
}
```

Le reste du fichier (création du routeur, `beforeEach`, export par défaut) ne change pas.

- [ ] **Step 5: Afficher le résultat de la vérification sur l'écran de connexion**

Dans `frontend/src/views/LoginView.vue`, ajouter au `<script setup>` :

```js
import { computed } from 'vue'
import { useRoute } from 'vue-router'

// Le serveur redirige ici avec un code, pas un message : c'est une navigation, et faire
// voyager le texte en query string le collerait dans l'historique du navigateur et dans
// les logs du proxy. Les libellés vivent donc ici — au prix d'une duplication avec les
// messages du domaine, dont aucun test ne surveille la divergence.
const VERIFICATION_MESSAGES = {
  ok: 'Votre adresse est vérifiée. Vous pouvez vous connecter.',
  'lien-invalide': "Ce lien de vérification n'est pas valide.",
  'lien-expire': 'Ce lien de vérification a expiré.',
  'lien-deja-utilise': 'Ce lien de vérification a déjà été utilisé.',
}

const route = useRoute()
// `computed` légitime ici, à l'inverse d'`isAuthenticated()` : la valeur ne dépend que de
// l'URL, qui est réactive.
const verificationMessage = computed(() => VERIFICATION_MESSAGES[route.query.verification])
```

et dans le `<template>`, juste après le `<h1>` :

```html
    <p v-if="verificationMessage" role="status">{{ verificationMessage }}</p>
```

puis, avant la fermeture de `</main>` :

```html
    <p>
      <RouterLink :to="{ name: 'register' }">Créer mon compte</RouterLink>
    </p>
```

- [ ] **Step 6: Lancer les tests et le build**

```bash
gfront npm run test:unit
gfront npm run build
```

Attendu : SUCCÈS pour les deux. Le build est le seul contrôle qui compile les templates des composants — les vues n'ont pas de test de rendu (écart assumé n° 15).

- [ ] **Step 7: Commit**

```bash
git add frontend/src
git commit -m "feat: ajoute l'écran d'inscription et le retour de vérification sur la connexion"
```

---

### Task 6: L'image de production du front

**Files:**
- Create: `frontend/Dockerfile`
- Create: `frontend/nginx.conf`
- Create: `frontend/.dockerignore`

**Interfaces:**
- Consumes: `npm run build` (script existant de `frontend/package.json`), qui produit `frontend/dist`.
- Produces: une image servant la SPA sur le port `80`, avec repli `try_files` sur `index.html`. La tâche 7 n'en dépend pas (le développement continue de tourner sous Vite) ; c'est le déploiement qui l'utilise.

- [ ] **Step 1: Écrire la configuration nginx**

Créer `frontend/nginx.conf` :

```nginx
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    location / {
        # Sans ce repli, un rechargement de page sur /login répondrait 404 : aucun fichier
        # ne porte ce nom, c'est le routeur Vue qui décide de ce que vaut un chemin.
        try_files $uri $uri/ /index.html;
    }
}
```

- [ ] **Step 2: Écrire le Dockerfile**

Créer `frontend/Dockerfile` :

```dockerfile
# syntax=docker/dockerfile:1

###############################################################################
# Étape 1 — build : produit les fichiers statiques de la SPA.
###############################################################################
FROM node:24-alpine AS build
WORKDIR /app

# Descripteurs d'abord : les dépendances restent en cache tant que le lock ne change pas.
# npm ci et non npm install : le lock est la référence.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

###############################################################################
# Étape 2 — runtime : nginx ne sert que du statique, il ne proxifie rien.
# Le routage vers l'API est l'affaire du reverse proxy placé devant (Traefik en
# développement, Coolify en production).
###############################################################################
FROM nginx:alpine AS runtime

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
```

- [ ] **Step 3: Écrire le `.dockerignore`**

Créer `frontend/.dockerignore` :

```
node_modules
dist
```

- [ ] **Step 4: Construire l'image et vérifier le repli SPA**

```bash
docker build -t second-brain-frontend ./frontend
docker run --rm -d -p 8099:80 --name second-brain-frontend-test second-brain-frontend
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8099/
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8099/login
docker rm -f second-brain-frontend-test
```

Attendu : `200` sur les deux. Un `404` sur `/login` signale que le `try_files` n'est pas actif.

- [ ] **Step 5: Commit**

```bash
git add frontend/Dockerfile frontend/nginx.conf frontend/.dockerignore
git commit -m "conf: construit et sert le front en autonomie derrière nginx"
```

---

### Task 7: Une seule origine en développement

**Files:**
- Modify: `compose.yaml`
- Modify: `frontend/vite.config.js`
- Modify: `.env.example`

**Interfaces:**
- Consumes: les routes des tâches 1 à 3 (`/api/**`, `/verification`), le service front sous Vite.
- Produces: une origine unique `http://localhost:${HTTP_PORT:-8080}` d'où tout le parcours est joignable. Aucun autre port applicatif publié.

- [ ] **Step 1: Ajouter le reverse proxy et les labels**

Dans `compose.yaml`, ajouter le service `proxy` en tête des services :

```yaml
  proxy:
    image: traefik:v3
    command:
      # Le routage se lit sur les labels des conteneurs, rien d'autre.
      - --providers.docker=true
      # Sans ça, db et mailpit se retrouveraient routés sans l'avoir demandé.
      - --providers.docker.exposedByDefault=false
      - --entryPoints.web.address=:80
    ports:
      # Seul port publié de l'application : c'est l'unique origine que voit le navigateur,
      # en développement comme en production, où Coolify tient ce rôle.
      - "${HTTP_PORT:-8080}:80"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
```

Dans le service `app` : supprimer le bloc `ports`, remplacer `SECONDBRAIN_BASE_URL` et ajouter les labels :

```yaml
      SECONDBRAIN_BASE_URL: http://localhost:${HTTP_PORT:-8080}
```

```yaml
    labels:
      - "traefik.enable=true"
      # /verification est hors /api à dessein : c'est une navigation, pas un appel XHR.
      # Swagger et actuator sont routés ici parce que ce fichier décrit un environnement de
      # développement ; en production le proxy n'expose que /api et /verification.
      - "traefik.http.routers.backend.rule=PathPrefix(`/api`) || PathPrefix(`/verification`) || PathPrefix(`/swagger-ui`) || PathPrefix(`/v3/api-docs`) || PathPrefix(`/actuator`)"
      - "traefik.http.routers.backend.entrypoints=web"
      # Priorité explicite : ne pas dépendre du calcul par longueur de règle de Traefik.
      - "traefik.http.routers.backend.priority=100"
      - "traefik.http.services.backend.loadbalancer.server.port=8080"
```

Dans le service `frontend` : supprimer le bloc `ports`, remplacer `VITE_API_TARGET` par `VITE_PUBLIC_PORT` et ajouter les labels :

```yaml
    environment:
      # Le client de rechargement à chaud se connecte à l'origine publique, pas au 5173
      # du conteneur, qui n'est plus publié.
      VITE_PUBLIC_PORT: ${HTTP_PORT:-8080}
    labels:
      - "traefik.enable=true"
      # Attrape-tout : tout ce que le back n'a pas réclamé est un chemin de la SPA.
      - "traefik.http.routers.frontend.rule=PathPrefix(`/`)"
      - "traefik.http.routers.frontend.entrypoints=web"
      - "traefik.http.routers.frontend.priority=1"
      - "traefik.http.services.frontend.loadbalancer.server.port=5173"
```

`db` et `mailpit` ne changent pas.

- [ ] **Step 2: Retirer le proxy de Vite et fixer le port du rechargement à chaud**

Dans `frontend/vite.config.js`, supprimer la constante `apiTarget` et remplacer le bloc `server` :

```js
  server: {
    // 0.0.0.0 : sans ça, le serveur n'écoute que la boucle locale du conteneur et le
    // proxy ne l'atteindrait pas.
    host: '0.0.0.0',
    port: 5173,
    // Le navigateur atteint Vite à travers le reverse proxy : le WebSocket du
    // rechargement à chaud doit viser le port public, pas le 5173 du conteneur.
    hmr: { clientPort: Number(process.env.VITE_PUBLIC_PORT ?? 8080) },
  },
```

Il n'y a plus de bloc `proxy` : l'origine unique est tenue par le reverse proxy, en développement comme en production. Le garder en laisserait une configuration morte racontant une histoire fausse.

- [ ] **Step 3: Remplacer les deux ports par un seul dans `.env.example`**

Remplacer les lignes `APP_PORT=8080` et `FRONTEND_PORT=5173` par :

```
# Port unique publié sur l'hôte : le reverse proxy sert le front et l'API sous cette seule
# origine. L'application Java et le serveur Vite ne publient plus rien.
HTTP_PORT=8080
```

Dans le commentaire de bas de fichier, `SECONDBRAIN_BASE_URL=http://localhost:8080` reste juste : c'est désormais l'origine du proxy.

- [ ] **Step 4: Vérifier le parcours complet à la main**

```bash
docker compose down -v
docker compose up --build -d
docker compose logs -f app     # attendre « Started SecondBrainApplication »
```

Puis, dans un navigateur :

1. <http://localhost:8080/> renvoie vers `/login`.
2. « Créer mon compte » mène à `/register` ; un email invalide affiche le message **sous le champ email**.
3. Une saisie valide affiche la confirmation d'envoi.
4. <http://localhost:8025> (Mailpit) contient le mail ; le lien pointe `http://localhost:8080/verification?…`.
5. Ce lien atterrit sur `/login?verification=ok` avec le bandeau de confirmation.
6. La connexion mène à `/home` et affiche l'email.
7. Éditer un `.vue` : la page se recharge à chaud en moins d'une seconde.
8. <http://localhost:8080/swagger-ui.html> répond.

Ce passage humain est une condition, pas une étape facultative : aucun test automatisé ne couvre le rendu des vues.

- [ ] **Step 5: Commit**

```bash
git add compose.yaml frontend/vite.config.js .env.example
git commit -m "conf: place un reverse proxy devant l'API et le front pour n'exposer qu'une origine"
```

---

### Task 8: La documentation dit la vérité

**Files:**
- Modify: `CLAUDE.md`
- Modify: `.claude/rules/frontend.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: l'état du dépôt après les tâches 1 à 7.
- Produces: rien de code.

- [ ] **Step 1: Reprendre `CLAUDE.md`**

- Section « Commandes » : le tableau des points d'entrée devient une seule origine — app et front sur <http://localhost:8080/>, Swagger `/swagger-ui.html`, health `/actuator/health`, Mailpit <http://localhost:8025>. Supprimer la mention du port 5173.
- Section « Architecture », arborescence : supprimer `shared/web/`, supprimer la mention des templates Thymeleaf, ajouter `frontend/Dockerfile` et `frontend/nginx.conf`.
- Section « Le flux de la vérification d'email » : la route répond désormais `302` vers `/login?verification=<code>` avec un `Location` relatif ; les quatre codes ; la raison du code plutôt que du message.
- Nouvelle sous-section « Le flux de l'inscription » : `POST /api/registrations`, `201` vide, `422 {errors}`, `503 {message}`, et la raison des deux formes d'erreur coexistantes.
- Écart n° 12 : le réécrire — le front se construit et se sert en autonomie (`frontend/Dockerfile`), ce qui reste hors déploiement est la configuration Coolify elle-même.
- Écart n° 2 : ajouter que l'origine unique est désormais tenue par un reverse proxy, en développement comme en production.
- Écart n° 15 : étendre à `RegisterView.vue`.
- Ajouter les quatre écarts nouveaux listés dans la spec (duplication des libellés de vérification, disparition de toute page publique, repli SPA non exercé avant la production, deux configurations de routage à garder cohérentes à la main).

- [ ] **Step 2: Reprendre `.claude/rules/frontend.md`**

- Section « Deux fronts cohabitent » : la supprimer et la remplacer par « Un seul front » — l'application Vue porte tout le parcours, l'application Java n'expose que des routes d'API et la redirection de vérification.
- Section « Communication avec le back » : l'origine unique vient du reverse proxy (Traefik en développement, Coolify en production), plus du proxy Vite ; `VITE_API_TARGET` n'existe plus ; la règle « aucun CORS » est inchangée et désormais vraie partout.
- Section « Langue » : amender la règle des messages — *un code quand le transport est une redirection, le message du serveur partout ailleurs*, avec le renvoi au `VERIFICATION_MESSAGES` de `LoginView.vue`.
- Section « Build et outillage » : `npm run build` n'est plus seulement un contrôle de compilation, il produit ce que sert l'image nginx.

- [ ] **Step 3: Reprendre `README.md`**

- Tableau des points d'entrée (lignes 34-37) : une seule origine.
- Section des variables d'environnement : `APP_PORT` et `FRONTEND_PORT` remplacés par `HTTP_PORT`.
- Section « Build de production » : ajouter la construction de l'image du front (`docker build -t second-brain-frontend ./frontend`) et préciser que le reverse proxy de production doit router `/api` et `/verification` vers le back, tout le reste vers le front, et n'exposer ni Swagger ni actuator.
- Les exemples `curl` sur `/api/token` et `/api/profile` restent valables sur le port 8080.

- [ ] **Step 4: Vérifier qu'aucune référence morte ne subsiste**

```bash
grep -rn "5173\|thymeleaf\|Thymeleaf\|APP_PORT\|FRONTEND_PORT\|VITE_API_TARGET\|templates/" \
  --exclude-dir=node_modules --exclude-dir=.git --exclude-dir=build --exclude-dir=dist \
  README.md CLAUDE.md .claude compose.yaml .env.example frontend/vite.config.js build.gradle.kts
```

Attendu : aucune correspondance, hormis celles des documents de conception sous `docs/`, qui décrivent l'état passé et doivent rester tels quels.

- [ ] **Step 5: Lancer la suite complète une dernière fois**

```bash
gtest build
gfront npm run test:unit
gfront npm run build
```

Attendu : SUCCÈS pour les trois.

- [ ] **Step 6: Commit**

```bash
git add CLAUDE.md .claude/rules/frontend.md README.md
git commit -m "docs: aligne la documentation sur le front unique et l'origine unique"
```
