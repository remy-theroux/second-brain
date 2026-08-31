package xyz.sterenn.secondbrain.knowledge.domain;

/**
 * Ce que le domaine tient pour un vecteur.
 *
 * <p>Règle métier pure, statique, sans dépendance — à la racine du domaine, à côté
 * d'{@link ExtractionPolicy} : elle se teste sans Spring.
 *
 * <p><strong>La dimension n'est pas une propriété de configuration, et c'est délibéré.</strong>
 * Elle est le contrat entre le modèle qui produit les vecteurs, la colonne qui les range et
 * l'index qui les compare : la désaligner par un fichier de configuration rendrait toute la
 * base incohérente sans qu'aucune erreur ne le dise à temps. Les vecteurs de deux modèles ne
 * se comparent de toute façon pas — changer de modèle est une migration et une réindexation,
 * pas une variable d'environnement.
 *
 * <p>Même arbitrage que la durée de vie du jeton d'accès, qui vit dans
 * {@code AccessTokenPolicy} et non dans {@code application.yml}.
 */
public final class EmbeddingPolicy {

    /** Ce que produit {@code bge-m3}, et ce qu'attend la colonne {@code vector(1024)}. */
    public static final int DIMENSIONS = 1024;

    private EmbeddingPolicy() {
        // règle métier, pas un objet
    }
}
