package com.betx.adapter.openrouter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.betx.application.MatchIntelligenceDecision;
import com.betx.application.MatchIntelligenceRequest;
import com.betx.domain.config.IntelligenceAutoBettingPolicy;
import com.betx.domain.config.IntelligenceConfig;
import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerAnalysis;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenRouterMatchIntelligenceGatewayTest {
    @Test
    void sendsWebSearchRequestAndParsesStructuredDecision() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterMatchIntelligenceGateway gateway = new OpenRouterMatchIntelligenceGateway(
            builder,
            Map.of("OPENROUTER_API_KEY", "test-key")
        );
        server.expect(requestTo("https://openrouter.ai/api/v1/chat/completions"))
            .andExpect(header("Authorization", "Bearer test-key"))
            .andExpect(jsonPath("$.model").value("x-ai/grok-4.3"))
            .andExpect(jsonPath("$.tools[0].type").value("openrouter:web_search"))
            .andExpect(jsonPath("$.messages[0].content").value(containsString("Return only compact valid JSON.")))
            .andExpect(jsonPath("$.messages[0].content").value(containsString("Be conservative. Automatic betting requires strong evidence.")))
            .andExpect(jsonPath("$.messages[1].content").value(containsString("Automatic execution policy: only APPROVE may place a live order.")))
            .andRespond(withSuccess("""
                {
                  "choices": [{
                    "message": {
                      "content": "{\\"decision\\":\\"APPROVE\\",\\"confidence\\":84,\\"summary\\":\\"No negative team news found.\\",\\"reasons\\":[\\"No major injuries reported\\"],\\"risks\\":[\\"Lineups are not official\\"],\\"sources\\":[{\\"title\\":\\"Team report\\",\\"url\\":\\"https://example.com/report\\",\\"date\\":\\"2026-06-08\\"}]}"
                    }
                  }]
                }
                """, MediaType.APPLICATION_JSON));

        var assessment = gateway.assess(new MatchIntelligenceRequest(
            new IntelligenceConfig(true, "openrouter", "x-ai/grok-4.3", "OPENROUTER_API_KEY", null, 20, 70),
            analysis(),
            true,
            false
        ));

        assertThat(assessment.decision()).isEqualTo(MatchIntelligenceDecision.APPROVE);
        assertThat(assessment.confidence()).isEqualTo(84);
        assertThat(assessment.summary()).isEqualTo("No negative team news found.");
        assertThat(assessment.reasons()).containsExactly("No major injuries reported");
        assertThat(assessment.risks()).containsExactly("Lineups are not official");
        assertThat(assessment.sources()).singleElement()
            .satisfies(source -> {
                assertThat(source.title()).isEqualTo("Team report");
                assertThat(source.url()).isEqualTo("https://example.com/report");
                assertThat(source.date()).isEqualTo("2026-06-08");
            });
        server.verify();
    }

    @Test
    void usesInlineApiKeyWhenConfigured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterMatchIntelligenceGateway gateway = new OpenRouterMatchIntelligenceGateway(
            builder,
            Map.of()
        );
        server.expect(requestTo("https://openrouter.ai/api/v1/chat/completions"))
            .andExpect(header("Authorization", "Bearer inline-key"))
            .andExpect(jsonPath("$.messages[1].content").value(containsString("Automatic execution policy: WATCH may proceed; REJECT and UNAVAILABLE block live orders.")))
            .andRespond(withSuccess("""
                {
                  "choices": [{
                    "message": {
                      "content": "{\\"decision\\":\\"WATCH\\",\\"confidence\\":65,\\"summary\\":\\"Context is unclear.\\",\\"reasons\\":[],\\"risks\\":[],\\"sources\\":[]}"
                    }
                  }]
                }
                """, MediaType.APPLICATION_JSON));

        var assessment = gateway.assess(new MatchIntelligenceRequest(
            new IntelligenceConfig(
                true,
                "openrouter",
                "x-ai/grok-4.3",
                "OPENROUTER_API_KEY",
                "inline-key",
                20,
                70,
                IntelligenceAutoBettingPolicy.BLOCK_ONLY_ON_REJECT
            ),
            analysis(),
            true,
            false
        ));

        assertThat(assessment.decision()).isEqualTo(MatchIntelligenceDecision.WATCH);
        server.verify();
    }

    @Test
    void returnsUnavailableWhenApiKeyIsMissing() {
        OpenRouterMatchIntelligenceGateway gateway = new OpenRouterMatchIntelligenceGateway(
            RestClient.builder(),
            Map.of()
        );

        var assessment = gateway.assess(new MatchIntelligenceRequest(
            new IntelligenceConfig(true, "openrouter", "x-ai/grok-4.3", "OPENROUTER_API_KEY", null, 20, 70),
            analysis(),
            true,
            false
        ));

        assertThat(assessment.decision()).isEqualTo(MatchIntelligenceDecision.UNAVAILABLE);
        assertThat(assessment.summary()).contains("OPENROUTER_API_KEY");
    }

    private RunnerAnalysis analysis() {
        return RunnerAnalysis.from(
            new MarketSnapshot(
                "betfair",
                "1.1",
                "Match Odds",
                "Team A v Team B",
                "La Liga",
                Instant.parse("2026-06-01T18:00:00Z"),
                42L,
                "Team A",
                BigDecimal.valueOf(2.50),
                BigDecimal.valueOf(2.60),
                BigDecimal.valueOf(0.04),
                BigDecimal.valueOf(1_200)
            ),
            RecommendationType.BET,
            "liquidity_ok, spread_ok"
        );
    }
}
