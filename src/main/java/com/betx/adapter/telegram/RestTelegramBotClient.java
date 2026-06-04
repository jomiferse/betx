package com.betx.adapter.telegram;

import com.betx.application.port.out.TelegramBotGateway;
import com.betx.application.port.out.TelegramParseMode;
import com.betx.domain.telegram.TelegramUpdate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RestTelegramBotClient implements TelegramBotGateway {
    private static final String TELEGRAM_API_BASE_URL = "https://api.telegram.org";

    private final RestClient restClient;

    public RestTelegramBotClient(RestClient.Builder builder) {
        this.restClient = builder.baseUrl(TELEGRAM_API_BASE_URL).build();
    }

    @Override
    public String getBotUsername(String token) {
        Map<String, Object> payload = get(token, "getMe", Map.of());
        Object result = unwrap(payload, "Telegram getMe failed.");

        if (result instanceof Map<?, ?> bot
            && bot.get("username") instanceof String username
            && !username.isBlank()) {
            return username;
        }

        throw new IllegalStateException("Telegram bot username could not be resolved.");
    }

    @Override
    public List<TelegramUpdate> getUpdates(String token, Long offset, int timeoutSeconds) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("timeout", timeoutSeconds);

        if (offset != null) {
            params.put("offset", offset);
        }

        Map<String, Object> payload = get(token, "getUpdates", params);
        Object result = unwrap(payload, "Telegram getUpdates failed.");

        if (!(result instanceof List<?> rawUpdates)) {
            throw new IllegalStateException("Telegram getUpdates returned an invalid response.");
        }

        List<TelegramUpdate> updates = new ArrayList<>();

        for (Object rawUpdate : rawUpdates) {
            if (rawUpdate instanceof Map<?, ?> update) {
                parseUpdate(update).ifPresent(updates::add);
            }
        }

        return updates;
    }

    @Override
    public void sendMessage(String token, String chatId, String text) {
        sendMessage(token, chatId, text, null);
    }

    @Override
    public void sendMessage(String token, String chatId, String text, TelegramParseMode parseMode) {
        sendMessage(token, chatId, text, parseMode, null);
    }

    @Override
    public void sendMessage(
        String token,
        String chatId,
        String text,
        TelegramParseMode parseMode,
        Map<String, Object> replyMarkup
    ) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            if (parseMode != null) {
                body.put("parse_mode", parseMode.apiValue());
            }
            if (replyMarkup != null) {
                body.put("reply_markup", replyMarkup);
            }

            Map<?, ?> payload = restClient.post()
                .uri("/bot{token}/sendMessage", token)
                .body(body)
                .retrieve()
                .body(Map.class);

            unwrap(payload, "Telegram sendMessage failed.");
        } catch (RestClientException exc) {
            throw new IllegalStateException("Telegram API request failed.", exc);
        }
    }

    @Override
    public void editMessageText(
        String token,
        String chatId,
        Integer messageId,
        String text,
        TelegramParseMode parseMode,
        Map<String, Object> replyMarkup
    ) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            body.put("text", text);
            if (parseMode != null) {
                body.put("parse_mode", parseMode.apiValue());
            }
            if (replyMarkup != null) {
                body.put("reply_markup", replyMarkup);
            }

            Map<?, ?> payload = restClient.post()
                .uri("/bot{token}/editMessageText", token)
                .body(body)
                .retrieve()
                .body(Map.class);

            unwrap(payload, "Telegram editMessageText failed.");
        } catch (RestClientException exc) {
            throw new IllegalStateException("Telegram API request failed.", exc);
        }
    }

    @Override
    public void answerCallbackQuery(String token, String callbackQueryId, String text, boolean showAlert) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("callback_query_id", callbackQueryId);
            if (text != null && !text.isBlank()) {
                body.put("text", text);
            }
            body.put("show_alert", showAlert);

            Map<?, ?> payload = restClient.post()
                .uri("/bot{token}/answerCallbackQuery", token)
                .body(body)
                .retrieve()
                .body(Map.class);

            unwrap(payload, "Telegram answerCallbackQuery failed.");
        } catch (RestClientException exc) {
            throw new IllegalStateException("Telegram API request failed.", exc);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String token, String method, Map<String, Object> params) {
        try {
            return restClient.get()
                .uri(builder -> {
                    var uri = builder.path("/bot{token}/{method}");
                    params.forEach(uri::queryParam);
                    return uri.build(token, method);
                })
                .retrieve()
                .body(Map.class);
        } catch (RestClientException exc) {
            throw new IllegalStateException("Telegram API request failed.", exc);
        }
    }

    private Object unwrap(Map<?, ?> payload, String fallback) {
        if (payload == null || !Boolean.TRUE.equals(payload.get("ok"))) {
            Object description = payload == null ? null : payload.get("description");
            throw new IllegalStateException(description == null ? fallback : String.valueOf(description));
        }

        return payload.get("result");
    }

    private Optional<TelegramUpdate> parseUpdate(Map<?, ?> update) {
        if (update.get("callback_query") instanceof Map<?, ?> callbackQuery) {
            return parseCallbackQuery(update, callbackQuery);
        }

        Object rawMessage = update.get("message");

        if (!(rawMessage instanceof Map<?, ?> message)) {
            return Optional.empty();
        }

        Object rawChat = message.get("chat");

        if (!(rawChat instanceof Map<?, ?> chat) || chat.get("id") == null) {
            return Optional.empty();
        }

        if (!(update.get("update_id") instanceof Number number)) {
            return Optional.empty();
        }

        Object rawFrom = message.get("from");
        Map<?, ?> from = rawFrom instanceof Map<?, ?> fromMap ? fromMap : Map.of();

        long updateId = number.longValue();
        String text = message.get("text") instanceof String value ? value : null;
        String username = from.get("username") instanceof String value ? value : null;
        String firstName = from.get("first_name") instanceof String value ? value : null;

        return Optional.of(
            new TelegramUpdate(
                updateId,
                String.valueOf(chat.get("id")),
                text,
                username,
                firstName
            )
        );
    }

    private Optional<TelegramUpdate> parseCallbackQuery(Map<?, ?> update, Map<?, ?> callbackQuery) {
        if (!(update.get("update_id") instanceof Number number)) {
            return Optional.empty();
        }

        if (!(callbackQuery.get("id") instanceof String callbackQueryId) || callbackQueryId.isBlank()) {
            return Optional.empty();
        }

        Object rawMessage = callbackQuery.get("message");

        if (!(rawMessage instanceof Map<?, ?> message)) {
            return Optional.empty();
        }

        Object rawChat = message.get("chat");

        if (!(rawChat instanceof Map<?, ?> chat) || chat.get("id") == null) {
            return Optional.empty();
        }

        Object rawFrom = callbackQuery.get("from");
        Map<?, ?> from = rawFrom instanceof Map<?, ?> fromMap ? fromMap : Map.of();

        String callbackData = callbackQuery.get("data") instanceof String value ? value : null;
        Integer messageId = message.get("message_id") instanceof Number value ? Integer.valueOf(value.intValue()) : null;

        return Optional.of(
            new TelegramUpdate(
                number.longValue(),
                String.valueOf(chat.get("id")),
                null,
                from.get("username") instanceof String value ? value : null,
                from.get("first_name") instanceof String value ? value : null,
                callbackQueryId,
                callbackData,
                messageId
            )
        );
    }
}
