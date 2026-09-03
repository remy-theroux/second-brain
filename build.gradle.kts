plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
    alias(libs.plugins.spotless)
}

group = "xyz.sterenn"
version = "0.0.1-SNAPSHOT"
description = "Second Brain"

java {
    toolchain {
        // Gradle télécharge le JDK 25 automatiquement s'il est absent (auto-provisioning).
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
    // Isole DevTools : présent au runtime en dev, jamais dans le jar de prod.
    developmentOnly
}

repositories {
    mavenCentral()
}

spotless {
    java {
        // Palantir et non google-java-format : ce dernier, même en style AOSP, casse les
        // chaînes fluent en cascades de 8 espaces (`SecurityConfig` en est l'exemple) et
        // repousse si loin à droite que les commentaires en fin de ligne se retrouvent
        // découpés en milieu de phrase. Palantir est né de ce reproche : 4 espaces de
        // continuation, 120 colonnes, et un traitement des builders qui reste lisible.
        //
        // Rien d'autre dans ce bloc : le formateur trie déjà les imports et supprime les
        // inutilisés, `removeUnusedImports()` ou `trimTrailingWhitespace()` feraient double
        // emploi.
        palantirJavaFormat(libs.versions.palantirJavaFormat.get())
    }
}

dependencies {
    // Web / REST — aucune vue rendue côté serveur : le front Vue est un projet séparé,
    // et la seule route non-API (GET /verification) répond par une redirection.
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Notifications
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // Événements métier : publication et consommation sur RabbitMQ. Le transport est un
    // choix de la spec 2026-08-25 (décisions 2 à 5) ; le domaine ne le connaît pas.
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    // Persistance
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Spring Boot 4 : le starter Flyway apporte l'auto-config (module spring-boot-flyway).
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    // Sécurité
    implementation("org.springframework.boot:spring-boot-starter-security")
    // Apporte spring-security-oauth2-jose (NimbusJwtEncoder ET NimbusJwtDecoder) et
    // spring-security-oauth2-resource-server. Nom Spring Boot 4 : l'ancien
    // spring-boot-starter-oauth2-resource-server existe toujours mais est déprécié.
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

    // Vectorisation : OllamaEmbeddingAdapter parle HTTP via RestClient. Sous Boot 4, ce
    // starter n'est plus tiré par spring-boot-starter-web : RestClientAutoConfiguration a
    // été extraite dans son propre module (spring-boot-restclient), et sans ce starter le
    // bean RestClient.Builder n'existe pas — le contexte refuse de démarrer.
    implementation("org.springframework.boot:spring-boot-starter-restclient")

    // Observabilité
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Documentation API
    implementation(libs.springdoc.openapi)

    // Extraction du texte des documents. Un extracteur par format plutôt qu'Apache Tika,
    // dont l'XHTML unifié aplatit précisément la sémantique qu'on cherche à garder
    // (ADR-0026). Aucune de ces versions n'est couverte par le BOM Spring Boot.
    implementation(libs.commonmark)
    implementation(libs.poi.ooxml)
    implementation(libs.pdfbox)

    // Comptage de tokens pour le découpage. Derrière le port TokenCounter : le domaine
    // compte, il ne sait pas avec quelle toise.
    implementation(libs.jtokkit)

    // Dev : hot reload (l'app tourne dans un conteneur Compose, donc pas de
    // module spring-boot-docker-compose qui gérerait Compose depuis l'app).
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-rabbitmq")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Le secret de signature n'a aucune valeur par défaut (voir application.yml) : sans
    // lui, plus aucun @SpringBootTest ne démarre. Une variable d'environnement est le
    // seul moyen *certain* de le fournir : elle prime sur tous les fichiers de
    // configuration, sans dépendre de la précédence entre application.properties et
    // application.yml.
    environment("SECONDBRAIN_JWT_SECRET", "secret-de-test-second-brain-32-octets-minimum")
}

// Fabrique les fixtures binaires d'extraction (docx, pdf) dans src/test/resources/fixtures/.
// Lancée À LA MAIN, une fois — `gtest generateFixtures` — et son produit est versionné.
// Ni test, ni étape de build : un binaire refabriqué à chaque exécution ferait un diff à
// chaque exécution, et la suite ne testerait plus que sa propre sortie du jour.
tasks.register<JavaExec>("generateFixtures") {
    group = "build"
    description = "Écrit les documents d'essai binaires ; à lancer à la main, puis committer"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "xyz.sterenn.secondbrain.knowledge.fixtures.FixtureFactory"
}
