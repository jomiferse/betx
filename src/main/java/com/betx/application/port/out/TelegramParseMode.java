package com.betx.application.port.out;

/** Telegram message formatting modes supported by the outbound gateway. */
public enum TelegramParseMode {
    HTML("HTML");

    private final String apiValue;

    TelegramParseMode(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }
}
