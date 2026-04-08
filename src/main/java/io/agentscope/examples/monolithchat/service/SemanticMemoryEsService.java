package io.agentscope.examples.monolithchat.service;

import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SemanticMemoryEsService {

    private static final String INDEX = "user_semantic_memory_v1";

    private final RestHighLevelClient client;

    public SemanticMemoryEsService(RestHighLevelClient client) {
        this.client = client;
    }

    @PostConstruct
    public void ensureIndex() {
        try {
            GetIndexRequest getIndexRequest = new GetIndexRequest().indices(INDEX);
            if (client.indices().exists(getIndexRequest, RequestOptions.DEFAULT)) {
                return;
            }
            CreateIndexRequest request = new CreateIndexRequest(INDEX);
            client.indices().create(request, RequestOptions.DEFAULT);
        } catch (Exception ignored) {
        }
    }

    public void save(String userId, String sessionId, String memoryType, String text, double importance, String triggerMessageId) {
        if (userId == null || userId.isBlank() || text == null || text.isBlank()) {
            return;
        }
        try {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("user_id", userId.strip());
            doc.put("session_id", sessionId == null ? "" : sessionId.strip());
            doc.put("memory_type", memoryType == null ? "dialogue" : memoryType.strip());
            doc.put("memory_text", text.strip());
            doc.put("importance", importance);
            doc.put("trigger_message_id", triggerMessageId == null ? "" : triggerMessageId.strip());
            doc.put("created_at", Instant.now().toString());
            IndexRequest request = new IndexRequest(INDEX)
                    .id(UUID.randomUUID().toString())
                    .source(doc);
            client.index(request, RequestOptions.DEFAULT);
        } catch (Exception ignored) {
        }
    }

    public List<String> search(String userId, String query, int limit) {
        if (userId == null || userId.isBlank() || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                    .filter(QueryBuilders.termQuery("user_id", userId.strip()))
                    .must(QueryBuilders.matchQuery("memory_text", query.strip()));
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                    .query(boolQuery)
                    .size(Math.max(1, limit))
                    .fetchSource(new String[]{"memory_text"}, null);
            SearchRequest request = new SearchRequest(INDEX).source(sourceBuilder);
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            List<String> lines = new ArrayList<>();
            for (SearchHit hit : response.getHits()) {
                Object value = hit.getSourceAsMap().get("memory_text");
                if (value != null) {
                    lines.add(String.valueOf(value));
                }
            }
            return lines;
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
