package com.betx.adapter.openrouter;

import com.betx.application.MatchIntelligenceAssessment;
import com.betx.application.MatchIntelligenceDecision;
import com.betx.application.MatchIntelligenceRequest;
import com.betx.application.MatchIntelligenceSource;
import com.betx.application.port.out.ExternalMatchIntelligenceGateway;
import com.betx.domain.signal.RunnerAnalysis;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OpenRouterMatchIntelligenceGateway implements ExternalMatchIntelligenceGateway {
    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";

    private final RestClient.Builder builder;
    private final Map<String, String> environment;
    private final ObjectMapper mapper;

    @Autowired
    public OpenRouterMatchIntelligenceGateway(RestClient.Builder builder) {
        this(builder, System.getenv());
    }

    OpenRouterMatchIntelligenceGateway(RestClient.Builder builder, Map<String, String> environment) {
        this.builder = builder;
        this.environment = environment;
        this.mapper = new ObjectMapper();
    }

    @Override
    public MatchIntelligenceAssessment assess(MatchIntelligenceRequest request) {
        String apiKey = apiKey(request);
        RunnerAnalysis analysis = request.analysis();
        if (apiKey == null || apiKey.isBlank()) {
            return MatchIntelligenceAssessment.unavailable(
                analysis.exchange(),
                analysis.marketId(),
                analysis.selectionId(),
                "OpenRouter API key is not configured in intelligence.api_key or " + request.config().apiKeyEnv() + "."
            );
        }

        try {
            JsonNode response = builder
                .clone()
                .build()
                .post()
                .uri(OPENROUTER_URL)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody(request))
                .retrieve()
                .body(JsonNode.class);
            return parseResponse(request, response);
        } catch (RestClientException | IllegalArgumentException exc) {
            return MatchIntelligenceAssessment.unavailable(
                analysis.exchange(),
                analysis.marketId(),
                analysis.selectionId(),
                "OpenRouter request failed: " + exc.getMessage()
            );
        }
    }

    private String apiKey(MatchIntelligenceRequest request) {
        if (request.config().apiKey() != null && !request.config().apiKey().isBlank()) {
            return request.config().apiKey();
        }
        return environment.get(request.config().apiKeyEnv());
    }

    private Map<String, Object> requestBody(MatchIntelligenceRequest request) {
        return Map.of(
            "model", request.config().model(),
            "temperature", 0.1,
            "max_tokens", 500,
            "messages", List.of(
                Map.of(
                    "role", "system",
                    "content", """
                        You are BetX match intelligence.

                        Your job is to validate a betting signal using current, reliable web information.
                        You must decide whether the external context supports, weakens, or is insufficient to validate the signal.

                        Return only compact valid JSON.
                        Do not include markdown.
                        Do not include explanations outside JSON.

                        Required JSON keys:
                        {
                          "decision": "APPROVE" | "REJECT" | "WATCH",
                          "confidence": 0-100,
                          "summary": "short explanation",
                          "reasons": ["reason 1", "reason 2", "reason 3"],
                          "risks": ["risk 1", "risk 2"],
                          "sources": [
                            {
                              "title": "source title",
                              "url": "source url",
                              "date": "YYYY-MM-DD or null"
                            }
                          ]
                        }

                        Decision rules:
                        - APPROVE only if current external context clearly supports the signal, no major contradictory news is found, and the bet appears to have positive expected value.
                        - REJECT if current external context contradicts the signal, materially weakens it, indicates missing key information, or suggests the price is poor.
                        - WATCH if the context is mixed, unclear, stale, insufficient, or the edge is too narrow.

                        Important:
                        - Never approve only because the team/player/selection is plausible.
                        - Prefer WATCH when evidence is incomplete.
                        - Treat missing injury/team news, stale previews, unavailable lineups, or uncertain motivation as reasons for WATCH.
                        - If sources disagree, choose WATCH unless the signal is clearly weakened, then REJECT.
                        - Be conservative. Automatic betting requires strong evidence.
                        """
                ),
                Map.of(
                    "role", "user",
                    "content", prompt(request)
                )
            ),
            "tools", List.of(Map.of(
                "type", "openrouter:web_search",
                "parameters", Map.of(
                    "engine", "auto",
                    "max_results", 5,
                    "max_total_results", 8,
                    "search_context_size", "medium"
                )
            ))
        );
    }

    private String prompt(MatchIntelligenceRequest request) {
        RunnerAnalysis analysis = request.analysis();
        return "Analyze current news and real-time context for this football betting signal.\n"
            + "Event: " + nullSafe(analysis.eventName()) + "\n"
            + "Competition: " + nullSafe(analysis.competitionName()) + "\n"
            + "Market: " + nullSafe(analysis.marketName()) + "\n"
            + "Selection: " + nullSafe(analysis.displayRunner()) + "\n"
            + "Kickoff: " + analysis.marketStartTime() + "\n"
            + "Exchange: " + analysis.exchange() + "\n"
            + "Back odds: " + analysis.bestBackPrice() + "\n"
            + "Lay odds: " + analysis.bestLayPrice() + "\n"
            + "Liquidity: " + analysis.liquidity() + "\n"
            + "Technical score: " + analysis.score().value() + "/100\n"
            + "Technical reasons: " + String.join("; ", analysis.score().reasons()) + "\n"
            + "Auto-betting enabled: " + request.autoBettingEnabled() + "\n"
            + "Telegram confirmation required: " + request.requestConfirmation() + "\n"
            + "Automatic execution policy: " + automaticExecutionPolicy(request) + "\n";
    }

    private String automaticExecutionPolicy(MatchIntelligenceRequest request) {
        if (!request.autoBettingEnabled()) {
            return "no live order can be placed because auto-betting is disabled.";
        }
        if (request.requestConfirmation()) {
            return "Telegram confirmation is required before any live order.";
        }
        return switch (request.config().autoBettingPolicy()) {
            case STRICT_APPROVE -> "only APPROVE may place a live order.";
            case BLOCK_ONLY_ON_REJECT -> "WATCH may proceed; REJECT and UNAVAILABLE block live orders.";
        };
    }

    private MatchIntelligenceAssessment parseResponse(MatchIntelligenceRequest request, JsonNode response) {
        RunnerAnalysis analysis = request.analysis();
        String content = response.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            return MatchIntelligenceAssessment.unavailable(
                analysis.exchange(),
                analysis.marketId(),
                analysis.selectionId(),
                "OpenRouter returned an empty intelligence response."
            );
        }
        try {
            JsonNode parsed = mapper.readTree(extractJson(content));
            MatchIntelligenceDecision decision = MatchIntelligenceDecision.valueOf(parsed.path("decision").asText("UNAVAILABLE"));
            int confidence = parsed.path("confidence").asInt(0);
            if (confidence < request.config().minConfidence() && decision == MatchIntelligenceDecision.APPROVE) {
                decision = MatchIntelligenceDecision.WATCH;
            }
            return new MatchIntelligenceAssessment(
                analysis.exchange(),
                analysis.marketId(),
                analysis.selectionId(),
                decision,
                confidence,
                parsed.path("summary").asText("No external intelligence summary available."),
                strings(parsed.path("reasons")),
                strings(parsed.path("risks")),
                sources(parsed.path("sources"))
            );
        } catch (RuntimeException | java.io.IOException exc) {
            return MatchIntelligenceAssessment.unavailable(
                analysis.exchange(),
                analysis.marketId(),
                analysis.selectionId(),
                "OpenRouter returned invalid intelligence JSON."
            );
        }
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    private List<String> strings(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
            .map(JsonNode::asText)
            .filter(value -> value != null && !value.isBlank())
            .limit(5)
            .toList();
    }

    private List<MatchIntelligenceSource> sources(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
            .map(this::source)
            .filter(source -> source.url() != null || source.title() != null)
            .limit(5)
            .toList();
    }

    private MatchIntelligenceSource source(JsonNode node) {
        if (node.isTextual()) {
            return MatchIntelligenceSource.fromUrl(node.asText());
        }
        return new MatchIntelligenceSource(
            node.path("title").asText(null),
            node.path("url").asText(null),
            node.path("date").isNull() ? null : node.path("date").asText(null)
        );
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
