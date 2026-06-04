package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramConfig(
    Boolean enabled,
    @JsonProperty("bot_token") String botToken,
    @JsonProperty("bot_token_env") String botTokenEnv,
    @JsonProperty("chat_id_env") String chatIdEnv,
    @JsonProperty("bot_username") String botUsername,
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("connected_at") String connectedAt,
    String username,
    @JsonProperty("first_name") String firstName,
    @JsonProperty("pending_link_code") String pendingLinkCode
) {
    public TelegramConfig {
        enabled = enabled == null || enabled;
        botTokenEnv = blankToDefault(botTokenEnv, "TELEGRAM_BOT_TOKEN");
        chatIdEnv = blankToDefault(chatIdEnv, "TELEGRAM_CHAT_ID");
        botToken = blankToNull(botToken);
        botUsername = blankToNull(botUsername);
        chatId = blankToNull(chatId);
        connectedAt = blankToNull(connectedAt);
        username = blankToNull(username);
        firstName = blankToNull(firstName);
        pendingLinkCode = blankToNull(pendingLinkCode);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
