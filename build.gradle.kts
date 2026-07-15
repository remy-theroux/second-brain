plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
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

dependencies {
    // Web / REST
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Persistance
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Spring Boot 4 : le starter Flyway apporte l'auto-config (module spring-boot-flyway).
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    // Sécurité
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Observabilité
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Documentation API
    implementation(libs.springdoc.openapi)

    // Dev : hot reload (l'app tourne dans un conteneur Compose, donc pas de
    // module spring-boot-docker-compose qui gérerait Compose depuis l'app).
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
