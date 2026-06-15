package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.port.out.BetExecutionGateway;
import com.betx.cli.PaperTradeCommand;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PaperTradingSafetyTest {
    @Test
    void paperTradingDoesNotDependOnRealOrderExecutionPort() {
        assertThat(hasDependency(RunPaperTradingService.class, BetExecutionGateway.class)).isFalse();
        assertThat(hasDependency(PaperTradeCommand.class, BetExecutionGateway.class)).isFalse();
    }

    private boolean hasDependency(Class<?> type, Class<?> dependency) {
        boolean fieldDependency = Arrays.stream(type.getDeclaredFields())
            .map(Field::getType)
            .anyMatch(dependency::equals);
        boolean constructorDependency = Arrays.stream(type.getDeclaredConstructors())
            .map(Constructor::getParameterTypes)
            .flatMap(Arrays::stream)
            .anyMatch(dependency::equals);
        return fieldDependency || constructorDependency;
    }
}
