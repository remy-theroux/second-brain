package xyz.sterenn.secondbrain.knowledge.domain;

/** Voir ADR-0025. */
public final class ExtractionPolicy {

    public static final int MINIMUM_USEFUL_CHARACTERS = 50;

    private ExtractionPolicy() {}

    public static boolean isExploitable(int characterCount) {
        return characterCount >= MINIMUM_USEFUL_CHARACTERS;
    }
}
