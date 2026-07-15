package xyz.sterenn.secondbrain;

import org.springframework.boot.SpringApplication;

/**
 * Point d'entrée de dev pour lancer l'application localement sur une PostgreSQL
 * Testcontainers (utile hors Docker Compose). Exécuter cette classe depuis l'IDE.
 */
public class TestSecondBrainApplication {

    public static void main(String[] args) {
        SpringApplication.from(SecondBrainApplication::main)
            .with(TestcontainersConfiguration.class)
            .run(args);
    }
}
