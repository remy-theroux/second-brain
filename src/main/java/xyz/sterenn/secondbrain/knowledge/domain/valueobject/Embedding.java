package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Arrays;
import java.util.Objects;
import xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy;

public final class Embedding {

    private final float[] values;

    private Embedding(float[] values) {
        this.values = values;
    }

    public static Embedding of(float[] values) {
        Objects.requireNonNull(values, "Le vecteur est obligatoire");
        if (values.length != EmbeddingPolicy.DIMENSIONS) {
            throw new IllegalArgumentException(
                    "Un vecteur porte " + EmbeddingPolicy.DIMENSIONS + " dimensions, reçu : " + values.length);
        }
        return new Embedding(values.clone());
    }

    public float[] values() {
        return values.clone();
    }

    // Arrays.equals: the equals a record would generate would compare array references.
    @Override
    public boolean equals(Object other) {
        return other instanceof Embedding vector && Arrays.equals(values, vector.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "Embedding[" + values.length + " dimensions]";
    }
}
