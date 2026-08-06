# Vérification d'un compte par email — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** un utilisateur qui crée un compte reçoit une notification contenant un lien ; en suivant ce lien, son compte passe à `verified = true`.

**Architecture :** la notification est une notion du domaine (`NotificationSender` est un port de `users/domain/port/`, l'email un adapter de `users/infrastructure/email/`). Un jeton aléatoire est envoyé en clair dans la notification et stocké uniquement sous forme de hash salé (BCrypt), porté par une entité `VerificationToken` avec expiration à 24 h et usage unique. L'émission se fait dans la transaction d'inscription ouverte par le `CommandBus`. En passant, la convention « une classe de contrôleur par route » est posée et appliquée aux routes existantes.

**Tech stack :** Java 25 · Spring Boot 4.0.7 (MVC, Data JPA, Security, Validation, Mail) · Thymeleaf · Flyway · PostgreSQL 17 · JUnit 5 + AssertJ + Testcontainers · Mailpit en développement.

**Spec :** `docs/superpowers/specs/2026-08-06-verification-email-design.md`

---

## Global Constraints

Ces contraintes s'appliquent à **toutes** les tâches. Elles viennent de `CLAUDE.md` et de `.claude/rules/backend.md`.

- **Aucun JDK ni Gradle sur l'hôte.** Définir la fonction suivante une fois par session avant toute commande Gradle :

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

- **Langue :** commentaires, Javadoc, messages d'exception, libellés d'interface et noms de méthodes de test en **français**. Noms de classes, de méthodes de production et de packages en **anglais**. Les paramètres d'URL visibles par l'utilisateur (`compte`, `jeton`) sont en français, comme les libellés.
- **Jamais de `@Transactional` sur un handler.** La transaction appartient au bus ; annoter un handler le proxifie en JDK proxy et casse la résolution de son type générique au démarrage.
- **Toute exception métier hérite de `RuntimeException`**, et son message est affichable tel quel à l'utilisateur.
- **Flyway est maître du schéma**, `ddl-auto: validate`. Ne jamais modifier une migration déjà appliquée. Les colonnes de longueur bornée portent un `length` explicite côté entité.
- **Tests d'intégration : `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`.** Ne jamais introduire `@DataJpaTest`. Tout ce qui peut être testé sans Spring l'est en test unitaire pur.
- **Tester le port, pas l'adapter** : injecter l'interface du domaine.
- **Ne pas changer les versions** de `gradle/libs.versions.toml`. Les starters Spring s'ajoutent sans version (BOM).
- **Commits** : préfixe conventionnel en minuscule (`feat:`, `refactor:`, `test:`, `docs:`, `conf:`) suivi d'une description en français. Un commit par tâche, tests verts.
- **Le domaine n'importe jamais `org.springframework.*` ni `…infrastructure.*`**, à la seule exception des annotations `jakarta.persistence` sur les entités.

---

## File Structure

| Fichier | Responsabilité | Tâche |
|---|---|---|
| `.claude/rules/backend.md` | + règle « une classe de contrôleur par route » | 1 |
| `shared/web/ShowHomeController.java` | GET `/` (renommage) | 1 |
| `users/infrastructure/web/ShowRegistrationFormController.java` | GET `/register` | 1 |
| `users/infrastructure/web/RegisterUserController.java` | POST `/register` | 1 |
| `users/domain/valueobject/RawVerificationToken.java` | tire et porte le jeton en clair | 2 |
| `users/domain/entity/VerificationToken.java` | agrégat jeton : expiration, consommation | 3 |
| `db/migration/V4__create_users_verification_tokens.sql` | table du jeton | 3 |
| `users/domain/exception/ExpiredVerificationLinkException.java` | refus « expiré » | 3 |
| `users/domain/exception/AlreadyUsedVerificationLinkException.java` | refus « déjà utilisé » | 3 |
| `users/domain/port/VerificationTokenRepository.java` | port de stockage du jeton | 4 |
| `users/infrastructure/persistence/JpaVerificationTokenRepositoryAdapter.java` | adapter JPA | 4 |
| `users/infrastructure/persistence/SpringDataVerificationTokenRepository.java` | détail Spring Data (package-private) | 4 |
| `users/domain/port/TokenHasher.java` | port de hachage du jeton | 5 |
| `users/infrastructure/security/BCryptTokenHasher.java` | adapter BCrypt | 5 |
| `users/domain/valueobject/Notification.java` | sealed interface, contrat du port | 6 |
| `users/domain/valueobject/VerificationNotification.java` | intention « vérifier ce compte » | 6 |
| `users/domain/port/NotificationSender.java` | port d'envoi | 6 |
| `users/infrastructure/email/EmailNotificationSender.java` | adapter email : URL, sujet, corps | 7 |
| `compose.yaml`, `.env.example`, `application.yml` | Mailpit et configuration | 7 |
| `config/ClockConfiguration.java` | `@Bean Clock` | 8 |
| `users/application/command/RegisterUserHandler.java` | + émission du jeton et envoi | 8 |
| `users/domain/exception/InvalidVerificationLinkException.java` | refus « lien invalide » | 9 |
| `users/domain/entity/User.java` | + `verify()` | 9 |
| `users/domain/port/UserRepository.java` | + `findById` | 9 |
| `users/application/command/VerifyAccount.java` | intention de vérification | 9 |
| `users/application/command/VerifyAccountHandler.java` | orchestration de la vérification | 9 |
| `users/infrastructure/web/VerifyAccountController.java` | GET `/verification` | 10 |
| `templates/verification.html` | confirmation ou motif du refus | 10 |
| `templates/register.html` | message de succès explicite | 10 |
| `CLAUDE.md` | arborescence, routes, Mailpit | 11 |

---

## Task 1 : Une classe de contrôleur par route

Tâche indépendante du reste : elle pose la convention et l'applique aux routes existantes, sans toucher au domaine. C'est un pur `refactor:`, le comportement HTTP est inchangé.

**Files:**
- Modify: `.claude/rules/backend.md` (section « Adapters »)
- Create: `src/main/java/xyz/sterenn/secondbrain/shared/web/ShowHomeController.java`
- Delete: `src/main/java/xyz/sterenn/secondbrain/shared/web/HomeController.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/ShowRegistrationFormController.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/RegisterUserController.java`
- Delete: `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/RegistrationController.java`
- Create: `src/test/java/xyz/sterenn/secondbrain/shared/web/ShowHomeControllerTest.java`
- Delete: `src/test/java/xyz/sterenn/secondbrain/shared/web/HomeControllerTest.java`
- Create: `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/web/ShowRegistrationFormControllerTest.java`
- Create: `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/web/RegisterUserControllerTest.java`
- Delete: `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/web/RegistrationControllerTest.java`

**Interfaces:**
- Consumes : rien.
- Produces : `ShowRegistrationFormController` (GET `/register`, vue `register`), `RegisterUserController` (POST `/register`, redirection `/register?success`). `RegistrationForm` est inchangé et reste partagé par les deux.

- [ ] **Step 1 : Ajouter la règle dans `.claude/rules/backend.md`**

Dans la section « Adapters », juste après la puce « Un contrôleur ne contient **aucune règle métier**… », insérer :

```markdown
- **Une classe de contrôleur ne porte qu'un seul mapping**, et elle est nommée par
  l'intention de la route, pas par son verbe HTTP : `ShowRegistrationFormController`
  et `RegisterUserController`, pas `RegistrationController`. Un contrôleur mono-route
  n'injecte que ce dont sa route a besoin — celui qui affiche le formulaire ne connaît
  pas le `CommandBus` — et son test n'a qu'un seul sujet. Le nom se lit comme celui
  d'une commande : un verbe et son objet.
```

- [ ] **Step 2 : Déplacer les tests de la page d'accueil**

Créer `src/test/java/xyz/sterenn/secondbrain/shared/web/ShowHomeControllerTest.java` :

```java
package xyz.sterenn.secondbrain.shared.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;

/**
 * La page d'accueil est statique : rien n'est écrit en base, donc pas de {@code @Transactional}.
 * Testcontainers reste nécessaire pour lever le contexte (Flyway et {@code ddl-auto: validate}).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ShowHomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void affiche_la_page_d_accueil_a_un_visiteur_anonyme() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("home"));
    }

    @Test
    void propose_un_lien_vers_la_creation_de_compte() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(content().string(containsString("href=\"/register\"")));
    }
}
```

Puis `git rm src/test/java/xyz/sterenn/secondbrain/shared/web/HomeControllerTest.java`.

- [ ] **Step 3 : Renommer le contrôleur d'accueil**

Créer `src/main/java/xyz/sterenn/secondbrain/shared/web/ShowHomeController.java` :

```java
package xyz.sterenn.secondbrain.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Page d'accueil publique. Elle n'appartient à aucun bounded context : elle se contente
 * d'orienter le visiteur vers les points d'entrée de l'application.
 */
@Controller
public class ShowHomeController {

    @GetMapping("/")
    public String showHome() {
        return "home";
    }
}
```

Puis `git rm src/main/java/xyz/sterenn/secondbrain/shared/web/HomeController.java`.

- [ ] **Step 4 : Lancer le test d'accueil**

```bash
gtest test --tests "xyz.sterenn.secondbrain.shared.web.ShowHomeControllerTest"
```
Attendu : PASS (2 tests).

- [ ] **Step 5 : Écrire le test du formulaire d'inscription**

Créer `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/web/ShowRegistrationFormControllerTest.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;

/**
 * Affichage seul : aucune écriture, donc pas de {@code @Transactional}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ShowRegistrationFormControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void affiche_le_formulaire_a_un_visiteur_anonyme() throws Exception {
        mockMvc.perform(get("/register"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeExists("registrationForm"));
    }
}
```

- [ ] **Step 6 : Écrire le test de la soumission**

Créer `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/web/RegisterUserControllerTest.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.users.application.query.FindUserByEmail;
import xyz.sterenn.secondbrain.users.application.query.UserView;

/**
 * Couvre les scénarios d'écriture du ticket de création de compte au niveau HTTP.
 * CSRF est désactivé côté application : aucun jeton à fournir.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegisterUserControllerTest {

    private static final String MOT_DE_PASSE_VALIDE = "chevalpile42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QueryBus queryBus;

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

Puis `git rm src/test/java/xyz/sterenn/secondbrain/users/infrastructure/web/RegistrationControllerTest.java`.

- [ ] **Step 7 : Vérifier que les tests échouent à la compilation**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.infrastructure.web.*"
```
Attendu : les tests passent encore (ils visent les routes, pas les classes) — c'est normal, `RegistrationController` sert toujours les deux routes. Le vrai contrôle est au step 9.

- [ ] **Step 8 : Découper le contrôleur**

Créer `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/ShowRegistrationFormController.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Affiche le formulaire de création de compte. Une seule route, donc aucune dépendance :
 * ce contrôleur ne connaît pas le {@code CommandBus}.
 */
@Controller
public class ShowRegistrationFormController {

    @GetMapping("/register")
    public String showForm(Model model) {
        model.addAttribute("registrationForm", new RegistrationForm());
        return "register";
    }
}
```

Créer `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/RegisterUserController.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.application.command.RegisterUser;
import xyz.sterenn.secondbrain.users.domain.exception.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidEmailException;
import xyz.sterenn.secondbrain.users.domain.exception.WeakPasswordException;

/**
 * Adapter entrant : traduit un formulaire HTML en commande, puis les exceptions métier
 * en erreurs de champ. Aucune règle métier ne vit ici.
 */
@Controller
public class RegisterUserController {

    private final CommandBus commandBus;

    public RegisterUserController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute RegistrationForm registrationForm,
            BindingResult bindingResult
    ) {
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

Puis `git rm src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/RegistrationController.java`.

- [ ] **Step 9 : Lancer toute la suite**

```bash
gtest test
```
Attendu : PASS. Si Spring signale un mapping ambigu sur `/register`, c'est que l'ancien `RegistrationController` n'a pas été supprimé.

- [ ] **Step 10 : Commit**

```bash
git add -A
git commit -m "refactor: une classe de contrôleur par route

Pose la convention dans les règles backend et l'applique aux trois routes
existantes. Comportement HTTP inchangé.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 2 : Le jeton en clair

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/users/domain/valueobject/RawVerificationToken.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/domain/valueobject/RawVerificationTokenTest.java`

**Interfaces:**
- Consumes : rien.
- Produces : `RawVerificationToken` (record, composant `String value()`), fabrique statique `RawVerificationToken generate()`, constante `int BYTE_LENGTH = 32`, `toString()` masqué.

- [ ] **Step 1 : Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/users/domain/valueobject/RawVerificationTokenTest.java` :

```java
package xyz.sterenn.secondbrain.users.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Value object sans dépendance : test unitaire pur, sans Spring.
 */
class RawVerificationTokenTest {

    @Test
    void genere_un_jeton_non_vide() {
        assertThat(RawVerificationToken.generate().value()).isNotBlank();
    }

    @Test
    void genere_un_jeton_utilisable_tel_quel_dans_une_url() {
        // base64url sans padding : lettres, chiffres, tiret et souligné uniquement.
        assertThat(RawVerificationToken.generate().value()).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void genere_un_jeton_assez_long_pour_ne_pas_etre_devine() {
        // 32 octets encodés en base64 sans padding donnent 43 caractères.
        assertThat(RawVerificationToken.generate().value()).hasSize(43);
    }

    @Test
    void genere_un_jeton_different_a_chaque_appel() {
        Set<String> jetons = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            jetons.add(RawVerificationToken.generate().value());
        }
        assertThat(jetons).hasSize(100);
    }

    @Test
    void refuse_un_jeton_vide() {
        assertThatThrownBy(() -> new RawVerificationToken("  "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ne_divulgue_pas_le_jeton_dans_sa_representation_textuelle() {
        RawVerificationToken jeton = RawVerificationToken.generate();

        assertThat(jeton.toString()).doesNotContain(jeton.value());
    }
}
```

- [ ] **Step 2 : Lancer le test pour vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.domain.valueobject.RawVerificationTokenTest"
```
Attendu : ÉCHEC de compilation, `RawVerificationToken` n'existe pas.

- [ ] **Step 3 : Écrire l'implémentation minimale**

Créer `src/main/java/xyz/sterenn/secondbrain/users/domain/valueobject/RawVerificationToken.java` :

```java
package xyz.sterenn.secondbrain.users.domain.valueobject;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Jeton de vérification en clair. Il n'existe qu'à deux endroits : dans la notification
 * envoyée à l'utilisateur, et le temps du calcul de son empreinte. Ce qui est persisté,
 * c'est uniquement son hash salé.
 *
 * <p>{@link #toString()} est redéfini pour qu'un log ou un message d'échec d'assertion ne
 * puisse jamais le rendre en clair — même règle que les commandes portant un secret.
 */
public record RawVerificationToken(String value) {

    /** 32 octets d'entropie : hors de portée d'une recherche exhaustive. */
    public static final int BYTE_LENGTH = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    public RawVerificationToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Le jeton de vérification est obligatoire");
        }
    }

    /**
     * Tire un jeton aléatoire, encodé en base64url sans remplissage : les 43 caractères
     * obtenus traversent une URL sans échappement.
     */
    public static RawVerificationToken generate() {
        byte[] octets = new byte[BYTE_LENGTH];
        RANDOM.nextBytes(octets);
        return new RawVerificationToken(Base64.getUrlEncoder().withoutPadding().encodeToString(octets));
    }

    @Override
    public String toString() {
        return "RawVerificationToken[value=***]";
    }
}
```

- [ ] **Step 4 : Lancer le test pour vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.domain.valueobject.RawVerificationTokenTest"
```
Attendu : PASS (6 tests).

