package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import java.util.List;

/**
 * {@code input} and not {@code prompt}: {@code /api/embed} is the batching route of Ollama,
 * where {@code /api/embeddings}, in the singular, takes a single text.
 */
record OllamaEmbeddingRequest(String model, List<String> input) {}
