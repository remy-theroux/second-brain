# syntax=docker/dockerfile:1

###############################################################################
# Étape 1 — build : compile le jar et l'éclate en layers pour le cache Docker.
###############################################################################
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Wrapper + descripteurs d'abord : les dépendances restent en cache tant que
# ces fichiers ne changent pas.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies --quiet || true

# Code source puis build du jar.
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar \
    && cp build/libs/*.jar application.jar \
    # jarmode=tools (Spring Boot 3.3+) : éclate le jar en layers. La couche
    # `application/` contient un application.jar fin qui référence les libs.
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

###############################################################################
# Étape 2 — runtime : JRE minimal, utilisateur non-root.
###############################################################################
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /application

RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

# Copie des layers du moins volatil au plus volatil (cache optimal).
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD ["java", "-version"]

ENTRYPOINT ["java", "-jar", "application.jar"]
