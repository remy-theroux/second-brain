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
        // Gradle downloads JDK 25 automatically when it is missing (auto-provisioning).
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
    // Isolates DevTools: present at runtime in dev, never in the production jar.
    developmentOnly
}

repositories {
    mavenCentral()
}

spotless {
    java {
        // Palantir and not google-java-format: the latter, even in AOSP style, breaks
        // fluent chains into cascades of 8 spaces (`SecurityConfig` is the example) and
        // pushes so far to the right that end-of-line comments end up
        // cut in mid-sentence. Palantir was born of that complaint: 4 spaces of
        // continuation, 120 columns, and a treatment of builders that stays readable.
        //
        // Nothing else in this block: the formatter already sorts imports and removes the
        // unused ones, `removeUnusedImports()` or `trimTrailingWhitespace()` would be
        // redundant.
        palantirJavaFormat(libs.versions.palantirJavaFormat.get())
    }
}

dependencies {
    // Web / REST — no view rendered server-side: the Vue front end is a separate project,
    // and the only non-API route (GET /verification) answers with a redirection.
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Notifications
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // Domain events: publication and consumption over RabbitMQ. The transport is a
    // choice of the 2026-08-25 spec (decisions 2 to 5); the domain does not know about it.
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Spring Boot 4: the Flyway starter brings the auto-config (spring-boot-flyway module).
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation(libs.flyway.postgresql)
    // The `vector` type on the Hibernate side. Arrived with the chunk table and not before:
    // a dependency without a caller is dead weight.
    implementation(libs.hibernate.vector)
    runtimeOnly(libs.postgresql)

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    // Brings spring-security-oauth2-jose (NimbusJwtEncoder AND NimbusJwtDecoder) and
    // spring-security-oauth2-resource-server. Spring Boot 4 name: the old
    // spring-boot-starter-oauth2-resource-server still exists but is deprecated.
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

    // Vectorisation: OllamaEmbeddingAdapter speaks HTTP through RestClient. Under Boot 4, this
    // starter is no longer pulled in by spring-boot-starter-web: RestClientAutoConfiguration has
    // been extracted into its own module (spring-boot-restclient), and without this starter the
    // RestClient.Builder bean does not exist — the context refuses to start.
    implementation("org.springframework.boot:spring-boot-starter-restclient")

    // Observability
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // API documentation
    implementation(libs.springdoc.openapi)

    // Text extraction from documents. One extractor per format rather than Apache Tika,
    // whose unified XHTML flattens precisely the semantics we are trying to keep
    // (ADR-0026). None of these versions is covered by the Spring Boot BOM.
    implementation(libs.commonmark)
    implementation(libs.poi.ooxml)
    implementation(libs.pdfbox)

    // Token counting for the chunking. Behind the TokenCounter port: the domain
    // counts, it does not know with which yardstick.
    implementation(libs.jtokkit)
    // Generation: behind the LlmPort port. Its imports never leave
    // knowledge/infrastructure/ai — the same lock as for any provider.
    implementation(libs.langchain4j)
    implementation(libs.langchain4j.ollama)
    // Object storage of the originals (Garage, S3-compatible — see compose.yaml). A
    // Gradle platform(...) and not the io.spring.dependency-management plugin: the latter
    // already holds the Spring Boot BOM, and nothing in the AWS BOM overlaps with it — the S3 SDK
    // depends neither on Jackson (the S3 protocol is XML, with a homemade parser, so zero
    // conflict with Jackson 3 / Spring Boot 4) nor, once the two exclusions below are
    // in place, on Apache HttpClient. A Gradle constraint is enough here, and it only bears
    // on the software.amazon.awssdk:* modules.
    implementation(platform(libs.awssdk.bom))
    // Two HTTP clients of the SDK arrive here unasked, inherited from the parent pom
    // software.amazon.awssdk:services. Why they are excluded — and why the two reasons
    // are not equivalent — is written once only, on awssdk-s3 in
    // gradle/libs.versions.toml.
    implementation(libs.awssdk.s3) {
        exclude(group = "software.amazon.awssdk", module = "apache5-client")
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
    // The only HTTP client we want to see on the classpath, and the only one to declare:
    // same block of the version catalog for the detail.
    implementation(libs.awssdk.url.connection.client)

    // Dev: hot reload (the app runs in a Compose container, hence no
    // spring-boot-docker-compose module that would drive Compose from the app).
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
    // The signing secret has no default value (see application.yml): without
    // it, no @SpringBootTest starts any more. An environment variable is the
    // only *certain* way to supply it: it takes precedence over every configuration
    // file, without depending on the precedence between application.properties and
    // application.yml.
    environment("SECONDBRAIN_JWT_SECRET", "secret-de-test-second-brain-32-octets-minimum")
    // Same status for the key that enciphers the Google refresh tokens: 32 bytes in Base64.
    environment("SECONDBRAIN_DRIVE_TOKEN_KEY", "Y2xlLWRlLXRlc3QtZHJpdmUtc2Vjb25kLWJyYWluMzI=")
}

// Builds the binary extraction fixtures (docx, pdf) into src/test/resources/fixtures/.
// Run BY HAND, once — `gtest generateFixtures` — and its output is versioned.
// Neither a test nor a build step: a binary rebuilt on every run would make a diff on
// every run, and the suite would only test its own output of the day.
tasks.register<JavaExec>("generateFixtures") {
    group = "build"
    description = "Écrit les documents d'essai binaires ; à lancer à la main, puis committer"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "xyz.sterenn.secondbrain.knowledge.fixtures.FixtureFactory"
}
