package com.example.memory.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Performs BM25 full-text search against Elasticsearch.
 * Returns ranked results with scores for hybrid retrieval fusion.
 */
@Service
public class ElasticsearchSearchService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchSearchService.class);

    private final ElasticsearchClient client;
    private final ElasticsearchIndexService indexService;

    public ElasticsearchSearchService(ElasticsearchClient client,
                                       ElasticsearchIndexService indexService) {
        this.client = client;
        this.indexService = indexService;
    }

    /**
     * BM25 full-text search across content and title fields.
     */
    public List<ScoredChunk> search(UUID tenantId, String query, int topK) {
        String index = indexService.indexName(tenantId);
        List<ScoredChunk> results = new ArrayList<>();

        try {
            SearchResponse<ObjectNode> response = client.search(s -> s
                .index(index)
                .query(q -> q
                    .multiMatch(mm -> mm
                        .query(query)
                        .fields("content^1.0", "title^1.5")
                        .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                        .fuzziness("AUTO")
                    )
                )
                .size(topK),
                ObjectNode.class
            );

            for (Hit<ObjectNode> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    String content = hit.source().has("content")
                        ? hit.source().get("content").asText() : "";
                    String chunkId = hit.id();
                    double score = hit.score() != null ? hit.score() : 0.0;
                    results.add(new ScoredChunk(chunkId, content, score));
                }
            }
        } catch (IOException e) {
            log.warn("Elasticsearch search failed for tenant {}: {}", tenantId, e.getMessage());
        }

        return results;
    }

    public record ScoredChunk(String chunkId, String content, double score) {}
}
