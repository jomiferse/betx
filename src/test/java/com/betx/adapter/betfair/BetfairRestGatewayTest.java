package com.betx.adapter.betfair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.betx.domain.betfair.BetfairCountry;
import com.betx.domain.betfair.BetfairCredentials;
import com.betx.domain.betfair.BetfairEvent;
import com.betx.domain.betfair.BetfairMarketQuery;
import com.betx.domain.betfair.BetfairSession;
import com.betx.domain.order.BetExecutionResult;
import com.betx.domain.order.BetOrder;
import com.betx.domain.signal.BetSide;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BetfairRestGatewayTest {
    @Test
    void logsInAgainstTheSelectedCountryEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BetfairRestGateway gateway = new BetfairRestGateway(builder, new ObjectMapper().findAndRegisterModules());
        server.expect(requestTo("https://identitysso.betfair.it/api/login"))
            .andRespond(withSuccess("{\"status\":\"SUCCESS\",\"token\":\"session-token\"}", APPLICATION_JSON));

        gateway.login(new BetfairCredentials("user", "password", "app-key", BetfairCountry.ITALY));

        server.verify();
    }

    @Test
    void failsLoginWithBetfairErrorMessage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BetfairRestGateway gateway = new BetfairRestGateway(builder, new ObjectMapper().findAndRegisterModules());
        server.expect(requestTo("https://identitysso.betfair.it/api/login"))
            .andRespond(withSuccess("{\"status\":\"FAIL\",\"error\":\"INVALID_USERNAME_OR_PASSWORD\"}", APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.login(new BetfairCredentials("user", "password", "app-key", BetfairCountry.ITALY)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Betfair login failed: INVALID_USERNAME_OR_PASSWORD");
        server.verify();
    }

    @Test
    void listsMarketCatalogueFromJsonRpcResult() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BetfairRestGateway gateway = new BetfairRestGateway(builder, new ObjectMapper().findAndRegisterModules());
        server.expect(requestTo("https://api.betfair.com/exchange/betting/json-rpc/v1"))
            .andRespond(withSuccess("""
                {
                  "jsonrpc": "2.0",
                  "result": [
                    {
                      "marketId": "1.234",
                      "marketName": "Match Odds",
                      "event": {"name": "Team A v Team B"},
                      "competition": {"name": "La Liga"},
                      "marketStartTime": "2026-06-01T18:00:00Z",
                      "runners": [
                        {"selectionId": 42, "runnerName": "Team A"},
                        {"selectionId": 43, "runnerName": "The Draw"}
                      ]
                    }
                  ],
                  "id": 1
                }
                """, APPLICATION_JSON));

        var catalogues = gateway.listMarketCatalogue(new BetfairSession("session-token", "app-key"), new BetfairMarketQuery(List.of("1"), List.of("MATCH_ODDS"), 5));

        assertThat(catalogues).singleElement().satisfies(catalogue -> {
            assertThat(catalogue.marketId()).isEqualTo("1.234");
            assertThat(catalogue.marketName()).isEqualTo("Match Odds");
            assertThat(catalogue.eventName()).isEqualTo("Team A v Team B");
            assertThat(catalogue.competitionName()).isEqualTo("La Liga");
            assertThat(catalogue.marketStartTime()).isEqualTo("2026-06-01T18:00:00Z");
            assertThat(catalogue.runnerName(42L)).contains("Team A");
            assertThat(catalogue.runnerName(43L)).contains("The Draw");
        });
        server.verify();
    }

    @Test
    void sendsMarketTypeCodesWhenListingMarketCatalogue() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BetfairRestGateway gateway = new BetfairRestGateway(builder, new ObjectMapper().findAndRegisterModules());
        server.expect(requestTo("https://api.betfair.com/exchange/betting/json-rpc/v1"))
            .andExpect(content().json("""
                {
                  "method": "SportsAPING/v1.0/listMarketCatalogue",
                  "params": {
                    "filter": {
                      "eventTypeIds": ["1"],
                      "marketTypeCodes": ["MATCH_ODDS"]
                    },
                    "maxResults": 5
                  }
                }
                """))
            .andRespond(withSuccess("{\"jsonrpc\":\"2.0\",\"result\":[],\"id\":1}", APPLICATION_JSON));

        gateway.listMarketCatalogue(new BetfairSession("session-token", "app-key"), new BetfairMarketQuery(List.of("1"), List.of("MATCH_ODDS"), 5));

        server.verify();
    }

    @Test
    void listsEventsFromJsonRpcResult() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BetfairRestGateway gateway = new BetfairRestGateway(builder, new ObjectMapper().findAndRegisterModules());
        server.expect(requestTo("https://api.betfair.com/exchange/betting/json-rpc/v1"))
            .andExpect(content().json("""
                {
                  "method": "SportsAPING/v1.0/listEvents",
                  "params": {
                    "filter": {
                      "eventTypeIds": ["1"],
                      "marketTypeCodes": ["MATCH_ODDS"]
                    },
                    "locale": "en"
                  }
                }
                """))
            .andRespond(withSuccess("""
                {
                  "jsonrpc": "2.0",
                  "result": [
                    {
                      "event": {
                        "id": "35660432",
                        "name": "Turkiye v North Macedonia",
                        "countryCode": "TR",
                        "timezone": "GMT",
                        "openDate": "2026-06-01T17:00:00Z"
                      },
                      "marketCount": 14
                    }
                  ],
                  "id": 1
                }
                """, APPLICATION_JSON));

        List<BetfairEvent> events = gateway.listEvents(new BetfairSession("session-token", "app-key"), List.of("1"), List.of("MATCH_ODDS"));

        assertThat(events).containsExactly(new BetfairEvent(
            "35660432",
            "Turkiye v North Macedonia",
            "TR",
            "GMT",
            java.time.Instant.parse("2026-06-01T17:00:00Z"),
            14
        ));
        server.verify();
    }

    @Test
    void sendsEventIdsWhenListingMarketCatalogue() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BetfairRestGateway gateway = new BetfairRestGateway(builder, new ObjectMapper().findAndRegisterModules());
        server.expect(requestTo("https://api.betfair.com/exchange/betting/json-rpc/v1"))
            .andExpect(content().json("""
                {
                  "method": "SportsAPING/v1.0/listMarketCatalogue",
                  "params": {
                    "filter": {
                      "eventTypeIds": ["1"],
                      "eventIds": ["35660432", "35654765"],
                      "marketTypeCodes": ["MATCH_ODDS"]
                    },
                    "maxResults": 1000
                  }
                }
                """))
            .andRespond(withSuccess("{\"jsonrpc\":\"2.0\",\"result\":[],\"id\":1}", APPLICATION_JSON));

        gateway.listMarketCatalogue(
            new BetfairSession("session-token", "app-key"),
            new BetfairMarketQuery(List.of("1"), List.of("35660432", "35654765"), List.of("MATCH_ODDS"), 1000)
        );

        server.verify();
    }

    @Test
    void sendsMarketStartTimeRangeWhenListingMarketCatalogue() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BetfairRestGateway gateway = new BetfairRestGateway(builder, new ObjectMapper().findAndRegisterModules());
        server.expect(requestTo("https://api.betfair.com/exchange/betting/json-rpc/v1"))
            .andExpect(content().json("""
                {
                  "method": "SportsAPING/v1.0/listMarketCatalogue",
                  "params": {
                    "filter": {
                      "eventTypeIds": ["1"],
                      "marketTypeCodes": ["MATCH_ODDS"],
                      "marketStartTime": {
                        "from": "2026-06-01T00:00:00Z",
                        "to": "2026-06-01T06:00:00Z"
                      }
                    },
                    "maxResults": 1000
                  }
                }
                """))
            .andRespond(withSuccess("{\"jsonrpc\":\"2.0\",\"result\":[],\"id\":1}", APPLICATION_JSON));

        gateway.listMarketCatalogue(
            new BetfairSession("session-token", "app-key"),
            new BetfairMarketQuery(
                List.of("1"),
                List.of(),
                List.of("MATCH_ODDS"),
                1000,
                java.time.Instant.parse("2026-06-01T00:00:00Z"),
                java.time.Instant.parse("2026-06-01T06:00:00Z")
            )
        );

        server.verify();
    }

    @Test
    void listsMarketBooksFromJsonRpcResult() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BetfairRestGateway gateway = new BetfairRestGateway(builder, new ObjectMapper().findAndRegisterModules());
        server.expect(requestTo("https://api.betfair.com/exchange/betting/json-rpc/v1"))
            .andRespond(withSuccess("""
                {
                  "jsonrpc": "2.0",
                  "result": [
                    {
                      "marketId": "1.234",
                      "status": "OPEN",
                      "inplay": false,
                      "totalMatched": 1500,
                      "runners": [
                        {
                          "selectionId": 42,
                          "lastPriceTraded": 2.55,
                          "totalMatched": 300,
                          "ex": {
                            "availableToBack": [{"price": 2.5}],
                            "availableToLay": [{"price": 2.6}]
                          }
                        }
                      ]
                    }
                  ],
                  "id": 1
                }
                """, APPLICATION_JSON));

        var books = gateway.listMarketBook(new BetfairSession("session-token", "app-key"), List.of("1.234"));

        assertThat(books).singleElement().satisfies(book -> {
            assertThat(book.marketId()).isEqualTo("1.234");
            assertThat(book.totalMatched()).isEqualByComparingTo("1500");
            assertThat(book.runners()).singleElement().satisfies(runner -> {
                assertThat(runner.selectionId()).isEqualTo(42L);
                assertThat(runner.bestBackPrice()).isEqualByComparingTo("2.5");
                assertThat(runner.bestLayPrice()).isEqualByComparingTo("2.6");
            });
        });
        server.verify();
    }

    @Test
    void readsAvailableBalanceFromGetAccountFunds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BetfairRestGateway gateway = new BetfairRestGateway(builder, new ObjectMapper().findAndRegisterModules());
        server.expect(requestTo("https://api.betfair.com/exchange/betting/json-rpc/v1"))
            .andRespond(withSuccess("""
                {
                  "jsonrpc": "2.0",
                  "result": {
                    "availableToBetBalance": 42.75
                  },
                  "id": 1
                }
                """, APPLICATION_JSON));

        assertThat(gateway.getAccountFunds(new BetfairSession("session-token", "app-key"))).isEqualByComparingTo("42.75");
        server.verify();
    }

    @Test
    void placesLimitOrderFromBetOrder() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BetfairRestGateway gateway = new BetfairRestGateway(builder, new ObjectMapper().findAndRegisterModules());
        server.expect(requestTo("https://api.betfair.com/exchange/betting/json-rpc/v1"))
            .andExpect(content().json("""
                {
                  "method": "SportsAPING/v1.0/placeOrders",
                  "params": {
                    "marketId": "1.234",
                    "instructions": [
                      {
                        "selectionId": 42,
                        "side": "BACK",
                        "orderType": "LIMIT",
                        "limitOrder": {
                          "size": "5.00",
                          "price": "2.50",
                          "persistenceType": "LAPSE"
                        }
                      }
                    ]
                  }
                }
                """))
            .andRespond(withSuccess("""
                {
                  "jsonrpc": "2.0",
                  "result": {
                    "status": "SUCCESS",
                    "instructionReports": [
                      {
                        "status": "SUCCESS",
                        "betId": "bet-1"
                      }
                    ]
                  },
                  "id": 1
                }
                """, APPLICATION_JSON));

        BetExecutionResult result = gateway.placeOrder(
            new BetfairSession("session-token", "app-key"),
            new BetOrder("betfair", "1.234", 42L, BetSide.BACK, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5))
        );

        assertThat(result.accepted()).isTrue();
        assertThat(result.message()).contains("bet-1");
        server.verify();
    }

    @Test
    void failsWhenJsonRpcReturnsError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BetfairRestGateway gateway = new BetfairRestGateway(builder, new ObjectMapper().findAndRegisterModules());
        server.expect(requestTo("https://api.betfair.com/exchange/betting/json-rpc/v1"))
            .andRespond(withSuccess("{\"jsonrpc\":\"2.0\",\"error\":{\"message\":\"bad request\"},\"id\":1}", APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.listMarketBook(new BetfairSession("session-token", "app-key"), List.of("1.234")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Betfair request failed:");
        server.verify();
    }
}