- [ ] **Step 5 : Commit**

```bash
git add src/main/java/xyz/sterenn/secondbrain/users/domain/valueobject/RawVerificationToken.java \
        src/test/java/xyz/sterenn/secondbrain/users/domain/valueobject/RawVerificationTokenTest.java
git commit -m "feat: ajoute le value object du jeton de vérification en clair

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 3 : L'agrégat `VerificationToken` et sa table

L'entité et la migration partent **dans le même commit** : Hibernate tourne en `ddl-auto: validate`, une `@Entity` sans table fait échouer le démarrage de tout le contexte, donc de toute la suite de tests.

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/users/domain/entity/VerificationToken.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/users/domain/exception/ExpiredVerificationLinkException.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/users/domain/exception/AlreadyUsedVerificationLinkException.java`
- Create: `src/main/resources/db/migration/V4__create_users_verification_tokens.sql`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/domain/entity/VerificationTokenTest.java`

**Interfaces:**
- Consumes : rien.
- Produces :
  - `VerificationToken.issue(UUID userId, String tokenHash, Instant maintenant)` → `VerificationToken`
  - `boolean isExpired(Instant maintenant)`, `boolean isConsumed()`, `void consume(Instant maintenant)`
  - getters : `getId()`, `getUserId()`, `getTokenHash()`, `getExpiresAt()`, `getConsumedAt()`, `getCreatedAt()`
  - constante `Duration VALIDITY = Duration.ofHours(24)`
  - `ExpiredVerificationLinkException`, `AlreadyUsedVerificationLinkException` : constructeurs sans argument.

- [ ] **Step 1 : Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/users/domain/entity/VerificationTokenTest.java` :

```java
package xyz.sterenn.secondbrain.users.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.users.domain.exception.AlreadyUsedVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.exception.ExpiredVerificationLinkException;

/**
 * Agrégat sans dépendance : test unitaire pur. Le temps entre par paramètre, ce qui
 * permet de vérifier l'expiration sans attendre 24 heures.
 */
class VerificationTokenTest {

    private static final Instant EMISSION = Instant.parse("2026-08-06T10:00:00Z");
    private static final UUID COMPTE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private VerificationToken emis() {
        return VerificationToken.issue(COMPTE, "empreinte", EMISSION);
    }

    @Test
    void nait_valide_et_non_consomme() {
        VerificationToken jeton = emis();

        assertThat(jeton.getUserId()).isEqualTo(COMPTE);
        assertThat(jeton.getTokenHash()).isEqualTo("empreinte");
        assertThat(jeton.isConsumed()).isFalse();
        assertThat(jeton.isExpired(EMISSION)).isFalse();
    }

    @Test
    void expire_vingt_quatre_heures_apres_son_emission() {
        VerificationToken jeton = emis();

        assertThat(jeton.getExpiresAt()).isEqualTo(EMISSION.plus(VerificationToken.VALIDITY));
        assertThat(jeton.isExpired(EMISSION.plus(Duration.ofHours(23)))).isFalse();
        assertThat(jeton.isExpired(EMISSION.plus(Duration.ofHours(25)))).isTrue();
    }

    @Test
    void marque_l_instant_de_consommation() {
        VerificationToken jeton = emis();
        Instant clic = EMISSION.plus(Duration.ofMinutes(5));

        jeton.consume(clic);

        assertThat(jeton.isConsumed()).isTrue();
        assertThat(jeton.getConsumedAt()).isEqualTo(clic);
    }

    @Test
    void refuse_une_seconde_consommation() {
        VerificationToken jeton = emis();
        jeton.consume(EMISSION.plus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> jeton.consume(EMISSION.plus(Duration.ofMinutes(10))))
            .isInstanceOf(AlreadyUsedVerificationLinkException.class);
    }

    @Test
    void refuse_la_consommation_d_un_jeton_expire() {
        VerificationToken jeton = emis();

        assertThatThrownBy(() -> jeton.consume(EMISSION.plus(Duration.ofHours(25))))
            .isInstanceOf(ExpiredVerificationLinkException.class);
        assertThat(jeton.isConsumed()).isFalse();
    }

    @Test
    void signale_d_abord_le_double_usage_quand_le_jeton_est_aussi_expire() {
        // L'utilisateur qui reclique un vieux lien déjà utilisé a besoin de savoir
        // qu'il a déjà vérifié son compte, pas que le lien a vieilli.
        VerificationToken jeton = emis();
        jeton.consume(EMISSION.plus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> jeton.consume(EMISSION.plus(Duration.ofHours(25))))
            .isInstanceOf(AlreadyUsedVerificationLinkException.class);
    }
}
```

- [ ] **Step 2 : Lancer le test pour vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.domain.entity.VerificationTokenTest"
```
Attendu : ÉCHEC de compilation, `VerificationToken` n'existe pas.

- [ ] **Step 3 : Créer les deux exceptions**

Créer `src/main/java/xyz/sterenn/secondbrain/users/domain/exception/ExpiredVerificationLinkException.java` :

```java
package xyz.sterenn.secondbrain.users.domain.exception;

/**
 * Le lien de vérification a dépassé sa durée de validité.
 */
public class ExpiredVerificationLinkException extends RuntimeException {

    public ExpiredVerificationLinkException() {
        super("Ce lien de vérification a expiré.");
    }
}
```

Créer `src/main/java/xyz/sterenn/secondbrain/users/domain/exception/AlreadyUsedVerificationLinkException.java` :

```java
package xyz.sterenn.secondbrain.users.domain.exception;

