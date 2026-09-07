package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import java.util.List;

/** Ollama returns one vector per text, in input order. */
record OllamaEmbeddingResponse(List<float[]> embeddings) {}
