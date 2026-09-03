package xyz.sterenn.secondbrain.knowledge.domain;

/**
 * Ce que le domaine tient pour un extrait de bonne taille.
 *
 * <p>Règle métier pure, statique, sans dépendance — à la racine du domaine, à côté
 * d'{@link ExtractionPolicy} et d'{@link EmbeddingPolicy} : elle se teste sans Spring.
 *
 * <p><strong>Ces trois nombres sont comptés avec la toise d'un autre.</strong> Le comptage
 * passe par {@code TokenCounter}, dont l'unique adapter emploie {@code cl100k_base}, le
 * tokenizer d'OpenAI — {@code bge-m3} s'appuie sur un sentencepiece XLM-RoBERTa. C'est sans
 * danger parce que {@code cl100k} sur-compte le français : un extrait de 800 tokens comptés
 * ici reste très en deçà des 8192 que le modèle accepte. Mais 600 est un <em>proxy</em>, pas
 * une mesure.
 */
public final class ChunkingPolicy {

    /**
     * Ce vers quoi l'accumulation tend. <strong>Elle gouverne l'accumulation, pas la découpe
     * d'un bloc déjà valide</strong> : une section de 700 tokens n'est pas coupée pour se
     * rapprocher de la cible, elle donne un extrait.
     */
    public static final int TARGET_TOKENS = 600;

    /**
     * Ce qu'aucun extrait ne dépasse — le premier scénario du ticket. Le recouvrement cède
     * devant lui, et une phrase qui le franchit à elle seule est coupée net.
     *
     * <p>Le plafond borne le <strong>corps</strong> de l'extrait, pas ce qui part réellement à
     * la vectorisation : {@code Chunk.contextualised} y ajoute un préfixe (« Document: … —
     * Section: … »), donc ce qui quitte le domaine pèse un peu plus. Sans danger — le plafond
     * est déjà une marge conservatrice face aux 8192 tokens que {@code bge-m3} accepte — mais
     * un lecteur pressé croirait autrement que le plafond borne ce que le modèle voit.
     */
    public static final int MAX_TOKENS = 800;

    /**
     * Ce que deux extraits consécutifs d'une même section partagent, soit 15 % de la cible.
     * Il se prend en <strong>phrases entières</strong> : une fenêtre glissante de tokens
     * reproduirait à la jointure exactement la coupure que le reste de l'algorithme évite.
     */
    public static final int OVERLAP_TOKENS = 90;

    private ChunkingPolicy() {
        // règle métier, pas un objet
    }
}