/**
 * Le lien de vérification a déjà servi. Un jeton est à usage unique.
 */
public class AlreadyUsedVerificationLinkException extends RuntimeException {

    public AlreadyUsedVerificationLinkException() {
        super("Ce lien de vérification a déjà été utilisé.");
    }
}
```

- [ ] **Step 4 : Créer l'entité**

Créer `src/main/java/xyz/sterenn/secondbrain/users/domain/entity/VerificationToken.java` :

```java
package xyz.sterenn.secondbrain.users.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import xyz.sterenn.secondbrain.users.domain.exception.AlreadyUsedVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.exception.ExpiredVerificationLinkException;

/**
 * Jeton de vérification d'une adresse email. Il porte deux règles métier : il expire, et
 * il ne sert qu'une fois.
 *
 * <p>{@code tokenHash} ne contient jamais le jeton en clair, seulement son empreinte
 * salée. Le compte est référencé par son identifiant, pas par une association JPA : deux
 * agrégats distincts ne se tiennent pas par un {@code @ManyToOne}.
 *
 * <p>Le temps entre toujours par paramètre, jamais par un {@code Instant.now()} interne :
 * c'est ce qui rend l'expiration testable sans Spring et sans attendre.
 */
@Entity
@Table(name = "users_verification_tokens")
public class VerificationToken {

    /** Assez long pour un mail lu le lendemain, assez court pour qu'un lien oublié ne vaille rien. */
    public static final Duration VALIDITY = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VerificationToken() {
        // requis par JPA
    }

