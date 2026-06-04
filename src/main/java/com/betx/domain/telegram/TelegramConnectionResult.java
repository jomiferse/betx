package com.betx.domain.telegram;

public record TelegramConnectionResult(boolean connected, String deepLink, String chatId) {
}
