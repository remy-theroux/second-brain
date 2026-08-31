package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.exception.EmbeddingUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;

/**
 * Port sortant vers le service qui transforme du texte en vecteurs.
 *
 * <p>Le domaine ignore qu'il y a un réseau, un modèle et des lots. Il demande des vecteurs
 * pour des textes, et il exige deux choses : autant de vecteurs que de textes, et
 * <strong>dans le même ordre</strong>. Sans cette garantie, l'appelant ne pourrait plus
 * rattacher un vecteur à l'extrait dont il provient — c'est tout le contrat.
 *
 * <p>Le lotissement appartient à l'adapter : c'est une propriété du transport, pas une règle
 * métier. L'appelant passe sa liste entière.
 */
public interface EmbeddingPort {

    /**
     * Vectorise les textes, dans l'ordre.
     *
     * @return autant de vecteurs que de textes ; une liste vide pour une entrée vide
     * @throws EmbeddingUnavailableException si le service ne répond pas, ou répond quelque
     *     chose qu'on ne peut pas rattacher aux textes demandés
     */
    List<Embedding> embed(List<String> texts);
}
