package com.betx.adapter.betfair;

import com.betx.application.port.out.BetfairGateway;
import com.betx.domain.betfair.BetfairCredentials;
import com.betx.domain.betfair.BetfairEvent;
import com.betx.domain.betfair.BetfairMarketBook;
import com.betx.domain.betfair.BetfairMarketCatalogue;
import com.betx.domain.betfair.BetfairMarketQuery;
import com.betx.domain.betfair.BetfairRunnerPrice;
import com.betx.domain.betfair.BetfairSession;
import com.betx.domain.order.BetExecutionResult;
import com.betx.domain.order.BetOrder;
import com.betx.domain.signal.BetSide;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class BetfairRestGateway implements BetfairGateway {
    private static final String GLOBAL_BETTING_URL = "https://api.betfair.com/exchange/betting/json-rpc/v1";

    private final RestClient loginClient;
    private final RestClient bettingClient;
    private final ObjectMapper mapper;

    @Autowired
    public BetfairRestGateway(RestClient.Builder builder) {
        this(builder, new ObjectMapper().findAndRegisterModules());
    }

    BetfairRestGateway(RestClient.Builder builder, ObjectMapper mapper) {
        this.loginClient = builder.build();
        this.bettingClient = builder.build();
        this.mapper = mapper;
    }

    @Override
    public BetfairSession login(BetfairCredentials credentials) {
        try {
            JsonNode payload = loginClient.post()
                .uri(URI.create(credentials.country().loginUrl()))
                .headers(headers -> {
                    headers.set("Accept", "application/json");
                    headers.set("X-Application", credentials.appKey());
                    headers.set("Content-Type", "application/x-www-form-urlencoded");
                })
                .body("username=" + encode(credentials.username()) + "&password=" + encode(credentials.password()))
                .retrieve()
                .body(JsonNode.class);

            if (payload == null) {
                throw new IllegalStateException("Betfair login returned no response.");
            }

            String status = text(payload, "status");
            String token = firstNonBlank(payload, "token", "sessionToken");
            String product = firstNonBlank(payload, "product", "appKey");

            if ("SUCCESS".equals(status) || "LIMITED_ACCESS".equals(status)) {
                return new BetfairSession(token, product.isBlank() ? credentials.appKey() : product);
            }

            String error = text(payload, "error");
            throw new IllegalStateException("Betfair login failed: " + (error.isBlank() ? status : error));
        } catch (RestClientException exc) {
            throw new IllegalStateException("Betfair login request failed.", exc);
        }
    }

    @Override
    public List<BetfairEvent> listEvents(BetfairSession session, List<String> eventTypeIds, List<String> marketTypeCodes) {
        ObjectNode params = mapper.createObjectNode();
        ObjectNode filter = params.putObject("filter");
        if (eventTypeIds != null && !eventTypeIds.isEmpty()) {
            ArrayNode eventTypeIdsNode = filter.putArray("eventTypeIds");
            eventTypeIds.forEach(eventTypeIdsNode::add);
        }
        if (marketTypeCodes != null && !marketTypeCodes.isEmpty()) {
            ArrayNode marketTypeCodesNode = filter.putArray("marketTypeCodes");
            marketTypeCodes.forEach(marketTypeCodesNode::add);
        }
        params.put("locale", "en");

        JsonNode result = invoke(session, "SportsAPING/v1.0/listEvents", params);
        List<BetfairEvent> events = new ArrayList<>();
        if (result != null && result.isArray()) {
            for (JsonNode item : result) {
                JsonNode event = item.path("event");
                events.add(new BetfairEvent(
                    text(event, "id"),
                    text(event, "name"),
                    text(event, "countryCode"),
                    text(event, "timezone"),
                    parseInstant(event, "openDate"),
                    item.path("marketCount").asInt(0)
                ));
            }
        }
        return events;
    }

    @Override
    public List<BetfairMarketCatalogue> listMarketCatalogue(BetfairSession session, BetfairMarketQuery query) {
        ObjectNode params = mapper.createObjectNode();
        ObjectNode filter = params.putObject("filter");
        if (!query.eventTypeIds().isEmpty()) {
            ArrayNode eventTypeIds = filter.putArray("eventTypeIds");
            query.eventTypeIds().forEach(eventTypeIds::add);
        }
        if (!query.eventIds().isEmpty()) {
            ArrayNode eventIds = filter.putArray("eventIds");
            query.eventIds().forEach(eventIds::add);
        }
        if (!query.marketTypeCodes().isEmpty()) {
            ArrayNode marketTypeCodes = filter.putArray("marketTypeCodes");
            query.marketTypeCodes().forEach(marketTypeCodes::add);
        }
        if (query.marketStartTimeFrom() != null || query.marketStartTimeTo() != null) {
            ObjectNode marketStartTime = filter.putObject("marketStartTime");
            if (query.marketStartTimeFrom() != null) {
                marketStartTime.put("from", query.marketStartTimeFrom().toString());
            }
            if (query.marketStartTimeTo() != null) {
                marketStartTime.put("to", query.marketStartTimeTo().toString());
            }
        }
        ArrayNode marketProjection = params.putArray("marketProjection");
        marketProjection.add("EVENT");
        marketProjection.add("COMPETITION");
        marketProjection.add("RUNNER_DESCRIPTION");
        marketProjection.add("MARKET_START_TIME");
        params.put("sort", "FIRST_TO_START");
        params.put("maxResults", query.maxResults());
        params.put("locale", "en");

        JsonNode result = invoke(session, "SportsAPING/v1.0/listMarketCatalogue", params);
        List<BetfairMarketCatalogue> markets = new ArrayList<>();
        if (result != null && result.isArray()) {
            for (JsonNode item : result) {
                markets.add(new BetfairMarketCatalogue(
                    text(item, "marketId"),
                    text(item, "marketName"),
                    text(item.path("event"), "name"),
                    text(item.path("competition"), "name"),
                    parseInstant(item, "marketStartTime"),
                    parseRunnerNames(item.path("runners"))
                ));
            }
        }
        return markets;
    }

    @Override
    public List<BetfairMarketBook> listMarketBook(BetfairSession session, List<String> marketIds) {
        ObjectNode params = mapper.createObjectNode();
        ArrayNode ids = params.putArray("marketIds");
        marketIds.forEach(ids::add);
        ObjectNode priceProjection = params.putObject("priceProjection");
        ArrayNode priceData = priceProjection.putArray("priceData");
        priceData.add("EX_BEST_OFFERS");
        priceProjection.put("virtualise", true);
        priceProjection.set("exBestOffersOverrides", mapper.createObjectNode().put("bestPricesDepth", 3));
        params.put("locale", "en");

        JsonNode result = invoke(session, "SportsAPING/v1.0/listMarketBook", params);
        List<BetfairMarketBook> books = new ArrayList<>();
        if (result != null && result.isArray()) {
            for (JsonNode item : result) {
                books.add(new BetfairMarketBook(
                    text(item, "marketId"),
                    text(item, "status"),
                    item.path("inplay").asBoolean(false),
                    decimal(item, "totalMatched"),
                    parseRunners(item.path("runners"))
                ));
            }
        }
        return books;
    }

    @Override
    public BigDecimal getAccountFunds(BetfairSession session) {
        JsonNode result = invoke(session, "SportsAPING/v1.0/getAccountFunds", mapper.createObjectNode());
        if (result == null || result.isMissingNode()) {
            return null;
        }
        JsonNode balance = result.path("availableToBetBalance");
        if (balance.isMissingNode() || balance.isNull()) {
            balance = result.path("availableToBettingBalance");
        }
        return balance.isMissingNode() || balance.isNull() ? null : balance.decimalValue();
    }

    @Override
    public BetExecutionResult placeOrder(BetfairSession session, BetOrder order) {
        ObjectNode params = mapper.createObjectNode();
        params.put("marketId", order.marketId());
        ArrayNode instructions = params.putArray("instructions");
        ObjectNode instruction = instructions.addObject();
        instruction.put("selectionId", order.selectionId());
        instruction.put("side", order.side() == BetSide.BACK ? "BACK" : order.side().name());
        instruction.put("orderType", "LIMIT");
        ObjectNode limitOrder = instruction.putObject("limitOrder");
        limitOrder.put("size", order.stake().setScale(2, RoundingMode.HALF_UP).toPlainString());
        limitOrder.put("price", order.odds().setScale(2, RoundingMode.HALF_UP).toPlainString());
        limitOrder.put("persistenceType", "LAPSE");

        JsonNode result = invoke(session, "SportsAPING/v1.0/placeOrders", params);
        if (result == null || result.isMissingNode()) {
            return BetExecutionResult.rejected("Betfair placeOrders returned no result.");
        }

        String status = text(result, "status");
        JsonNode reports = result.path("instructionReports");
        if ("SUCCESS".equalsIgnoreCase(status) && reports.isArray() && !reports.isEmpty()) {
            JsonNode report = reports.get(0);
            String reportStatus = text(report, "status");
            if ("SUCCESS".equalsIgnoreCase(reportStatus)) {
                return new BetExecutionResult(true, text(report, "betId").isBlank() ? "Bet placed." : "Bet placed. BetId=" + text(report, "betId"));
            }
            return BetExecutionResult.rejected(text(report, "errorCode").isBlank() ? "Betfair rejected the order." : text(report, "errorCode"));
        }

        return BetExecutionResult.rejected(text(result, "errorCode").isBlank() ? "Betfair rejected the order." : text(result, "errorCode"));
    }

    private Map<Long, String> parseRunnerNames(JsonNode runnersNode) {
        Map<Long, String> names = new LinkedHashMap<>();
        if (runnersNode == null || !runnersNode.isArray()) {
            return names;
        }
        for (JsonNode runner : runnersNode) {
            long selectionId = runner.path("selectionId").asLong();
            String runnerName = text(runner, "runnerName");
            if (selectionId > 0 && !runnerName.isBlank()) {
                names.put(selectionId, runnerName);
            }
        }
        return names;
    }

    private JsonNode invoke(BetfairSession session, String method, JsonNode params) {
        try {
            ObjectNode request = mapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("method", method);
            request.set("params", params);
            request.put("id", 1);
            JsonNode payload = bettingClient.post()
                .uri(URI.create(GLOBAL_BETTING_URL))
                .headers(headers -> {
                    headers.set("Accept", "application/json");
                    headers.set("X-Application", session.appKey());
                    headers.set("X-Authentication", session.token());
                    headers.set("Content-Type", "application/json");
                })
                .body(request)
                .retrieve()
                .body(JsonNode.class);

            if (payload == null) {
                throw new IllegalStateException("Betfair request returned no response.");
            }
            if (payload.hasNonNull("error")) {
                throw new IllegalStateException("Betfair request failed: " + payload.path("error").toString());
            }
            return payload.path("result");
        } catch (RestClientException exc) {
            throw new IllegalStateException("Betfair API request failed.", exc);
        }
    }

    private List<BetfairRunnerPrice> parseRunners(JsonNode runnersNode) {
        List<BetfairRunnerPrice> runners = new ArrayList<>();
        if (runnersNode == null || !runnersNode.isArray()) {
            return runners;
        }
        for (JsonNode runner : runnersNode) {
            JsonNode ex = runner.path("ex");
            runners.add(new BetfairRunnerPrice(
                runner.path("selectionId").asLong(),
                decimal(runner, "lastPriceTraded"),
                bestPrice(ex.path("availableToBack")),
                bestPrice(ex.path("availableToLay")),
                decimal(runner, "totalMatched")
            ));
        }
        return runners;
    }

    private BigDecimal bestPrice(JsonNode prices) {
        if (prices == null || !prices.isArray() || prices.isEmpty()) {
            return null;
        }
        return decimal(prices.get(0), "price");
    }

    private String text(JsonNode node, String field) {
        return node == null || node.isMissingNode() ? "" : node.path(field).asText("");
    }

    private String firstNonBlank(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private Instant parseInstant(JsonNode node, String field) {
        String raw = text(node, field);
        return raw.isBlank() ? null : Instant.parse(raw);
    }

    private BigDecimal decimal(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return null;
        }
        return node.path(field).decimalValue();
    }

    private String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exc) {
            throw new IllegalStateException("Could not encode Betfair credentials.", exc);
        }
    }
}
