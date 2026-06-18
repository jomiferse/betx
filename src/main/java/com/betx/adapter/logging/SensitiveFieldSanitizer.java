package com.betx.adapter.logging;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SensitiveFieldSanitizer {
    private static final String REDACTED = "[REDACTED]";
    private static final List<String> SENSITIVE_MARKERS = List.of(
        "token",
        "password",
        "api_key",
        "apikey",
        "session",
        "chat_id",
        "chatid",
        "secret",
        "credential"
    );

    public Map<String, Object> sanitize(Map<String, ?> fields) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        if (fields == null) {
            return sanitized;
        }
        fields.forEach((key, value) -> sanitized.put(key, sensitive(key) ? REDACTED : sanitizeValue(value)));
        return sanitized;
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> {
                String stringKey = String.valueOf(key);
                sanitized.put(stringKey, sensitive(stringKey) ? REDACTED : sanitizeValue(nestedValue));
            });
            return sanitized;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::sanitizeValue).toList();
        }
        return value;
    }

    private boolean sensitive(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return SENSITIVE_MARKERS.stream().anyMatch(normalized::contains);
    }
}
