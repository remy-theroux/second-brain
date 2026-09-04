package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import java.util.List;

/**
 * {@code input} et non {@code prompt} : {@code /api/embed} est la route lotissable d'Ollama,
 * là où {@code /api/embeddings}, au singulier, ne prend qu'un texte.
 */
record OllamaEmbeddingRequest(String model, List<String> input) {}
