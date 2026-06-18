package com.betx.adapter.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SensitiveFieldSanitizerTest {
    @Test
    void redactsSensitiveKeysRecursively() {
        SensitiveFieldSanitizer sanitizer = new SensitiveFieldSanitizer();

        Map<String, Object> sanitized = sanitizer.sanitize(Map.of(
            "api_key", "sk-secret",
            "safe", "visible",
            "nested", Map.of(
                "sessionToken", "session",
                "count", 3
            ),
            "items", java.util.List.of(Map.of("chat_id", "12345"))
        ));

        assertThat(sanitized)
            .containsEntry("api_key", "[REDACTED]")
            .containsEntry("safe", "visible");
        assertThat((Map<String, Object>) sanitized.get("nested"))
            .containsEntry("sessionToken", "[REDACTED]")
            .containsEntry("count", 3);
        assertThat((java.util.List<?>) sanitized.get("items"))
            .singleElement()
            .satisfies(item -> assertThat((Map<String, Object>) item).containsEntry("chat_id", "[REDACTED]"));
    }
}
