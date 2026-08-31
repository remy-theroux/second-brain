package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import java.util.List;

/**
 * Ce qu'Ollama rend : un vecteur par texte, dans l'ordre d'entrée.
 *
 * <p>Les autres champs de la réponse ({@code model}, les durées) ne sont pas déclarés :
 * Jackson ignore ce qu'il ne sait pas placer, et un champ déclaré est un champ qu'on
 * s'engage à maintenir.
 */
record OllamaEmbeddingResponse(List<float[]> embeddings) {}
