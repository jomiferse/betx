package com.betx.application.port.out;

/** Persists Telegram polling state. */
public interface TelegramStateRepository {
    long loadLastProcessedUpdateId(String databasePath);

    void saveLastProcessedUpdateId(String databasePath, long updateId);
}
