package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Arrays;
import java.util.Objects;
import xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy;

/**
 * Un vecteur produit par le service de vectorisation.
 *
 * <p>Objet-valeur : il valide dans sa fabrique, il est immuable, et deux vecteurs de même
 * contenu sont égaux. <strong>Il est impossible d'en construire un dont la dimension ne soit
 * pas celle du modèle</strong> — une configuration pointée sur un autre modèle se fait donc
 * refuser à l'endroit exact où le vecteur entre dans le domaine, et non trois couches plus
 * loin par une contrainte PostgreSQL au moment de l'écriture.
 *
 * <p>Une classe et non un {@code record} : le champ est un {@code float[]}, et l'{@code
 * equals} qu'un record engendrerait comparerait les <em>références</em> de tableau. Deux
 * vecteurs identiques seraient différents.
 *
 * <p>Le tableau est copié à l'entrée comme à la sortie. Un tableau est mutable ; sans ces
 * deux copies, l'appelant garderait la main sur l'état d'un objet-valeur.
 *
 * <p>Le refus est une {@link IllegalArgumentException} et non un refus métier : ce n'est
 * jamais l'utilisateur qui a mal fait, c'est la configuration ou le service. C'est l'adapter
 * qui la traduit en un message affichable, comme un adapter de persistance traduit une
 * violation de contrainte.
 */
public final class Embedding {

    private final float[] values;

    private Embedding(float[] values) {
        this.values = values;
    }

    /**
     * @throws IllegalArgumentException si la dimension n'est pas celle
     *     d'{@link EmbeddingPolicy#DIMENSIONS}
     */
    public static Embedding of(float[] values) {
        Objects.requireNonNull(values, "Le vecteur est obligatoire");
        if (values.length != EmbeddingPolicy.DIMENSIONS) {
            throw new IllegalArgumentException(
                    "Un vecteur porte " + EmbeddingPolicy.DIMENSIONS + " dimensions, reçu : " + values.length);
        }
        return new Embedding(values.clone());
    }

    /** Une copie : personne ne modifie l'état d'un objet-valeur. */
    public float[] values() {
        return values.clone();
    }

    @Override
    public boolean equals(Object autre) {
        return autre instanceof Embedding vecteur && Arrays.equals(values, vecteur.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    /** Volontairement sans les valeurs : mille flottants dans un journal ne servent personne. */
    @Override
    public String toString() {
        return "Embedding[" + values.length + " dimensions]";
    }
}
