package com.example.memory.search;

import com.example.memory.search.ElasticsearchSearchService.ScoredChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hybrid search combining vector similarity (pgvector) and BM25 (Elasticsearch)
 * using Reciprocal Rank Fusion (RRF) to merge rankings.
 *
 * RRF formula: score(d) = sum( 1 / (k + rank_i(d)) ) for each ranking list i
 * where k is a constant (default 60) that controls the influence of high-ranked items.
 */
@Service
public class HybridSearchService {

    private static final Logger log = LoggerFactory.getLogger(HybridSearchService.class);
    private static final int RRF_K = 60;

    private final VectorStore vectorStore;
    private final ElasticsearchSearchService elasticsearchSearch;

    public HybridSearchService(VectorStore vectorStore,
                                ElasticsearchSearchService elasticsearchSearch) {
        this.vectorStore = vectorStore;
        this.elasticsearchSearch = elasticsearchSearch;
    }

    /**
     * Performs hybrid search with reciprocal rank fusion.
     *
     * @param tenantId  tenant for data isolation
     * @param query     user query
     * @param topK      number of results to return
     * @return fused results ordered by combined RRF score
     */
    public List<HybridResult> search(UUID tenantId, String query, int topK) {
        // Fetch more candidates from each source for better fusion
        int candidateK = topK * 3;

        // 1. Vector search (semantic similarity via pgvector)
        List<RankedItem> vectorResults = vectorSearch(tenantId, query, candidateK);

        // 2. BM25 search (full-text via Elasticsearch)
        List<RankedItem> bm25Results = bm25Search(tenantId, query, candidateK);

        // 3. Reciprocal Rank Fusion
        List<HybridResult> fused = reciprocalRankFusion(vectorResults, bm25Results, topK);

        log.debug("Hybrid search for tenant {}: vector={}, bm25={}, fused={}",
            tenantId, vectorResults.size(), bm25Results.size(), fused.size());

        return fused;
    }

    /**
     * Convenience method that returns just the text content.
     */
    public List<String> searchText(UUID tenantId, String query, int topK) {
        return search(tenantId, query, topK).stream()
            .map(HybridResult::content)
            .toList();
    }

    private List<RankedItem> vectorSearch(UUID tenantId, String query, int topK) {
        try {
            SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression("tenant_id == '" + tenantId + "'")
                .build();

            List<Document> results = vectorStore.similaritySearch(request);

            List<RankedItem> ranked = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                Document doc = results.get(i);
                ranked.add(new RankedItem(doc.getId(), doc.getText(), i + 1, "vector"));
            }
            return ranked;
        } catch (Exception e) {
            log.warn("Vector search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<RankedItem> bm25Search(UUID tenantId, String query, int topK) {
        try {
            List<ScoredChunk> results = elasticsearchSearch.search(tenantId, query, topK);

            List<RankedItem> ranked = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                ScoredChunk chunk = results.get(i);
                ranked.add(new RankedItem(chunk.chunkId(), chunk.content(), i + 1, "bm25"));
            }
            return ranked;
        } catch (Exception e) {
            log.warn("BM25 search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Merges two ranked lists using Reciprocal Rank Fusion.
     */
    private List<HybridResult> reciprocalRankFusion(
            List<RankedItem> vectorResults,
            List<RankedItem> bm25Results,
            int topK) {

        // Accumulate RRF scores by document ID
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, String> contentMap = new HashMap<>();
        Map<String, Set<String>> sourceMap = new HashMap<>();

        for (RankedItem item : vectorResults) {
            double rrfScore = 1.0 / (RRF_K + item.rank());
            rrfScores.merge(item.id(), rrfScore, Double::sum);
            contentMap.putIfAbsent(item.id(), item.content());
            sourceMap.computeIfAbsent(item.id(), k -> new LinkedHashSet<>()).add("vector");
        }

        for (RankedItem item : bm25Results) {
            double rrfScore = 1.0 / (RRF_K + item.rank());
            rrfScores.merge(item.id(), rrfScore, Double::sum);
            contentMap.putIfAbsent(item.id(), item.content());
            sourceMap.computeIfAbsent(item.id(), k -> new LinkedHashSet<>()).add("bm25");
        }

        // Sort by RRF score descending and take topK
        return rrfScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(entry -> new HybridResult(
                entry.getKey(),
                contentMap.get(entry.getKey()),
                entry.getValue(),
                sourceMap.get(entry.getKey())
            ))
            .toList();
    }

    private record RankedItem(String id, String content, int rank, String source) {}

    public record HybridResult(String chunkId, String content, double rrfScore, Set<String> sources) {}
}
