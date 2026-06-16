package com.betx.domain.config;

import com.betx.common.ConfigException;
import com.betx.domain.betfair.BetfairAutoBettingConfig;
import java.math.BigDecimal;
import java.util.List;

public class BetxConfigValidator {
    public void validate(BetxConfig config) {
        requirePositive(config.risk().maxStake(), "risk.max_stake");
        requirePositive(config.risk().maxDailyLoss(), "risk.max_daily_loss");
        if (config.risk().maxOpenPositions() <= 0) {
            throw new ConfigException("risk.max_open_positions must be greater than zero.");
        }
        if (!config.storage().type().equals("sqlite")) {
            throw new ConfigException("storage.type must be sqlite.");
        }
        if (config.marketData().pollIntervalSeconds() <= 0) {
            throw new ConfigException("market_data.poll_interval_seconds must be greater than zero.");
        }
        if (config.marketData().maxMarkets() < 0) {
            throw new ConfigException("market_data.max_markets must be zero or greater.");
        }
        if (config.marketData().betfairEventBatchSize() <= 0) {
            throw new ConfigException("market_data.betfair_event_batch_size must be greater than zero.");
        }
        if (config.paper().pollInterval().isZero() || config.paper().pollInterval().isNegative()) {
            throw new ConfigException("paper.poll_interval must be greater than zero.");
        }
        if (config.paper().closingCaptureMinutesBeforeStart() < 0) {
            throw new ConfigException("paper.closing_capture_minutes_before_start must be zero or greater.");
        }
        if (config.paper().settlementPollInterval().isZero() || config.paper().settlementPollInterval().isNegative()) {
            throw new ConfigException("paper.settlement_poll_interval must be greater than zero.");
        }
        if (!List.of("key_events", "all_signals", "orders_only").contains(config.telegram().alerts().mode())) {
            throw new ConfigException("telegram.alerts.mode must be one of: key_events, all_signals, orders_only.");
        }
        validateResilience(config.resilience().betfair(), "resilience.betfair");
        validateResilience(config.resilience().telegram(), "resilience.telegram");
        validateResilience(config.resilience().openrouter(), "resilience.openrouter");
        validateExecutionQueue(config.execution().queue());
        validateIntelligence(config.intelligence());
        config.exchanges().stream()
            .filter(exchange -> "betfair".equals(exchange.name()))
            .map(exchange -> exchange.betfair().autoBetting())
            .filter(BetfairAutoBettingConfig::enabled)
            .forEach(this::validateBetfairAutoBetting);
    }

    private void validateIntelligence(IntelligenceConfig intelligence) {
        if (!intelligence.enabled()) {
            return;
        }
        if (!"openrouter".equals(intelligence.provider())) {
            throw new ConfigException("intelligence.provider must be openrouter.");
        }
        if (intelligence.timeoutSeconds() <= 0) {
            throw new ConfigException("intelligence.timeout_seconds must be greater than zero.");
        }
        if (intelligence.minConfidence() < 0 || intelligence.minConfidence() > 100) {
            throw new ConfigException("intelligence.min_confidence must be between 0 and 100.");
        }
        if (intelligence.autoBettingPolicy() == null) {
            throw new ConfigException("intelligence.auto_betting_policy must be one of: strict_approve, block_only_on_reject.");
        }
        if (intelligence.apiKeyEnv().startsWith("sk-")) {
            throw new ConfigException("intelligence.api_key_env must be an environment variable name, not an API key.");
        }
    }

    private void validateBetfairAutoBetting(BetfairAutoBettingConfig autoBetting) {
        requirePositive(autoBetting.maxStake(), "exchanges.betfair.auto_betting.max_stake");
        requirePositive(autoBetting.maxDailyLoss(), "exchanges.betfair.auto_betting.max_daily_loss");
        if (autoBetting.maxOpenPositions() <= 0) {
            throw new ConfigException("exchanges.betfair.auto_betting.max_open_positions must be greater than zero.");
        }
    }

    private void validateResilience(ResilienceDependencyConfig dependency, String prefix) {
        if (dependency.failureThreshold() <= 0) {
            throw new ConfigException(prefix + ".failure_threshold must be greater than zero.");
        }
        if (dependency.cooldownDuration().isZero() || dependency.cooldownDuration().isNegative()) {
            throw new ConfigException(prefix + ".cooldown must be greater than zero.");
        }
    }

    private void validateExecutionQueue(ExecutionQueueConfig queue) {
        if (queue.maxPendingPerExchange() <= 0) {
            throw new ConfigException("execution.queue.max_pending_per_exchange must be greater than zero.");
        }
        if (queue.orderTtl().isZero() || queue.orderTtl().isNegative()) {
            throw new ConfigException("execution.queue.order_ttl must be greater than zero.");
        }
        if (queue.staleBalanceTtl().isZero() || queue.staleBalanceTtl().isNegative()) {
            throw new ConfigException("execution.queue.stale_balance_ttl must be greater than zero.");
        }
        if (queue.revalidateOddsAfter().isZero() || queue.revalidateOddsAfter().isNegative()) {
            throw new ConfigException("execution.queue.revalidate_odds_after must be greater than zero.");
        }
        if (queue.minEffectiveBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new ConfigException("execution.queue.min_effective_balance must be zero or greater.");
        }
    }

    private void requirePositive(BigDecimal value, String field) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ConfigException(field + " must be greater than zero.");
        }
    }
}
