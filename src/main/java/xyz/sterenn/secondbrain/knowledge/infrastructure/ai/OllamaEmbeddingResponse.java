package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import java.util.List;

/** Ollama rend un vecteur par texte, dans l'ordre d'entrée. */
record OllamaEmbeddingResponse(List<float[]> embeddings) {}
