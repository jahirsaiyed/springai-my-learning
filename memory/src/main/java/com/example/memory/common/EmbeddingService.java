package com.example.memory.common;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Wraps the Spring AI EmbeddingModel to provide consistent embedding generation
 * across all memory types.
 */
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    public List<float[]> embedBatch(List<String> texts) {
        return embeddingModel.embed(texts).stream()
            .map(e -> e)
            .toList();
    }

    public int dimensions() {
        return embeddingModel.dimensions();
    }
}
