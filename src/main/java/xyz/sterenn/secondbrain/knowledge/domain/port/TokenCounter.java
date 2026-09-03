package xyz.sterenn.secondbrain.knowledge.domain.port;

/**
 * Port sortant vers la toise qui mesure un texte en tokens.
 *
 * <p><strong>Pourquoi un port pour trois lignes de jtokkit.</strong> Le ticket impose
 * {@code cl100k_base}, qui est le tokenizer d'OpenAI — et {@code bge-m3} n'en est pas un : il
 * s'appuie sur le sentencepiece XLM-RoBERTa, dont le découpage du français est sensiblement
 * différent. On mesure en pieds une étoffe vendue en mètres. C'est sans danger, parce que
 * {@code cl100k} sur-compte le français : un extrait de 800 « tokens cl100k » reste très en
 * deçà des 8192 que le modèle accepte, et le plafond ne peut donc pas être dépassé par
 * surprise. Mais la cible est un <strong>proxy</strong>, pas une mesure, et deux choses en
 * découlent : le jour où le modèle change, la toise change sans qu'on touche au découpage ;
 * et les tests du découpage prennent un compteur « un mot égale un token », ce qui rend les
 * frontières d'extraits lisibles dans les assertions au lieu d'être des nombres magiques.
 *
 * <p>Interface fonctionnelle de fait : un test la satisfait par une lambda.
 */
public interface TokenCounter {

    /**
     * @return le nombre de tokens du texte ; {@code 0} pour un texte absent ou vide. Ne lève
     *     jamais : compter n'est pas un refus métier, et un compteur qui échouerait ferait
     *     échouer un découpage pour une raison que l'utilisateur ne pourrait pas corriger.
     */
    int count(String text);
}
