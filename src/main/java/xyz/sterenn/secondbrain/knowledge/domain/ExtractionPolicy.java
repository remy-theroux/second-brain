package xyz.sterenn.secondbrain.knowledge.domain;

/**
 * Ce que le domaine tient pour du texte exploitable.
 *
 * <p>Règle métier pure, statique, sans dépendance — à la racine du domaine, comme
 * {@code PasswordPolicy} l'est pour {@code users} : elle se teste sans Spring.
 *
 * <p>Le plancher n'est pas une précaution de confort. Un PDF numérisé rend rarement zéro
 * caractère : il rend un numéro de page, un tampon, une mention de scanner. Un test
 * {@code isBlank()} seul les laisserait passer, et c'est exactement le vide silencieux que
 * le ticket interdit. Voir ADR-0025.
 */
public final class ExtractionPolicy {

    /**
     * En dessous, aucun document n'est tenu pour exploitable. Cinquante caractères, c'est
     * moins d'une phrase : le seuil vise la numérisation muette, pas le document bref.
     */
    public static final int MINIMUM_USEFUL_CHARACTERS = 50;

    private ExtractionPolicy() {
        // règle métier, pas un objet
    }

    public static boolean isExploitable(int characterCount) {
        return characterCount >= MINIMUM_USEFUL_CHARACTERS;
    }
}
