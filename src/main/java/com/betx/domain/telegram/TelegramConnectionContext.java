package com.betx.domain.telegram;

/** Resolved Telegram credentials for an already connected bot. */
public record TelegramConnectionContext(String token, String chatId) {
    public TelegramConnectionContext {
        token = token == null ? null : token.strip();
        chatId = chatId == null ? null : chatId.strip();
    }
}