    private VerificationToken(UUID userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /**
     * Émet un jeton valide {@link #VALIDITY} à compter de {@code maintenant}.
     */
    public static VerificationToken issue(UUID userId, String tokenHash, Instant maintenant) {
        return new VerificationToken(userId, tokenHash, maintenant.plus(VALIDITY));
    }

    public boolean isExpired(Instant maintenant) {
        return maintenant.isAfter(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    /**
     * Consomme le jeton, définitivement.
     *
     * @throws AlreadyUsedVerificationLinkException si le jeton a déjà servi — vérifié en
     *         premier, car c'est l'information utile à qui reclique un lien déjà utilisé
     * @throws ExpiredVerificationLinkException si le jeton a dépassé sa validité
     */
    public void consume(Instant maintenant) {
        if (isConsumed()) {
            throw new AlreadyUsedVerificationLinkException();
        }
        if (isExpired(maintenant)) {
            throw new ExpiredVerificationLinkException();
        }
        this.consumedAt = maintenant;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
```

- [ ] **Step 5 : Créer la migration**

Créer `src/main/resources/db/migration/V4__create_users_verification_tokens.sql` :

```sql
-- Jetons de vérification d'adresse email. Seule l'empreinte salée du jeton est stockée :
-- le clair n'existe que dans la notification envoyée à l'utilisateur.
--
-- UNIQUE (user_id) documente l'invariant courant « un jeton par compte ». Le renvoi d'un
-- lien de vérification, hors périmètre ici, décidera de lever cette contrainte ou de
-- réécrire la ligne existante.

CREATE TABLE users_verification_tokens (
    id          UUID                     NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID                     NOT NULL,
    token_hash  VARCHAR(255)             NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_users_verification_tokens PRIMARY KEY (id),
    CONSTRAINT uq_users_verification_tokens_user UNIQUE (user_id),
    CONSTRAINT fk_users_verification_tokens_user FOREIGN KEY (user_id)
        REFERENCES users_users (id) ON DELETE CASCADE
);
```

- [ ] **Step 6 : Lancer le test unitaire**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.domain.entity.VerificationTokenTest"
```
Attendu : PASS (6 tests).

- [ ] **Step 7 : Vérifier que le mapping est validé par Hibernate**

```bash
gtest test --tests "xyz.sterenn.secondbrain.SecondBrainApplicationTests"
```
Attendu : PASS. C'est ce test qui prouve que la migration et les annotations concordent — un `Schema-validation: wrong column type` ici se corrige côté migration ou annotations, **jamais** en touchant `ddl-auto`.

- [ ] **Step 8 : Commit**

```bash
git add src/main/java/xyz/sterenn/secondbrain/users/domain/entity/VerificationToken.java \
        src/main/java/xyz/sterenn/secondbrain/users/domain/exception/ExpiredVerificationLinkException.java \
        src/main/java/xyz/sterenn/secondbrain/users/domain/exception/AlreadyUsedVerificationLinkException.java \
        src/main/resources/db/migration/V4__create_users_verification_tokens.sql \
        src/test/java/xyz/sterenn/secondbrain/users/domain/entity/VerificationTokenTest.java
git commit -m "feat: ajoute l'agrégat du jeton de vérification et sa table

Expiration à 24 h et usage unique portés par le domaine. Seule l'empreinte
salée du jeton est persistée.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 4 : Le port de stockage du jeton et son adapter

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/users/domain/port/VerificationTokenRepository.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/persistence/SpringDataVerificationTokenRepository.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/persistence/JpaVerificationTokenRepositoryAdapter.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/persistence/JpaVerificationTokenRepositoryAdapterTest.java`

**Interfaces:**
- Consumes : `VerificationToken` (Task 3), `User`/`UserRepository` (existants).
- Produces : `VerificationTokenRepository` avec `VerificationToken save(VerificationToken token)` et `Optional<VerificationToken> findByUserId(UUID userId)`.

- [ ] **Step 1 : Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/persistence/JpaVerificationTokenRepositoryAdapterTest.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.entity.VerificationToken;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.port.VerificationTokenRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * Le test injecte le <em>port</em>, pas l'adapter : c'est le contrat du domaine qui est
 * vérifié. {@code @Transactional} fait rouler chaque test en arrière.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaVerificationTokenRepositoryAdapterTest {

    private static final Instant EMISSION = Instant.parse("2026-08-06T10:00:00Z");

    @Autowired
    private VerificationTokenRepository tokens;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID compteExistant(String email) {
        return users.save(User.register(new Email(email), "empreinte")).getId();
    }

    @Test
    void persiste_un_jeton_pour_un_compte() {
        UUID compte = compteExistant("alice@example.com");

        VerificationToken saved = tokens.save(VerificationToken.issue(compte, "empreinte", EMISSION));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(compte);
        assertThat(saved.getExpiresAt()).isEqualTo(EMISSION.plus(VerificationToken.VALIDITY));
        assertThat(saved.getConsumedAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void retrouve_le_jeton_d_un_compte() {
        UUID compte = compteExistant("bob@example.com");
        tokens.save(VerificationToken.issue(compte, "empreinte", EMISSION));

        assertThat(tokens.findByUserId(compte))
            .isPresent()
            .hasValueSatisfying(jeton -> assertThat(jeton.getTokenHash()).isEqualTo("empreinte"));
    }

    @Test
    void ne_retrouve_rien_pour_un_compte_sans_jeton() {
        assertThat(tokens.findByUserId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void enregistre_la_consommation_du_jeton() {
        UUID compte = compteExistant("carol@example.com");
        VerificationToken jeton = tokens.save(VerificationToken.issue(compte, "empreinte", EMISSION));
        Instant clic = EMISSION.plusSeconds(60);

        jeton.consume(clic);
        tokens.save(jeton);

        assertThat(tokens.findByUserId(compte))
            .hasValueSatisfying(relu -> assertThat(relu.isConsumed()).isTrue());
    }

    @Test
    void ne_stocke_jamais_le_jeton_en_clair() {
        // Contrôle direct en SQL : la colonne ne doit contenir que l'empreinte reçue.
        UUID compte = compteExistant("dave@example.com");
        tokens.save(VerificationToken.issue(compte, "{bcrypt}$2a$10$empreinte", EMISSION));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT token_hash FROM users_verification_tokens WHERE user_id = ?",
            String.class, compte))
            .isEqualTo("{bcrypt}$2a$10$empreinte");
    }
}
```

- [ ] **Step 2 : Lancer le test pour vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.infrastructure.persistence.JpaVerificationTokenRepositoryAdapterTest"
```
Attendu : ÉCHEC de compilation, `VerificationTokenRepository` n'existe pas.

- [ ] **Step 3 : Créer le port**

Créer `src/main/java/xyz/sterenn/secondbrain/users/domain/port/VerificationTokenRepository.java` :

```java
package xyz.sterenn.secondbrain.users.domain.port;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.users.domain.entity.VerificationToken;

/**
 * Port sortant vers le stockage des jetons de vérification. Un compte n'a qu'un jeton à
 * la fois tant que le renvoi de lien n'existe pas.
 */
public interface VerificationTokenRepository {

    VerificationToken save(VerificationToken token);

    Optional<VerificationToken> findByUserId(UUID userId);
}
```

- [ ] **Step 4 : Créer le repository Spring Data et l'adapter**

Créer `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/persistence/SpringDataVerificationTokenRepository.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.sterenn.secondbrain.users.domain.entity.VerificationToken;

/**
 * Détail Spring Data, volontairement package-private : rien hors de ce package ne doit
 * en dépendre.
 */
interface SpringDataVerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByUserId(UUID userId);
}
```

Créer `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/persistence/JpaVerificationTokenRepositoryAdapter.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.users.domain.entity.VerificationToken;
import xyz.sterenn.secondbrain.users.domain.port.VerificationTokenRepository;

/**
 * Adapter du port {@link VerificationTokenRepository}.
 *
 * <p>Contrairement à {@link JpaUserRepositoryAdapter}, aucune erreur technique n'est ici
 * traduite en erreur métier : la seule contrainte susceptible d'être violée est
 * {@code uq_users_verification_tokens_user}, et rien dans le domaine actuel ne peut
 * émettre un second jeton pour un même compte.
 */
@Component
public class JpaVerificationTokenRepositoryAdapter implements VerificationTokenRepository {

    private final SpringDataVerificationTokenRepository jpa;

    JpaVerificationTokenRepositoryAdapter(SpringDataVerificationTokenRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public VerificationToken save(VerificationToken token) {
        // saveAndFlush : le jeton référence l'utilisateur par une clé étrangère, la
        // violation éventuelle doit survenir ici et non au commit.
        return jpa.saveAndFlush(token);
    }

    @Override
    public Optional<VerificationToken> findByUserId(UUID userId) {
        return jpa.findByUserId(userId);
    }
}
```

- [ ] **Step 5 : Lancer le test pour vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.infrastructure.persistence.JpaVerificationTokenRepositoryAdapterTest"
```
Attendu : PASS (5 tests).

- [ ] **Step 6 : Commit**

```bash
git add src/main/java/xyz/sterenn/secondbrain/users/domain/port/VerificationTokenRepository.java \
        src/main/java/xyz/sterenn/secondbrain/users/infrastructure/persistence/ \
        src/test/java/xyz/sterenn/secondbrain/users/infrastructure/persistence/JpaVerificationTokenRepositoryAdapterTest.java
git commit -m "feat: ajoute le port de stockage des jetons de vérification

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 5 : Le port de hachage du jeton

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/users/domain/port/TokenHasher.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/security/BCryptTokenHasher.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/security/BCryptTokenHasherTest.java`

**Interfaces:**
- Consumes : rien.
- Produces : `TokenHasher` avec `String hash(String rawToken)` et `boolean matches(String rawToken, String hash)`.

- [ ] **Step 1 : Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/security/BCryptTokenHasherTest.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.users.domain.port.TokenHasher;
import xyz.sterenn.secondbrain.users.domain.valueobject.RawVerificationToken;

/**
 * L'adapter n'a besoin d'aucun contexte Spring : il s'instancie directement.
 */
class BCryptTokenHasherTest {

    private final TokenHasher hasher = new BCryptTokenHasher();

    @Test
    void ne_rend_jamais_le_jeton_en_clair() {
        String jeton = RawVerificationToken.generate().value();

        assertThat(hasher.hash(jeton)).doesNotContain(jeton);
    }

    @Test
    void reconnait_le_jeton_d_origine() {
        String jeton = RawVerificationToken.generate().value();

        assertThat(hasher.matches(jeton, hasher.hash(jeton))).isTrue();
    }

    @Test
    void refuse_un_autre_jeton() {
        String empreinte = hasher.hash(RawVerificationToken.generate().value());

        assertThat(hasher.matches(RawVerificationToken.generate().value(), empreinte)).isFalse();
    }

    @Test
    void produit_une_empreinte_differente_a_chaque_appel_pour_un_meme_jeton() {
        // Le salt est tiré au hasard et embarqué dans l'empreinte : deux hachages du
        // même jeton diffèrent, et aucune table précalculée n'est exploitable.
        String jeton = RawVerificationToken.generate().value();

        assertThat(hasher.hash(jeton)).isNotEqualTo(hasher.hash(jeton));
    }
}
```

- [ ] **Step 2 : Lancer le test pour vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.infrastructure.security.BCryptTokenHasherTest"
```
Attendu : ÉCHEC de compilation, `TokenHasher` et `BCryptTokenHasher` n'existent pas.

- [ ] **Step 3 : Créer le port et l'adapter**

Créer `src/main/java/xyz/sterenn/secondbrain/users/domain/port/TokenHasher.java` :

```java
package xyz.sterenn.secondbrain.users.domain.port;

/**
 * Port sortant vers le hachage des jetons de vérification. Jumeau de
 * {@link PasswordHasher}, et distinct de lui : les deux secrets n'ont ni la même durée de
 * vie ni la même exposition, rien n'impose qu'ils partagent un jour le même algorithme.
 */
public interface TokenHasher {

    String hash(String rawToken);

    boolean matches(String rawToken, String hash);
}
```

Créer `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/security/BCryptTokenHasher.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.security;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.users.domain.port.TokenHasher;

/**
 * Adapter du port {@link TokenHasher}, adossé à l'encodeur délégant de Spring Security :
 * les empreintes sont préfixées de l'algorithme ({@code {bcrypt}...}), ce qui permettra
 * d'en changer sans invalider les jetons en vol.
 *
 * <p>Le salt est tiré par BCrypt à chaque hachage et embarqué dans l'empreinte : deux
 * hachages du même jeton diffèrent, et la comparaison passe forcément par
 * {@link #matches}. La troncature de BCrypt au 72e octet est sans effet ici — un jeton
 * fait 43 caractères.
 */
@Component
public class BCryptTokenHasher implements TokenHasher {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Override
    public String hash(String rawToken) {
        return encoder.encode(rawToken);
    }

    @Override
    public boolean matches(String rawToken, String hash) {
        return encoder.matches(rawToken, hash);
    }
}
```

- [ ] **Step 4 : Lancer le test pour vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.infrastructure.security.BCryptTokenHasherTest"
```
Attendu : PASS (4 tests).

- [ ] **Step 5 : Commit**

```bash
git add src/main/java/xyz/sterenn/secondbrain/users/domain/port/TokenHasher.java \
        src/main/java/xyz/sterenn/secondbrain/users/infrastructure/security/BCryptTokenHasher.java \
        src/test/java/xyz/sterenn/secondbrain/users/infrastructure/security/BCryptTokenHasherTest.java
git commit -m "feat: ajoute le port de hachage des jetons de vérification

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 6 : La notification, notion du domaine

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/users/domain/valueobject/Notification.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/users/domain/valueobject/VerificationNotification.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/users/domain/port/NotificationSender.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/domain/valueobject/VerificationNotificationTest.java`

Aucune implémentation du port n'est créée ici, et aucun bean ne l'injecte encore : le contexte démarre sans problème. L'adapter arrive en Task 7, le consommateur en Task 8.

**Interfaces:**
- Consumes : `Email` (existant), `RawVerificationToken` (Task 2).
- Produces :
  - `sealed interface Notification permits VerificationNotification`, méthode `Email recipient()`
  - `record VerificationNotification(Email recipient, UUID accountId, RawVerificationToken rawToken) implements Notification`
  - `interface NotificationSender { void send(Notification notification); }`

- [ ] **Step 1 : Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/users/domain/valueobject/VerificationNotificationTest.java` :

```java
package xyz.sterenn.secondbrain.users.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificationNotificationTest {

    private static final UUID COMPTE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void porte_le_destinataire_le_compte_et_le_jeton() {
        RawVerificationToken jeton = RawVerificationToken.generate();

        VerificationNotification notification =
            new VerificationNotification(new Email("alice@example.com"), COMPTE, jeton);

        assertThat(notification.recipient()).isEqualTo(new Email("alice@example.com"));
        assertThat(notification.accountId()).isEqualTo(COMPTE);
        assertThat(notification.rawToken()).isEqualTo(jeton);
    }

    @Test
    void ne_divulgue_pas_le_jeton_dans_sa_representation_textuelle() {
        RawVerificationToken jeton = RawVerificationToken.generate();

        VerificationNotification notification =
            new VerificationNotification(new Email("alice@example.com"), COMPTE, jeton);

        assertThat(notification.toString()).doesNotContain(jeton.value());
    }

    @Test
    void est_bien_une_notification() {
        Notification notification =
            new VerificationNotification(new Email("alice@example.com"), COMPTE,
                RawVerificationToken.generate());

        assertThat(notification.recipient()).isEqualTo(new Email("alice@example.com"));
    }
}
```

- [ ] **Step 2 : Lancer le test pour vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotificationTest"
```
Attendu : ÉCHEC de compilation, `VerificationNotification` n'existe pas.

- [ ] **Step 3 : Créer les trois fichiers**

Créer `src/main/java/xyz/sterenn/secondbrain/users/domain/valueobject/Notification.java` :

```java
package xyz.sterenn.secondbrain.users.domain.valueobject;

/**
 * Message que le domaine décide d'adresser à un utilisateur. Notifier est une intention
 * métier ; le canal — email aujourd'hui — est un détail d'infrastructure.
 *
 * <p>L'interface est scellée : un adapter peut faire un {@code switch} exhaustif sur le
 * type de notification, et le compilateur lui imposera de traiter tout nouveau type le
 * jour où il en naîtra un. C'est ce qui garde le port générique sans le rendre flou.
 */
public sealed interface Notification permits VerificationNotification {

    Email recipient();
}
```

Créer `src/main/java/xyz/sterenn/secondbrain/users/domain/valueobject/VerificationNotification.java` :

```java
package xyz.sterenn.secondbrain.users.domain.valueobject;

import java.util.UUID;

/**
 * Invitation à vérifier l'adresse email d'un compte fraîchement créé.
 *
 * <p>Elle porte la donnée métier — qui, quel compte, quel jeton — et rien de la forme :
 * l'URL absolue, le sujet et le corps sont construits par l'adapter, qui seul connaît son
 * canal.
 *
 * <p>{@link #toString()} est redéfini pour ne jamais exposer le jeton, même règle que
 * {@code RegisterUser} pour le mot de passe.
 *
 * @param recipient adresse à notifier
 * @param accountId compte concerné, repris tel quel dans le lien
 * @param rawToken  jeton en clair, dont seule l'empreinte est stockée
 */
public record VerificationNotification(Email recipient, UUID accountId, RawVerificationToken rawToken)
        implements Notification {

    @Override
    public String toString() {
        return "VerificationNotification[recipient=" + recipient
            + ", accountId=" + accountId + ", rawToken=***]";
    }
}
```

Créer `src/main/java/xyz/sterenn/secondbrain/users/domain/port/NotificationSender.java` :

```java
package xyz.sterenn.secondbrain.users.domain.port;

import xyz.sterenn.secondbrain.users.domain.valueobject.Notification;

/**
 * Port sortant vers le canal de notification. Le domaine ignore lequel est utilisé —
 * email aujourd'hui, autre chose demain.
 */
public interface NotificationSender {

    void send(Notification notification);
}
```

- [ ] **Step 4 : Lancer le test pour vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotificationTest"
```
Attendu : PASS (3 tests).

- [ ] **Step 5 : Commit**

```bash
git add src/main/java/xyz/sterenn/secondbrain/users/domain/valueobject/Notification.java \
        src/main/java/xyz/sterenn/secondbrain/users/domain/valueobject/VerificationNotification.java \
        src/main/java/xyz/sterenn/secondbrain/users/domain/port/NotificationSender.java \
        src/test/java/xyz/sterenn/secondbrain/users/domain/valueobject/VerificationNotificationTest.java
git commit -m "feat: pose la notification comme notion du domaine

Interface scellée Notification et port NotificationSender : le domaine décide
de notifier, le canal reste un détail d'infrastructure.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 7 : L'adapter email et Mailpit

**Files:**
- Modify: `build.gradle.kts` (ajout de `spring-boot-starter-mail`)
- Create: `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/email/EmailNotificationSender.java`
- Modify: `src/main/resources/application.yml`
- Modify: `compose.yaml`
- Modify: `.env.example`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/email/EmailNotificationSenderTest.java`

**Interfaces:**
- Consumes : `Notification`, `VerificationNotification` (Task 6).
- Produces : `EmailNotificationSender` (bean `@Component`), méthode package-private `SimpleMailMessage buildMessage(Notification notification)` exposée pour le test.

- [ ] **Step 1 : Ajouter le starter mail**

Dans `build.gradle.kts`, après la ligne `implementation("org.springframework.boot:spring-boot-starter-thymeleaf")`, ajouter un bloc :

```kotlin
    // Notifications
    implementation("org.springframework.boot:spring-boot-starter-mail")
```

Pas de version : elle vient du BOM Spring Boot.

- [ ] **Step 2 : Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/email/EmailNotificationSenderTest.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;
import xyz.sterenn.secondbrain.users.domain.valueobject.RawVerificationToken;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

/**
 * Ce qui est testé ici, c'est ce que l'adapter écrit : l'URL, l'adresse, le sujet. Le
 * transport lui-même est celui de Spring, il n'a pas à être vérifié — d'où le
 * {@code JavaMailSender} nul, jamais sollicité par {@code buildMessage}.
 */
class EmailNotificationSenderTest {

    private static final UUID COMPTE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final EmailNotificationSender sender =
        new EmailNotificationSender(null, "http://localhost:8080", "no-reply@second-brain.localhost");

    private SimpleMailMessage message(RawVerificationToken jeton) {
        return sender.buildMessage(
            new VerificationNotification(new Email("alice@example.com"), COMPTE, jeton));
    }

    @Test
    void adresse_le_message_au_destinataire_de_la_notification() {
        SimpleMailMessage message = message(RawVerificationToken.generate());

        assertThat(message.getTo()).containsExactly("alice@example.com");
        assertThat(message.getFrom()).isEqualTo("no-reply@second-brain.localhost");
    }

    @Test
    void annonce_la_verification_dans_le_sujet() {
        assertThat(message(RawVerificationToken.generate()).getSubject())
            .isEqualTo("Vérifiez votre adresse email");
    }

    @Test
    void construit_le_lien_absolu_de_verification() {
        RawVerificationToken jeton = RawVerificationToken.generate();

        assertThat(message(jeton).getText())
            .contains("http://localhost:8080/verification"
                + "?compte=11111111-1111-1111-1111-111111111111"
                + "&jeton=" + jeton.value());
    }

    @Test
    void annonce_la_duree_de_validite_du_lien() {
        assertThat(message(RawVerificationToken.generate()).getText()).contains("24 heures");
    }
}
```

- [ ] **Step 3 : Lancer le test pour vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.infrastructure.email.EmailNotificationSenderTest"
```
Attendu : ÉCHEC de compilation, `EmailNotificationSender` n'existe pas.

- [ ] **Step 4 : Écrire l'adapter**

Créer `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/email/EmailNotificationSender.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import xyz.sterenn.secondbrain.users.domain.port.NotificationSender;
import xyz.sterenn.secondbrain.users.domain.valueobject.Notification;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

/**
 * Adapter email du port {@link NotificationSender}. Il est le seul à connaître l'URL
 * publique de l'application, le sujet et la rédaction du message : le domaine ne dit que
 * <em>quoi</em> notifier et à qui.
 *
 * <p>Le {@code switch} sur {@link Notification} est exhaustif parce que l'interface est
 * scellée : ajouter un type de notification sans le traiter ici ne compilera pas.
 */
@Component
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final String baseUrl;
    private final String from;

    EmailNotificationSender(
            JavaMailSender mailSender,
            @Value("${secondbrain.base-url}") String baseUrl,
            @Value("${secondbrain.notification.from}") String from
    ) {
        this.mailSender = mailSender;
        this.baseUrl = baseUrl;
        this.from = from;
    }

    @Override
    public void send(Notification notification) {
        mailSender.send(buildMessage(notification));
    }

    // Package-private : c'est le contenu rédigé ici qui mérite un test, pas le transport.
    SimpleMailMessage buildMessage(Notification notification) {
        return switch (notification) {
            case VerificationNotification verification -> verificationMessage(verification);
        };
    }

    private SimpleMailMessage verificationMessage(VerificationNotification notification) {
        String lien = UriComponentsBuilder.fromUriString(baseUrl)
            .path("/verification")
            .queryParam("compte", notification.accountId())
            .queryParam("jeton", notification.rawToken().value())
            .build()
            .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(notification.recipient().value());
        message.setSubject("Vérifiez votre adresse email");
        message.setText("""
            Bonjour,

            Votre compte Second Brain a bien été créé. Il reste à vérifier votre adresse
            email en suivant ce lien :

            %s

            Ce lien est valable 24 heures et ne fonctionne qu'une fois.

            Si vous n'êtes pas à l'origine de cette création de compte, ignorez ce message.
            """.formatted(lien));
        return message;
    }
}
```

- [ ] **Step 5 : Lancer le test pour vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.infrastructure.email.EmailNotificationSenderTest"
```
Attendu : PASS (4 tests).

- [ ] **Step 6 : Configurer l'application**

Dans `src/main/resources/application.yml`, ajouter sous `spring:` (après le bloc `flyway:`) :

```yaml
  mail:
    host: ${SPRING_MAIL_HOST:localhost}
    port: ${SPRING_MAIL_PORT:1025}
    properties:
      mail:
        smtp:
          auth: false
          starttls:
            enable: false
```

Et, à la racine du fichier (après le bloc `springdoc:`) :

```yaml
# Propriétés applicatives. `base-url` est l'URL publique telle que l'utilisateur la voit
# dans son navigateur : c'est elle qui est écrite dans les liens des notifications.
secondbrain:
  base-url: ${SECONDBRAIN_BASE_URL:http://localhost:8080}
  notification:
    from: ${SECONDBRAIN_NOTIFICATION_FROM:no-reply@second-brain.localhost}
```

- [ ] **Step 7 : Ajouter Mailpit à Compose**

Dans `compose.yaml`, ajouter un service après `adminer` :

```yaml
  mailpit:
    image: axllent/mailpit:latest
    ports:
      # SMTP pour l'application, interface web pour lire les mails capturés.
      - "${MAILPIT_SMTP_PORT:-1025}:1025"
      - "${MAILPIT_WEB_PORT:-8025}:8025"
```

Et, dans le bloc `environment:` du service `app`, ajouter :

```yaml
      SPRING_MAIL_HOST: mailpit
      SPRING_MAIL_PORT: 1025
      SECONDBRAIN_BASE_URL: http://localhost:${APP_PORT:-8080}
```

Ajouter aussi `mailpit` aux `depends_on` du service `app` :

```yaml
    depends_on:
      db:
        condition: service_healthy
      mailpit:
        condition: service_started
```

Dans `.env.example`, ajouter sous « Ports exposés sur l'hôte » :

```bash
MAILPIT_SMTP_PORT=1025
MAILPIT_WEB_PORT=8025
```

- [ ] **Step 8 : Vérifier que le contexte démarre toujours**

```bash
gtest test --tests "xyz.sterenn.secondbrain.SecondBrainApplicationTests"
```
Attendu : PASS. Le bean `JavaMailSender` est créé mais aucune connexion SMTP n'est tentée tant que rien n'envoie de message.

- [ ] **Step 9 : Commit**

```bash
git add build.gradle.kts compose.yaml .env.example \
        src/main/resources/application.yml \
        src/main/java/xyz/sterenn/secondbrain/users/infrastructure/email/ \
        src/test/java/xyz/sterenn/secondbrain/users/infrastructure/email/
git commit -m "feat: ajoute l'adapter email du port de notification

L'adapter construit l'URL absolue, le sujet et le corps ; Mailpit capte les
mails en développement (interface sur 8025).

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 8 : L'inscription émet le jeton et notifie

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/config/ClockConfiguration.java`
- Modify: `src/main/java/xyz/sterenn/secondbrain/users/application/command/RegisterUserHandler.java`
- Create: `src/test/java/xyz/sterenn/secondbrain/users/RecordingNotificationSenderConfiguration.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/application/command/RegisterUserHandlerTest.java` (modifier — ajouter les cas)

**Interfaces:**
- Consumes : `RawVerificationToken` (2), `VerificationToken` (3), `VerificationTokenRepository` (4), `TokenHasher` (5), `VerificationNotification`/`NotificationSender` (6).
- Produces :
  - bean `Clock` (`Clock.systemUTC()`)
  - `RecordingNotificationSenderConfiguration.RecordingNotificationSender` avec `List<VerificationNotification> verifications()`, `VerificationNotification derniere()`, `void clear()`

- [ ] **Step 1 : Créer le bean `Clock`**

Créer `src/main/java/xyz/sterenn/secondbrain/config/ClockConfiguration.java` :

```java
package xyz.sterenn.secondbrain.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Le temps est une dépendance comme une autre. Les handlers reçoivent une {@link Clock}
 * et passent l'instant au domaine, qui n'appelle jamais {@code Instant.now()} lui-même :
 * c'est ce qui rend l'expiration d'un jeton testable sans attendre.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

- [ ] **Step 2 : Créer l'enregistreur de notifications pour les tests**

Créer `src/test/java/xyz/sterenn/secondbrain/users/RecordingNotificationSenderConfiguration.java` :

```java
package xyz.sterenn.secondbrain.users;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import xyz.sterenn.secondbrain.users.domain.port.NotificationSender;
import xyz.sterenn.secondbrain.users.domain.valueobject.Notification;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

/**
 * Remplace le canal email par un enregistreur en mémoire. Les tests vérifient ainsi le
 * <em>port</em> et non l'adapter, et peuvent relire le jeton en clair pour enchaîner sur
 * la route de vérification — exactement ce que ferait l'utilisateur depuis sa boîte mail.
 *
 * <p>Le bean est partagé par tout le contexte : appeler {@link RecordingNotificationSender#clear()}
 * en {@code @BeforeEach}, le rollback de la transaction de test ne le vide pas.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecordingNotificationSenderConfiguration {

    @Bean
    @Primary
    public RecordingNotificationSender recordingNotificationSender() {
        return new RecordingNotificationSender();
    }

    public static class RecordingNotificationSender implements NotificationSender {

        private final List<Notification> envoyees = new ArrayList<>();

        @Override
        public void send(Notification notification) {
            envoyees.add(notification);
        }

        public List<VerificationNotification> verifications() {
            return envoyees.stream()
                .filter(VerificationNotification.class::isInstance)
                .map(VerificationNotification.class::cast)
                .toList();
        }

        public VerificationNotification derniere() {
            List<VerificationNotification> verifications = verifications();
            if (verifications.isEmpty()) {
                throw new IllegalStateException("Aucune notification de vérification enregistrée");
            }
            return verifications.getLast();
        }

        public void clear() {
            envoyees.clear();
        }
    }
}
```

- [ ] **Step 3 : Écrire les tests qui échouent**

Remplacer `src/test/java/xyz/sterenn/secondbrain/users/application/command/RegisterUserHandlerTest.java` par le contenu ci-dessous. **Les six tests existants sont conservés à l'identique** ; quatre s'ajoutent à la fin.

```java
package xyz.sterenn.secondbrain.users.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.entity.VerificationToken;
import xyz.sterenn.secondbrain.users.domain.exception.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidEmailException;
import xyz.sterenn.secondbrain.users.domain.exception.WeakPasswordException;
import xyz.sterenn.secondbrain.users.domain.port.PasswordHasher;
import xyz.sterenn.secondbrain.users.domain.port.TokenHasher;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.port.VerificationTokenRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

/**
 * La commande est toujours dispatchée par le bus, jamais appelée en direct : c'est le
 * chemin réel de production. Le canal de notification est remplacé par un enregistreur en
 * mémoire — c'est le port qui est vérifié, pas l'adapter email.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
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

    @Autowired
    private VerificationTokenRepository verificationTokens;

    @Autowired
    private TokenHasher tokenHasher;

    @Autowired
    private RecordingNotificationSender notifications;

    @BeforeEach
    void vide_les_notifications() {
        // L'enregistreur est un bean partagé par le contexte : le rollback de la
        // transaction de test ne le vide pas.
        notifications.clear();
    }

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

    @Test
    void notifie_le_nouveau_compte_de_sa_verification() {
        commandBus.dispatch(new RegisterUser("frank@example.com", MOT_DE_PASSE_VALIDE));

        VerificationNotification notification = notifications.derniere();
        assertThat(notification.recipient()).isEqualTo(new Email("frank@example.com"));
        assertThat(notification.rawToken().value()).isNotBlank();
    }

    @Test
    void emet_un_jeton_dont_seule_l_empreinte_est_stockee() {
        commandBus.dispatch(new RegisterUser("grace@example.com", MOT_DE_PASSE_VALIDE));

        VerificationNotification notification = notifications.derniere();
        VerificationToken jeton = verificationTokens.findByUserId(notification.accountId()).orElseThrow();

        assertThat(jeton.getTokenHash()).doesNotContain(notification.rawToken().value());
        assertThat(tokenHasher.matches(notification.rawToken().value(), jeton.getTokenHash())).isTrue();
        assertThat(jeton.isConsumed()).isFalse();
    }

    @Test
    void adresse_la_notification_au_compte_reellement_cree() {
        commandBus.dispatch(new RegisterUser("heidi@example.com", MOT_DE_PASSE_VALIDE));

        VerificationNotification notification = notifications.derniere();
        assertThat(users.findByEmail(new Email("heidi@example.com")).orElseThrow().getId())
            .isEqualTo(notification.accountId());
    }

    @Test
    void ne_notifie_pas_quand_l_inscription_est_refusee() {
        assertThatThrownBy(() -> commandBus.dispatch(new RegisterUser("ivan@example.com", "court")))
            .isInstanceOf(WeakPasswordException.class);

        assertThat(notifications.verifications()).isEmpty();
    }
}
```

- [ ] **Step 4 : Lancer les tests pour vérifier qu'ils échouent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.application.command.RegisterUserHandlerTest"
```
Attendu : ÉCHEC — aucune notification n'est enregistrée (`IllegalStateException: Aucune notification de vérification enregistrée`).

- [ ] **Step 5 : Faire émettre le jeton par le handler**

Remplacer `src/main/java/xyz/sterenn/secondbrain/users/application/command/RegisterUserHandler.java` par :

```java
package xyz.sterenn.secondbrain.users.application.command;

import java.time.Clock;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.users.domain.PasswordPolicy;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.entity.VerificationToken;
import xyz.sterenn.secondbrain.users.domain.exception.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.exception.WeakPasswordException;
import xyz.sterenn.secondbrain.users.domain.port.NotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.PasswordHasher;
import xyz.sterenn.secondbrain.users.domain.port.TokenHasher;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.port.VerificationTokenRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;
import xyz.sterenn.secondbrain.users.domain.valueobject.RawVerificationToken;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

/**
 * Orchestre l'inscription : conversion en value objects, contrôles métier, écriture, puis
 * émission du jeton de vérification et notification.
 *
 * <p>Aucun {@code @Transactional} ici — la transaction est ouverte par
 * {@code SpringCommandBus.dispatch} et couvre tout ce qui suit, envoi compris. Une panne
 * du canal de notification annule donc l'inscription : tant qu'il n'existe pas de renvoi
 * de lien, un compte créé sans notification serait définitivement invérifiable.
 */
@Component
public class RegisterUserHandler implements CommandHandler<RegisterUser> {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final VerificationTokenRepository verificationTokens;
    private final TokenHasher tokenHasher;
    private final NotificationSender notificationSender;
    private final Clock clock;

    public RegisterUserHandler(
            UserRepository users,
            PasswordHasher passwordHasher,
            VerificationTokenRepository verificationTokens,
            TokenHasher tokenHasher,
            NotificationSender notificationSender,
            Clock clock
    ) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.verificationTokens = verificationTokens;
        this.tokenHasher = tokenHasher;
        this.notificationSender = notificationSender;
        this.clock = clock;
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

        User user = users.save(User.register(email, passwordHasher.hash(command.rawPassword())));

        // Le clair ne quitte jamais cette méthode autrement que dans la notification :
        // ce qui est persisté, c'est uniquement son empreinte salée.
        RawVerificationToken rawToken = RawVerificationToken.generate();
        verificationTokens.save(
            VerificationToken.issue(user.getId(), tokenHasher.hash(rawToken.value()), clock.instant()));

        notificationSender.send(new VerificationNotification(email, user.getId(), rawToken));
    }
}
```

- [ ] **Step 6 : Lancer les tests pour vérifier qu'ils passent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.application.command.RegisterUserHandlerTest"
```
Attendu : PASS, tests existants compris.

- [ ] **Step 7 : Brancher l'enregistreur sur le test du contrôleur d'inscription**

`RegisterUserControllerTest` (Task 1) dispatche `RegisterUser` : sans l'enregistreur, c'est le vrai adapter email qui part et échoue faute de serveur SMTP. Dans ce fichier, remplacer :

```java
@Import(TestcontainersConfiguration.class)
```

par :

```java
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
```

et ajouter l'import `xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration`.

`CommandBusTransactionTest` n'a rien à changer : il dispatche sa propre commande de test, jamais `RegisterUser`.

- [ ] **Step 8 : Lancer toute la suite**

```bash
gtest test
```
Attendu : PASS. Un échec sur `MailSendException` signale un test d'intégration qui déclenche une inscription sans importer l'enregistreur — lui ajouter le même `@Import`.

- [ ] **Step 9 : Commit**

```bash
git add -A
git commit -m "feat: l'inscription émet un jeton et notifie le nouveau compte

Émission dans la transaction du bus : une panne du canal annule l'inscription
plutôt que de créer un compte invérifiable.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 9 : La commande de vérification

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/users/domain/exception/InvalidVerificationLinkException.java`
- Modify: `src/main/java/xyz/sterenn/secondbrain/users/domain/entity/User.java` (ajout de `verify()`)
- Modify: `src/main/java/xyz/sterenn/secondbrain/users/domain/port/UserRepository.java` (ajout de `findById`)
- Modify: `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/persistence/JpaUserRepositoryAdapter.java`
- Modify: `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/persistence/SpringDataUserRepository.java` (rien à ajouter : `findById` vient de `JpaRepository`)
- Create: `src/main/java/xyz/sterenn/secondbrain/users/application/command/VerifyAccount.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/users/application/command/VerifyAccountHandler.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/domain/entity/UserTest.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/application/command/VerifyAccountTest.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/users/application/command/VerifyAccountHandlerTest.java`

**Interfaces:**
- Consumes : tout ce qui précède.
- Produces :
  - `record VerifyAccount(String accountId, String rawToken) implements Command`
  - `VerifyAccountHandler implements CommandHandler<VerifyAccount>`
  - `User.verify()`
  - `UserRepository.findById(UUID id)` → `Optional<User>`
  - `InvalidVerificationLinkException` (constructeur sans argument)

- [ ] **Step 1 : Écrire les tests unitaires de l'agrégat et de la commande**

Créer `src/test/java/xyz/sterenn/secondbrain/users/domain/entity/UserTest.java` :

```java
package xyz.sterenn.secondbrain.users.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

class UserTest {

    @Test
    void nait_non_verifie() {
        assertThat(User.register(new Email("alice@example.com"), "empreinte").isVerified()).isFalse();
    }

    @Test
    void devient_verifie_quand_son_adresse_est_confirmee() {
        User user = User.register(new Email("alice@example.com"), "empreinte");

        user.verify();

        assertThat(user.isVerified()).isTrue();
    }
}
```

Créer `src/test/java/xyz/sterenn/secondbrain/users/application/command/VerifyAccountTest.java` :

```java
package xyz.sterenn.secondbrain.users.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VerifyAccountTest {

    @Test
    void ne_divulgue_pas_le_jeton_dans_sa_representation_textuelle() {
        VerifyAccount commande = new VerifyAccount(
            "11111111-1111-1111-1111-111111111111", "un-jeton-tres-secret");

        assertThat(commande.toString())
            .doesNotContain("un-jeton-tres-secret")
            .contains("11111111-1111-1111-1111-111111111111");
    }
}
```

- [ ] **Step 2 : Écrire le test d'intégration du handler**

Créer `src/test/java/xyz/sterenn/secondbrain/users/application/command/VerifyAccountHandlerTest.java` :

```java
package xyz.sterenn.secondbrain.users.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.exception.AlreadyUsedVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.exception.ExpiredVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

/**
 * Les commandes sont dispatchées par le bus, chemin réel de production. Le jeton en clair
 * est relu dans l'enregistreur de notifications, comme l'utilisateur le lirait dans sa
 * boîte mail.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@Transactional
class VerifyAccountHandlerTest {

    private static final String MOT_DE_PASSE_VALIDE = "chevalpile42";

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private UserRepository users;

    @Autowired
    private RecordingNotificationSender notifications;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void vide_les_notifications() {
        notifications.clear();
    }

    private VerificationNotification inscrit(String email) {
        commandBus.dispatch(new RegisterUser(email, MOT_DE_PASSE_VALIDE));
        return notifications.derniere();
    }

    private void vieillitLeJeton(UUID compte, Duration age) {
        // Le jeton est écrit par le handler avec l'horloge réelle : le faire vieillir en
        // base est le seul moyen d'observer l'expiration sans figer l'horloge du contexte.
        entityManager.flush();
        jdbcTemplate.update(
            "UPDATE users_verification_tokens "
                + "SET expires_at = expires_at - CAST(? AS interval) WHERE user_id = ?",
            age.toHours() + " hours", compte);
        // Sans ce clear, le cache de premier niveau d'Hibernate resservirait l'entité
        // telle qu'elle était avant l'UPDATE, et l'expiration passerait inaperçue.
        entityManager.clear();
    }

    @Test
    void verifie_le_compte_quand_le_jeton_correspond() {
        VerificationNotification notification = inscrit("alice@example.com");

        commandBus.dispatch(new VerifyAccount(
            notification.accountId().toString(), notification.rawToken().value()));

        assertThat(users.findById(notification.accountId()).orElseThrow().isVerified()).isTrue();
    }

    @Test
    void refuse_un_jeton_qui_ne_correspond_pas() {
        VerificationNotification notification = inscrit("bob@example.com");

        assertThatThrownBy(() -> commandBus.dispatch(
            new VerifyAccount(notification.accountId().toString(), "un-autre-jeton")))
            .isInstanceOf(InvalidVerificationLinkException.class);

        assertThat(users.findById(notification.accountId()).orElseThrow().isVerified()).isFalse();
    }

    @Test
    void refuse_un_compte_inconnu() {
        VerificationNotification notification = inscrit("carol@example.com");

        assertThatThrownBy(() -> commandBus.dispatch(
            new VerifyAccount(UUID.randomUUID().toString(), notification.rawToken().value())))
            .isInstanceOf(InvalidVerificationLinkException.class);
    }

    @Test
    void refuse_un_identifiant_de_compte_mal_forme() {
        VerificationNotification notification = inscrit("dave@example.com");

        assertThatThrownBy(() -> commandBus.dispatch(
            new VerifyAccount("pas-un-uuid", notification.rawToken().value())))
            .isInstanceOf(InvalidVerificationLinkException.class);
    }

    @Test
    void refuse_un_jeton_expire() {
        VerificationNotification notification = inscrit("erin@example.com");
        vieillitLeJeton(notification.accountId(), Duration.ofHours(25));

        assertThatThrownBy(() -> commandBus.dispatch(new VerifyAccount(
            notification.accountId().toString(), notification.rawToken().value())))
            .isInstanceOf(ExpiredVerificationLinkException.class);

        assertThat(users.findById(notification.accountId()).orElseThrow().isVerified()).isFalse();
    }

    @Test
    void refuse_un_jeton_deja_utilise() {
        VerificationNotification notification = inscrit("frank@example.com");
        commandBus.dispatch(new VerifyAccount(
            notification.accountId().toString(), notification.rawToken().value()));

        assertThatThrownBy(() -> commandBus.dispatch(new VerifyAccount(
            notification.accountId().toString(), notification.rawToken().value())))
            .isInstanceOf(AlreadyUsedVerificationLinkException.class);

        // Le compte reste vérifié : le second clic ne défait rien.
        assertThat(users.findById(notification.accountId()).orElseThrow().isVerified()).isTrue();
    }
}
```

- [ ] **Step 3 : Lancer les tests pour vérifier qu'ils échouent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.application.command.VerifyAccount*" \
           --tests "xyz.sterenn.secondbrain.users.domain.entity.UserTest"
```
Attendu : ÉCHEC de compilation — `VerifyAccount`, `User.verify()` et `UserRepository.findById` n'existent pas.

- [ ] **Step 4 : Créer l'exception « lien invalide »**

Créer `src/main/java/xyz/sterenn/secondbrain/users/domain/exception/InvalidVerificationLinkException.java` :

```java
package xyz.sterenn.secondbrain.users.domain.exception;

/**
 * Le lien de vérification ne désigne rien d'exploitable : identifiant de compte
 * illisible, compte inexistant, ou jeton ne correspondant pas.
 *
 * <p>Ces trois situations partagent volontairement un seul message. Les distinguer
 * ferait de la route de vérification un oracle : un visiteur pourrait savoir quels
 * comptes existent en observant la réponse.
 */
public class InvalidVerificationLinkException extends RuntimeException {

    public InvalidVerificationLinkException() {
        super("Ce lien de vérification n'est pas valide.");
    }
}
```

- [ ] **Step 5 : Ajouter `verify()` à l'agrégat `User`**

Dans `src/main/java/xyz/sterenn/secondbrain/users/domain/entity/User.java`, après la fabrique `register`, insérer :

```java
    /**
     * Marque l'adresse email comme vérifiée. La garantie qu'un lien ne sert qu'une fois
     * est portée par {@code VerificationToken}, pas ici.
     */
    public void verify() {
        this.verified = true;
    }
```

Mettre aussi à jour la Javadoc de classe : remplacer « ce qui garantit l'invariant « un compte naît non vérifié » » par « ce qui garantit l'invariant « un compte naît non vérifié », que seul {@link #verify()} lève ».

- [ ] **Step 6 : Ajouter `findById` au port et à son adapter**

Dans `src/main/java/xyz/sterenn/secondbrain/users/domain/port/UserRepository.java`, ajouter l'import `java.util.UUID` et la méthode :

```java
    Optional<User> findById(UUID id);
```

Dans `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/persistence/JpaUserRepositoryAdapter.java`, ajouter l'import `java.util.UUID` et l'implémentation :

```java
    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id);
    }
```

`SpringDataUserRepository` n'a rien à déclarer : `findById(UUID)` est hérité de `JpaRepository<User, UUID>`.

- [ ] **Step 7 : Créer la commande**

Créer `src/main/java/xyz/sterenn/secondbrain/users/application/command/VerifyAccount.java` :

```java
package xyz.sterenn.secondbrain.users.application.command;

import xyz.sterenn.secondbrain.shared.bus.Command;

/**
 * Vérification d'une adresse email à partir du lien reçu par notification.
 *
 * <p>Les champs sont des {@code String} bruts, tels qu'ils arrivent dans l'URL : c'est le
 * handler qui les interprète. Un identifiant illisible est un refus métier, pas une
 * erreur de conversion.
 *
 * <p>{@link #toString()} est redéfini pour ne jamais exposer {@code rawToken} : le jeton
 * en clair vaut mot de passe à usage unique tant qu'il n'est pas consommé.
 *
 * @param accountId identifiant du compte, tel que reçu
 * @param rawToken  jeton de vérification en clair
 */
public record VerifyAccount(String accountId, String rawToken) implements Command {

    @Override
    public String toString() {
        return "VerifyAccount[accountId=" + accountId + ", rawToken=***]";
    }
}
```

- [ ] **Step 8 : Créer le handler**

Créer `src/main/java/xyz/sterenn/secondbrain/users/application/command/VerifyAccountHandler.java` :

```java
package xyz.sterenn.secondbrain.users.application.command;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.entity.VerificationToken;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.port.TokenHasher;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.port.VerificationTokenRepository;

/**
 * Orchestre la vérification : interprétation du lien, comparaison du jeton, consommation.
 *
 * <p>Les règles « expiré » et « déjà utilisé » appartiennent à {@code VerificationToken} :
 * ce handler ne fait que les laisser remonter. Aucun {@code @Transactional} ici — la
 * transaction appartient au bus, et la moindre exception annule tout.
 */
@Component
public class VerifyAccountHandler implements CommandHandler<VerifyAccount> {

    private final UserRepository users;
    private final VerificationTokenRepository verificationTokens;
    private final TokenHasher tokenHasher;
    private final Clock clock;

    public VerifyAccountHandler(
            UserRepository users,
            VerificationTokenRepository verificationTokens,
            TokenHasher tokenHasher,
            Clock clock
    ) {
        this.users = users;
        this.verificationTokens = verificationTokens;
        this.tokenHasher = tokenHasher;
        this.clock = clock;
    }

    @Override
    public void handle(VerifyAccount command) {
        UUID accountId = parseAccountId(command.accountId());

        VerificationToken token = verificationTokens.findByUserId(accountId)
            .orElseThrow(InvalidVerificationLinkException::new);

        // Le hash est salé : la seule comparaison possible passe par le hasher.
        if (!tokenHasher.matches(command.rawToken(), token.getTokenHash())) {
            throw new InvalidVerificationLinkException();
        }

        // Lève « déjà utilisé » ou « expiré » le cas échéant.
        token.consume(clock.instant());
        verificationTokens.save(token);

        User user = users.findById(accountId).orElseThrow(InvalidVerificationLinkException::new);
        user.verify();
        users.save(user);
    }

    private UUID parseAccountId(String accountId) {
        try {
            return UUID.fromString(accountId);
        } catch (IllegalArgumentException | NullPointerException e) {
            // Un identifiant illisible se refuse comme un lien invalide, pas comme une panne.
            throw new InvalidVerificationLinkException();
        }
    }
}
```

- [ ] **Step 9 : Lancer les tests pour vérifier qu'ils passent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.application.command.VerifyAccount*" \
           --tests "xyz.sterenn.secondbrain.users.domain.entity.UserTest"
```
Attendu : PASS (2 + 1 + 6 tests).

- [ ] **Step 10 : Lancer toute la suite**

```bash
gtest test
```
Attendu : PASS.

- [ ] **Step 11 : Commit**

```bash
git add -A
git commit -m "feat: ajoute la commande de vérification d'un compte

Le jeton est comparé par le hasher, consommé par le domaine, et le compte
bascule à vérifié. Les trois cas de lien inexploitable partagent un message
unique pour ne pas révéler l'existence d'un compte.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 10 : La route de vérification

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/VerifyAccountController.java`
- Create: `src/main/resources/templates/verification.html`
- Modify: `src/main/resources/templates/register.html` (message de succès)
- Test: `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/web/VerifyAccountControllerTest.java`

**Interfaces:**
- Consumes : `VerifyAccount` (Task 9), les trois exceptions du domaine.
- Produces : route `GET /verification?compte=&jeton=`, vue `verification`, attributs de modèle `verifie` (booléen) et `erreur` (message, absent en cas de succès).

- [ ] **Step 1 : Écrire le test qui échoue**

Créer `src/test/java/xyz/sterenn/secondbrain/users/infrastructure/web/VerifyAccountControllerTest.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

/**
 * Parcours complet vu du dehors : je crée un compte, je lis le lien reçu, je le suis.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VerifyAccountControllerTest {

    private static final String MOT_DE_PASSE_VALIDE = "chevalpile42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingNotificationSender notifications;

    @Autowired
    private UserRepository users;

    @BeforeEach
    void vide_les_notifications() {
        notifications.clear();
    }

    private VerificationNotification inscrit(String email) throws Exception {
        mockMvc.perform(post("/register").param("email", email).param("password", MOT_DE_PASSE_VALIDE));
        return notifications.derniere();
    }

    @Test
    void verifie_le_compte_quand_je_suis_le_lien_recu() throws Exception {
        VerificationNotification notification = inscrit("alice@example.com");

        mockMvc.perform(get("/verification")
                .param("compte", notification.accountId().toString())
                .param("jeton", notification.rawToken().value()))
            .andExpect(status().isOk())
            .andExpect(view().name("verification"))
            .andExpect(model().attribute("verifie", true))
            .andExpect(model().attributeDoesNotExist("erreur"));

        assertThat(users.findById(notification.accountId()).orElseThrow().isVerified()).isTrue();
    }

    @Test
    void refuse_un_lien_falsifie() throws Exception {
        VerificationNotification notification = inscrit("bob@example.com");

        mockMvc.perform(get("/verification")
                .param("compte", notification.accountId().toString())
                .param("jeton", "un-autre-jeton"))
            .andExpect(status().isOk())
            .andExpect(view().name("verification"))
            .andExpect(model().attribute("verifie", false))
            .andExpect(model().attributeExists("erreur"));

        assertThat(users.findById(notification.accountId()).orElseThrow().isVerified()).isFalse();
    }

    @Test
    void refuse_un_lien_dont_le_compte_est_inconnu() throws Exception {
        VerificationNotification notification = inscrit("carol@example.com");

        mockMvc.perform(get("/verification")
                .param("compte", UUID.randomUUID().toString())
                .param("jeton", notification.rawToken().value()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("verifie", false));
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
            .andExpect(status().isOk())
            .andExpect(model().attribute("verifie", false))
            .andExpect(model().attribute("erreur", "Ce lien de vérification a déjà été utilisé."));
    }

    @Test
    void refuse_un_lien_sans_parametre() throws Exception {
        mockMvc.perform(get("/verification"))
            .andExpect(status().isOk())
            .andExpect(view().name("verification"))
            .andExpect(model().attribute("verifie", false));
    }
}
```

- [ ] **Step 2 : Lancer le test pour vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.infrastructure.web.VerifyAccountControllerTest"
```
Attendu : ÉCHEC — 404 sur `/verification`, la route n'existe pas.

- [ ] **Step 3 : Créer le contrôleur**

Créer `src/main/java/xyz/sterenn/secondbrain/users/infrastructure/web/VerifyAccountController.java` :

```java
package xyz.sterenn.secondbrain.users.infrastructure.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.application.command.VerifyAccount;
import xyz.sterenn.secondbrain.users.domain.exception.AlreadyUsedVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.exception.ExpiredVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidVerificationLinkException;

/**
 * Adapter entrant de la route de vérification. Il traduit les paramètres du lien en
 * commande, puis les refus métier en message affichable. Aucune règle métier ici.
 *
 * <p>Les paramètres sont optionnels et vides par défaut : un lien tronqué doit donner la
 * même page de refus qu'un lien falsifié, pas une erreur 400.
 */
@Controller
public class VerifyAccountController {

    private final CommandBus commandBus;

    public VerifyAccountController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @GetMapping("/verification")
    public String verify(
            @RequestParam(name = "compte", defaultValue = "") String compte,
            @RequestParam(name = "jeton", defaultValue = "") String jeton,
            Model model
    ) {
        try {
            commandBus.dispatch(new VerifyAccount(compte, jeton));
            model.addAttribute("verifie", true);
        } catch (InvalidVerificationLinkException
                 | ExpiredVerificationLinkException
                 | AlreadyUsedVerificationLinkException e) {
            model.addAttribute("verifie", false);
            model.addAttribute("erreur", e.getMessage());
        }
        return "verification";
    }
}
```

- [ ] **Step 4 : Créer le template**

Créer `src/main/resources/templates/verification.html` :

```html
<!DOCTYPE html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Vérification de mon adresse email</title>
</head>
<body>
<h1>Vérification de mon adresse email</h1>

<p th:if="${verifie}">
    Votre adresse email est vérifiée. Votre compte est prêt.
</p>

<p th:unless="${verifie}" th:text="${erreur}"></p>

<p><a th:href="@{/}">Retour à l'accueil</a></p>
</body>
</html>
```

- [ ] **Step 5 : Rendre explicite le message de succès de l'inscription**

Dans `src/main/resources/templates/register.html`, remplacer :

```html
<p th:if="${param.success}">
    Votre compte a été créé. Il reste à vérifier.
</p>
```

par :

```html
<p th:if="${param.success}">
    Votre compte a été créé. Un mail vient de vous être envoyé : suivez le lien qu'il
    contient pour vérifier votre adresse email. Ce lien est valable 24 heures.
</p>
```

- [ ] **Step 6 : Lancer le test pour vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.users.infrastructure.web.VerifyAccountControllerTest"
```
Attendu : PASS (5 tests).

- [ ] **Step 7 : Lancer toute la suite**

```bash
gtest build
```
Attendu : BUILD SUCCESSFUL — c'est ce que fait la CI.

- [ ] **Step 8 : Vérifier le parcours à la main**

```bash
docker compose up --build -d
docker compose logs -f app   # attendre le démarrage, puis Ctrl+C
```

Puis : créer un compte sur <http://localhost:8080/register>, ouvrir <http://localhost:8025>, ouvrir le mail reçu, cliquer le lien, constater la page de confirmation. Vérifier en base via Adminer (<http://localhost:8081>, serveur `db`, base/user/mdp `second_brain`) que `users_users.verified` vaut `true` et que `users_verification_tokens.consumed_at` est renseigné. Recliquer le lien : « Ce lien de vérification a déjà été utilisé. »

```bash
docker compose down
```

- [ ] **Step 9 : Commit**

```bash
git add -A
git commit -m "feat: ajoute la route de vérification d'adresse email

Une classe de contrôleur pour la seule route GET /verification, qui traduit
les refus métier en message affichable.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 11 : Documentation

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1 : Mettre à jour l'arborescence**

Dans la section « Architecture » de `CLAUDE.md`, remplacer le bloc d'arborescence par :

```
xyz.sterenn.secondbrain
├── config/                  SecurityConfig, OpenApiConfig, ClockConfiguration — transverse
├── shared/
│   ├── bus/                 socle CQRS, aucune dépendance métier
│   └── web/                 pages n'appartenant à aucun contexte (accueil)
└── users/                   bounded context (gabarit pour les suivants)
    ├── domain/              règles métier pures et transverses (PasswordPolicy)
    │   ├── entity/          agrégats (User, VerificationToken)
    │   ├── valueobject/     valeurs validées et normalisées (Email, RawVerificationToken,
    │   │                    Notification et ses implémentations)
    │   ├── port/            interfaces vers l'extérieur (UserRepository, PasswordHasher,
    │   │                    TokenHasher, VerificationTokenRepository, NotificationSender)
    │   └── exception/       refus métier, messages affichables tels quels
    ├── application/
    │   ├── command/         une commande + son handler par intention d'écriture
    │   └── query/           une query + son handler + son modèle de lecture
    └── infrastructure/
        ├── persistence/     ADAPTERS JPA des ports de stockage + mapping (EmailAttributeConverter)
        ├── security/        ADAPTERS des ports PasswordHasher et TokenHasher
        ├── email/           ADAPTER du port NotificationSender
        └── web/             ADAPTERS entrants (un contrôleur par route + form de liaison)
```

- [ ] **Step 2 : Décrire le flux de vérification**

Après la section « Le flux d'une écriture », ajouter :

```markdown
### Le flux de la vérification d'email

L'inscription émet un jeton aléatoire, n'en persiste que l'empreinte salée
(`TokenHasher`, adapter BCrypt) et envoie le clair par le port `NotificationSender`.
Notifier est une décision du domaine ; l'email n'est qu'un canal, et l'adapter
`users/infrastructure/email/` est seul à connaître l'URL publique, le sujet et le corps.
`Notification` est une interface **scellée** : l'adapter fait un `switch` exhaustif, donc
un nouveau type de notification non traité ne compile pas.

`GET /verification?compte=&jeton=` recharge le jeton du compte, le compare via le hasher
puis le consomme. `VerificationToken` porte les deux règles — expiration à 24 h et usage
unique — et lève lui-même le refus correspondant. Les trois façons de présenter un lien
inexploitable (UUID illisible, compte inconnu, jeton faux) partagent volontairement un
seul message : les distinguer ferait de la route un oracle d'existence de compte.

L'envoi se fait **dans la transaction du bus** : une panne du canal annule l'inscription.
Tant que « renvoyer le lien » n'existe pas, un compte créé sans notification serait
définitivement invérifiable.
```

- [ ] **Step 3 : Documenter Mailpit**

Dans la section « Commandes », après le paragraphe sur les points d'entrée, ajouter Mailpit à la liste : `Mailpit <http://localhost:8025>` — tous les mails émis en développement y sont capturés, aucun ne sort de la machine.

- [ ] **Step 4 : Ajouter l'écart assumé**

Dans « Écarts assumés », ajouter :

```markdown
5. `VerificationToken` référence son compte par un `UUID` et non par un `@ManyToOne` :
   deux agrégats distincts ne se tiennent pas par une association JPA. La cohérence est
   garantie par la clé étrangère en base, pas par le graphe d'objets.
```

Les quatre points existants sont inchangés : la vérification n'introduit ni session ni CSRF, la dette du point 2 reste celle du ticket « login ».

- [ ] **Step 5 : Lancer la suite une dernière fois**

```bash
gtest build
```
Attendu : BUILD SUCCESSFUL.

- [ ] **Step 6 : Commit**

```bash
git add CLAUDE.md
git commit -m "docs: documente la vérification d'email dans CLAUDE.md

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Notes d'exécution

**Pièges connus, dans l'ordre où ils mordent :**

1. **Task 3 — l'entité et la migration sont indissociables.** `ddl-auto: validate` fait échouer le démarrage du contexte si l'une manque, donc *toute* la suite de tests tombe, pas seulement celle de la tâche.
2. **Task 8 — les tests d'intégration qui déclenchent une inscription doivent importer `RecordingNotificationSenderConfiguration`.** Sans lui, c'est le vrai adapter email qui part et échoue faute de SMTP. Passer en revue tous les tests qui dispatchent `RegisterUser`.
3. **L'enregistreur est un bean partagé par le contexte** : le rollback de `@Transactional` ne le vide pas. Un `@BeforeEach` qui appelle `clear()` est obligatoire dans chaque classe qui l'utilise.
4. **Ne jamais annoter un handler de `@Transactional`** : le JDK proxy casse la résolution du type générique et l'application ne démarre plus.
5. **`gtest` doit être défini dans le shell courant** (voir Global Constraints). Il n'y a ni JDK ni Gradle sur l'hôte.
