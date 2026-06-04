package com.betx.domain.telegram;

public record TelegramUpdate(
    long updateId,
    String chatId,
    String text,
    String username,
    String firstName,
    String callbackQueryId,
    String callbackData,
    Integer messageId
) {
    public TelegramUpdate(
        long updateId,
        String chatId,
        String text,
        String username,
        String firstName
    ) {
        this(updateId, chatId, text, username, firstName, null, null, null);
    }

    public String startPayload() {
        if (text == null) {
            return null;
        }
        String stripped = text.strip();
        if (!stripped.startsWith("/start ")) {
            return null;
        }
        String payload = stripped.substring("/start ".length()).strip();
        return payload.isEmpty() ? null : payload;
    }

    public boolean hasCallbackQuery() {
        return callbackQueryId != null && !callbackQueryId.isBlank() && callbackData != null && !callbackData.isBlank();
    }
}
