package com.betx.application.port.out;

import com.betx.domain.telegram.TelegramUpdate;
import java.util.List;
import java.util.Map;

public interface TelegramBotGateway {
    String getBotUsername(String token);

    List<TelegramUpdate> getUpdates(String token, Long offset, int timeoutSeconds);

    void sendMessage(String token, String chatId, String text);

    default void sendMessage(String token, String chatId, String text, TelegramParseMode parseMode) {
        sendMessage(token, chatId, text);
    }

    default void sendMessage(
        String token,
        String chatId,
        String text,
        TelegramParseMode parseMode,
        Map<String, Object> replyMarkup
    ) {
        sendMessage(token, chatId, text, parseMode);
    }

    default void editMessageText(
        String token,
        String chatId,
        Integer messageId,
        String text,
        TelegramParseMode parseMode,
        Map<String, Object> replyMarkup
    ) {
        sendMessage(token, chatId, text, parseMode, replyMarkup);
    }

    default void answerCallbackQuery(String token, String callbackQueryId, String text, boolean showAlert) {
    }
}
