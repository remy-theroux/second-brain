package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import java.util.List;

/**
 * Le corps de {@code POST /api/embed}. Package-private : la forme du fil ne regarde que
 * l'adapter.
 *
 * <p>{@code input} et non {@code prompt} : c'est la route lotissable d'Ollama.
 * {@code /api/embeddings}, au singulier, est l'ancienne, qui ne prend qu'un texte.
 */
record OllamaEmbeddingRequest(String model, List<String> input) {}
