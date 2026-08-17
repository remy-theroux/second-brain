package xyz.sterenn.secondbrain.users.application.query;

/**
 * Projection de lecture d'un jeton d'accès : sa valeur et le nombre de secondes qui lui
 * restent. Le calcul du reste-à-vivre appartient au handler, seul à détenir l'horloge ;
 * le contrôleur n'a plus qu'à recopier ces deux champs dans la réponse HTTP.
 *
 * @param value     jeton à présenter en {@code Authorization: Bearer …}
 * @param expiresIn secondes restantes avant expiration
 */
public record AccessTokenView(String value, long expiresIn) {

    /**
     * Le jeton est un porteur d'identité : il ne doit apparaître dans aucun log ni dans
     * aucun message d'échec d'assertion.
     */
    @Override
    public String toString() {
        return "AccessTokenView[value=***, expiresIn=" + expiresIn + "]";
    }
}
