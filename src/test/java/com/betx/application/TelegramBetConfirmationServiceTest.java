package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.port.out.BetExecutionGateway;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.ExchangeAccountGateway;
import com.betx.application.port.out.TelegramBetIntentRepository;
import com.betx.application.port.out.TelegramBotGateway;
import com.betx.domain.betfair.BetfairAutoBettingConfig;
import com.betx.domain.betfair.BetfairConfig;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.ExchangeConfig;
import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.BetSignal;
import com.betx.domain.signal.BetSide;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerAnalysis;
import com.betx.domain.signal.SignalScore;
import com.betx.domain.telegram.TelegramBetIntent;
import com.betx.domain.telegram.TelegramBetIntentStage;
import com.betx.domain.telegram.TelegramConnectionContext;
import com.betx.domain.telegram.TelegramUpdate;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TelegramBetConfirmationServiceTest {
    private static final ConfigPath CONFIG_PATH = new ConfigPath(Path.of("betx.yml"));

    @Test
    void offersConfirmationButtonsForNewBetSignals() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        TelegramBetConfirmationService service = service(
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            new RecordingExecutionGateway()
        );

        service.sync(CONFIG_PATH, resultWithLiveConfirmationSignal());

        assertThat(telegram.sentMessages()).hasSize(1);
        assertThat(telegram.sentMessages().getFirst().text())
            .contains("<b>BETX SIGNAL</b>")
            .contains("BET CONFIRMATION")
            .contains("Market movement detected")
            .contains("Score: 85/100 🟢 High confidence")
            .contains("Trigger: Odds moved favourably (-1.30%)")
            .contains("<b>CD Castellon vs Almeria</b>")
            .contains("Bet: Draw @ 3.80")
            .contains("Action: BACK on betfair")
            .contains("Previous odds: 3.85 -> 3.80 (-1.30%)")
            .contains("Kickoff: 06 Jun 2026 21:00 CEST")
            .contains("Market: Match Odds")
            .contains("Why this signal:")
            .contains("- Base market quality is acceptable")
            .contains("- Odds moved from 3.85 -&gt; 3.80")
            .contains("- Volatility is low")
            .contains("- Movement stands out versus recent baseline")
            .contains("Safety:")
            .contains("No bet is placed until you confirm and choose stake.")
            .contains("Betfair auto-betting is enabled. Confirmation required.")
            .contains("Confirm bet?")
            .doesNotContain("marketId")
            .doesNotContain("selectionId")
            .doesNotContain("Market ID")
            .doesNotContain("Selection ID")
            .doesNotContain("1.1")
            .doesNotContain("42")
            .doesNotContain("liquidity_ok")
            .doesNotContain("favorable_odds_movement");
        assertThat(telegram.sentMessages().getFirst().replyMarkup()).isNotNull();
        assertThat(intents.saved()).hasSize(1);
        assertThat(intents.saved().getFirst().stage()).isEqualTo(TelegramBetIntentStage.AWAITING_CONFIRMATION);
    }

    @Test
    void yesMovesIntentToStakeSelectionAndShowsAllowedAmounts() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        TelegramBetConfirmationService service = service(
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            new RecordingExecutionGateway()
        );

        service.sync(CONFIG_PATH, resultOf(signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)), analysis("Team A")));
        String intentId = intents.saved().getFirst().id();
        telegram.clear();
        gateway.addUpdate(newCallbackUpdate(1L, intentId, "yes", 77));

        service.sync(CONFIG_PATH, resultOf());

        assertThat(telegram.editedMessages()).singleElement().satisfies(edit -> {
            assertThat(edit.text())
                .contains("<b>CHOOSE STAKE</b>")
                .contains("<b>Team A v Team B</b>")
                .contains("Bet: Team A to win @ 2.50")
                .contains("Action: BACK on betfair")
                .contains("Balance available: 12.50")
                .contains("Max allowed: 5.00")
                .contains("Choose stake:")
                .doesNotContain("marketId")
                .doesNotContain("selectionId")
                .doesNotContain("Market ID")
                .doesNotContain("Selection ID")
                .doesNotContain("1.1")
                .doesNotContain("42");
            assertThat(edit.replyMarkup()).isNotNull();
        });
        assertThat(intents.updated().getFirst().stage()).isEqualTo(TelegramBetIntentStage.AWAITING_STAKE);
        assertThat(intents.updated().getFirst().availableBalance()).isEqualByComparingTo("12.50");
    }

    @Test
    void noCancelsPendingIntent() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        TelegramBetConfirmationService service = service(
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            new RecordingExecutionGateway()
        );

        service.sync(CONFIG_PATH, resultOf(signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)), analysis("Team A")));
        String intentId = intents.saved().getFirst().id();
        telegram.clear();
        gateway.addUpdate(newCallbackUpdate(1L, intentId, "no", 77));

        service.sync(CONFIG_PATH, resultOf());

        assertThat(telegram.editedMessages()).singleElement().satisfies(edit ->
            assertThat(edit.text())
                .contains("BET CANCELLED")
                .contains("<b>Team A v Team B</b>")
                .contains("Bet: Team A to win @ 2.50")
                .contains("Status: cancelled by user.")
                .doesNotContain("Market ID")
                .doesNotContain("Selection ID")
        );
        assertThat(intents.updated().getFirst().stage()).isEqualTo(TelegramBetIntentStage.CANCELLED);
    }

    @Test
    void telegramCallbackAnswerFailuresDoNotAbortSync() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        TelegramBetConfirmationService service = service(
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            new RecordingExecutionGateway()
        );

        service.sync(CONFIG_PATH, resultOf(signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)), analysis("Team A")));
        String firstIntentId = intents.saved().getFirst().id();
        telegram.clear();
        telegram.failCallbackAnswers = true;
        gateway.addUpdate(newCallbackUpdate(1L, firstIntentId, "no", 77));

        service.sync(CONFIG_PATH, resultOf(
            signal("betfair", "1.2", 43L, BigDecimal.valueOf(2.7), BigDecimal.valueOf(5)),
            analysis("Team C", "1.2", 43L)
        ));

        assertThat(intents.updated().getFirst().stage()).isEqualTo(TelegramBetIntentStage.CANCELLED);
        assertThat(intents.saved()).hasSize(2);
    }

    @Test
    void telegramSendFailuresDoNotAbortSyncAfterIntentIsSaved() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        telegram.failSends = true;
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        TelegramBetConfirmationService service = service(
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            new RecordingExecutionGateway()
        );

        service.sync(CONFIG_PATH, resultOf(signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)), analysis("Team A")));

        assertThat(intents.saved()).isEmpty();
    }

    @Test
    void doesNotCreateActiveIntentWhenTelegramIsDisconnected() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        telegram.connected = false;
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        TelegramBetConfirmationService service = service(
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            new RecordingExecutionGateway()
        );

        service.sync(CONFIG_PATH, resultOf(signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)), analysis("Team A")));

        assertThat(intents.saved()).isEmpty();
        assertThat(telegram.sentMessages()).isEmpty();
    }

    @Test
    void stakeSelectionExecutesTheBet() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        RecordingExecutionGateway executionGateway = new RecordingExecutionGateway();
        TelegramBetConfirmationService service = service(
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            executionGateway
        );

        service.sync(CONFIG_PATH, resultOf(signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)), analysis("Team A")));
        String intentId = intents.saved().getFirst().id();
        gateway.addUpdate(newCallbackUpdate(1L, intentId, "yes", 77));
        service.sync(CONFIG_PATH, resultOf());

        telegram.clear();
        gateway.addUpdate(newCallbackUpdate(2L, intentId, "stake", 77, BigDecimal.valueOf(5)));

        service.sync(CONFIG_PATH, resultOf());

        assertThat(executionGateway.orders()).singleElement().satisfies(order -> {
            assertThat(order.exchange()).isEqualTo("betfair");
            assertThat(order.marketId()).isEqualTo("1.1");
            assertThat(order.selectionId()).isEqualTo(42L);
            assertThat(order.stake()).isEqualByComparingTo("5");
        });
        assertThat(intents.updated().getLast().stage()).isEqualTo(TelegramBetIntentStage.EXECUTED);
        assertThat(telegram.editedMessages().getLast().text())
            .contains("BET EXECUTED")
            .contains("<b>Team A v Team B</b>")
            .contains("Bet: Team A to win @ 2.50")
            .contains("Stake: 5.00")
            .contains("Status: accepted.")
            .doesNotContain("Market ID")
            .doesNotContain("Selection ID");
    }

    @Test
    void disabledAutoBettingBlocksStakeSelectionWithoutExecutingTheBet() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        RecordingExecutionGateway executionGateway = new RecordingExecutionGateway();
        TelegramBetConfirmationService service = service(
            configWithAutoBetting(BigDecimal.valueOf(5), BigDecimal.valueOf(25), 3, false, true),
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            executionGateway,
            Clock.fixed(Instant.parse("2026-06-01T18:10:00Z"), ZoneOffset.UTC)
        );
        String intentId = "pending-stake";
        intents.save("data.db", new TelegramBetIntent(
            intentId,
            "betfair",
            "1.1",
            42L,
            "Team A v Team B",
            "Match Odds",
            "Team A",
            "liquidity_ok",
            BigDecimal.valueOf(2.5),
            BigDecimal.valueOf(5),
            BigDecimal.valueOf(12.5),
            null,
            "Stake selection requested.",
            TelegramBetIntentStage.AWAITING_STAKE,
            Instant.parse("2026-06-01T18:00:00Z"),
            Instant.parse("2026-06-01T18:00:00Z")
        ));

        telegram.clear();
        gateway.addUpdate(newCallbackUpdate(1L, intentId, "stake", 77, BigDecimal.valueOf(5)));

        service.sync(CONFIG_PATH, resultOf());

        assertThat(executionGateway.orders()).isEmpty();
        assertThat(intents.updated().getLast().stage()).isEqualTo(TelegramBetIntentStage.FAILED);
        assertThat(telegram.editedMessages().getLast().text())
            .contains("BET REJECTED")
            .contains("Bet: Team A to win @ 2.50")
            .contains("Status: Auto-betting is disabled.")
            .doesNotContain("Market ID")
            .doesNotContain("Selection ID");
    }

    @Test
    void autoBettingWithoutConfirmationExecutesSignalImmediately() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        RecordingExecutionGateway executionGateway = new RecordingExecutionGateway();
        TelegramBetConfirmationService service = service(
            configWithAutoBetting(BigDecimal.valueOf(3), BigDecimal.valueOf(25), 3, true, false),
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            executionGateway
        );

        service.sync(CONFIG_PATH, resultOf(
            signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)),
            analysis("Team A")
        ));

        assertThat(executionGateway.orders()).singleElement().satisfies(order -> {
            assertThat(order.exchange()).isEqualTo("betfair");
            assertThat(order.marketId()).isEqualTo("1.1");
            assertThat(order.selectionId()).isEqualTo(42L);
            assertThat(order.stake()).isEqualByComparingTo("3");
        });
        assertThat(intents.saved()).singleElement().satisfies(intent -> {
            assertThat(intent.stage()).isEqualTo(TelegramBetIntentStage.EXECUTED);
            assertThat(intent.selectedStake()).isEqualByComparingTo("3");
            assertThat(intent.resultMessage()).isEqualTo("accepted");
        });
        assertThat(telegram.sentMessages()).isEmpty();
    }

    @Test
    void skipsRecentIntentForSameSelectionDuringCooldown() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        TelegramBetConfirmationService service = service(
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            new RecordingExecutionGateway()
        );

        service.sync(CONFIG_PATH, resultOf(signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)), analysis("Team A")));
        String intentId = intents.saved().getFirst().id();
        gateway.addUpdate(newCallbackUpdate(1L, intentId, "no", 77));
        service.sync(CONFIG_PATH, resultOf());

        telegram.clear();
        service.sync(CONFIG_PATH, resultOf(signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)), analysis("Team A")));

        assertThat(intents.saved()).hasSize(1);
        assertThat(telegram.sentMessages()).isEmpty();
    }

    @Test
    void doesNotOfferNewIntentWhenOpenPositionLimitIsReached() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        TelegramBetConfirmationService service = service(
            configWithRisk(BigDecimal.valueOf(5), BigDecimal.valueOf(25), 1, true),
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            new RecordingExecutionGateway()
        );

        service.sync(CONFIG_PATH, resultOf(signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)), analysis("Team A")));
        telegram.clear();

        service.sync(CONFIG_PATH, resultOf(
            signal("betfair", "1.2", 43L, BigDecimal.valueOf(2.7), BigDecimal.valueOf(5)),
            analysis("Team C", "1.2", 43L)
        ));

        assertThat(intents.saved()).hasSize(1);
        assertThat(telegram.sentMessages()).isEmpty();
    }

    @Test
    void expiresStalePendingIntentsBeforeApplyingOpenPositionLimit() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        intents.save("data.db", new TelegramBetIntent(
            "stale-1",
            "betfair",
            "1.0",
            41L,
            "Old Event",
            "Match Odds",
            "Old Runner",
            "liquidity_ok",
            BigDecimal.valueOf(2.1),
            BigDecimal.valueOf(5),
            null,
            null,
            null,
            TelegramBetIntentStage.AWAITING_CONFIRMATION,
            Instant.parse("2026-06-01T09:00:00Z"),
            Instant.parse("2026-06-01T09:00:00Z")
        ));
        TelegramBetConfirmationService service = service(
            configWithRisk(BigDecimal.valueOf(5), BigDecimal.valueOf(25), 1, true),
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            new RecordingExecutionGateway(),
            Clock.fixed(Instant.parse("2026-06-01T10:00:01Z"), ZoneOffset.UTC)
        );

        service.sync(CONFIG_PATH, resultOf(
            signal("betfair", "1.2", 43L, BigDecimal.valueOf(2.7), BigDecimal.valueOf(5)),
            analysis("Team C", "1.2", 43L)
        ));

        assertThat(intents.updated()).singleElement()
            .satisfies(intent -> {
                assertThat(intent.id()).isEqualTo("stale-1");
                assertThat(intent.stage()).isEqualTo(TelegramBetIntentStage.CANCELLED);
                assertThat(intent.resultMessage()).isEqualTo("Expired before confirmation.");
            });
        assertThat(telegram.sentMessages()).hasSize(1);
        assertThat(intents.saved()).hasSize(2);
    }

    @Test
    void expiresStalePendingIntentsWhenTelegramContextIsUnavailable() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        telegram.connected = false;
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        intents.save("data.db", new TelegramBetIntent(
            "stale-1",
            "betfair",
            "1.0",
            41L,
            "Old Event",
            "Match Odds",
            "Old Runner",
            "liquidity_ok",
            BigDecimal.valueOf(2.1),
            BigDecimal.valueOf(5),
            null,
            null,
            null,
            TelegramBetIntentStage.AWAITING_CONFIRMATION,
            Instant.parse("2026-06-01T09:00:00Z"),
            Instant.parse("2026-06-01T09:00:00Z")
        ));
        TelegramBetConfirmationService service = service(
            configWithRisk(BigDecimal.valueOf(5), BigDecimal.valueOf(25), 1, true),
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            new RecordingExecutionGateway(),
            Clock.fixed(Instant.parse("2026-06-01T10:00:01Z"), ZoneOffset.UTC)
        );

        service.sync(CONFIG_PATH, resultOf());

        assertThat(intents.updated()).singleElement()
            .satisfies(intent -> {
                assertThat(intent.id()).isEqualTo("stale-1");
                assertThat(intent.stage()).isEqualTo(TelegramBetIntentStage.CANCELLED);
                assertThat(intent.resultMessage()).isEqualTo("Expired before confirmation.");
            });
    }

    @Test
    void offersConfirmationWhenOnlyExecutedIntentReachedPositionLimit() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        intents.save("data.db", new TelegramBetIntent(
            "executed-1",
            "betfair",
            "1.0",
            41L,
            "Old Event",
            "Match Odds",
            "Old Runner",
            "liquidity_ok",
            BigDecimal.valueOf(2.1),
            BigDecimal.valueOf(5),
            null,
            BigDecimal.valueOf(5),
            "accepted",
            TelegramBetIntentStage.EXECUTED,
            Instant.parse("2026-06-01T10:00:00Z"),
            Instant.parse("2026-06-01T10:01:00Z")
        ));
        TelegramBetConfirmationService service = service(
            configWithRisk(BigDecimal.valueOf(5), BigDecimal.valueOf(25), 1, true),
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            new RecordingExecutionGateway()
        );

        service.sync(CONFIG_PATH, resultOf(
            signal("betfair", "1.2", 43L, BigDecimal.valueOf(2.7), BigDecimal.valueOf(5)),
            analysis("Team C", "1.2", 43L)
        ));

        assertThat(telegram.sentMessages()).hasSize(1);
        assertThat(intents.saved()).hasSize(2);
    }

    @Test
    void blocksStakeSelectionWhenDailyRiskLimitWouldBeExceeded() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        RecordingExecutionGateway executionGateway = new RecordingExecutionGateway();
        TelegramBetConfirmationService service = service(
            configWithRisk(BigDecimal.valueOf(5), BigDecimal.valueOf(6), 3, true),
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            executionGateway
        );

        service.sync(CONFIG_PATH, resultOf(signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)), analysis("Team A")));
        String intentId = intents.saved().getFirst().id();
        gateway.addUpdate(newCallbackUpdate(1L, intentId, "yes", 77));
        service.sync(CONFIG_PATH, resultOf());
        telegram.clear();
        gateway.addUpdate(newCallbackUpdate(2L, intentId, "stake", 77, BigDecimal.valueOf(5)));
        service.sync(CONFIG_PATH, resultOf());

        telegram.clear();
        service.sync(CONFIG_PATH, resultOf(
            signal("betfair", "1.2", 43L, BigDecimal.valueOf(2.7), BigDecimal.valueOf(5)),
            analysis("Team C", "1.2", 43L)
        ));
        String secondIntentId = intents.saved().getLast().id();
        gateway.addUpdate(newCallbackUpdate(3L, secondIntentId, "yes", 78));
        service.sync(CONFIG_PATH, resultOf());
        telegram.clear();
        gateway.addUpdate(newCallbackUpdate(4L, secondIntentId, "stake", 78, BigDecimal.valueOf(5)));

        service.sync(CONFIG_PATH, resultOf());

        assertThat(executionGateway.orders()).hasSize(1);
        assertThat(intents.updated().getLast().stage()).isEqualTo(TelegramBetIntentStage.FAILED);
        assertThat(telegram.editedMessages().getLast().text())
            .contains("BET REJECTED")
            .contains("Stake: 5.00")
            .contains("Status: Daily risk limit exceeded.")
            .doesNotContain("Market ID")
            .doesNotContain("Selection ID");
    }

    private TelegramBetConfirmationService service(
        RecordingTelegramConnectionService telegram,
        RecordingTelegramGateway gateway,
        RecordingIntentRepository intents,
        ExchangeAccountGateway accountGateway,
        BetExecutionGateway executionGateway
    ) {
        return service(
            configWithAutoBetting(BigDecimal.valueOf(5), BigDecimal.valueOf(25), 3, true, true),
            telegram,
            gateway,
            intents,
            accountGateway,
            executionGateway
        );
    }

    private TelegramBetConfirmationService service(
        BetxConfig config,
        RecordingTelegramConnectionService telegram,
        RecordingTelegramGateway gateway,
        RecordingIntentRepository intents,
        ExchangeAccountGateway accountGateway,
        BetExecutionGateway executionGateway
    ) {
        return service(config, telegram, gateway, intents, accountGateway, executionGateway, Clock.systemUTC());
    }

    private TelegramBetConfirmationService service(
        BetxConfig config,
        RecordingTelegramConnectionService telegram,
        RecordingTelegramGateway gateway,
        RecordingIntentRepository intents,
        ExchangeAccountGateway accountGateway,
        BetExecutionGateway executionGateway,
        Clock clock
    ) {
        return new TelegramBetConfirmationService(
            new StaticConfigRepository(config),
            telegram,
            gateway,
            intents,
            accountGateway,
            executionGateway,
            clock
        );
    }

    private DryRunSignalsResult resultOf(BetSignal signal, RunnerAnalysis analysis) {
        return new DryRunSignalsResult(
            List.of(signal),
            List.of(),
            false,
            0,
            0,
            List.of(),
            List.of(analysis),
            0,
            0,
            0,
            0
        );
    }

    private DryRunSignalsResult resultWithLiveConfirmationSignal() {
        MarketSnapshot previous = new MarketSnapshot(
            "betfair",
            "1.1",
            "Match Odds",
            "CD Castellon v Almeria",
            "Spanish Segunda Division",
            Instant.parse("2026-06-06T19:00:00Z"),
            42L,
            "The Draw",
            BigDecimal.valueOf(3.85),
            BigDecimal.valueOf(4.00),
            BigDecimal.valueOf(0.04),
            BigDecimal.valueOf(12_000)
        );
        MarketSnapshot current = new MarketSnapshot(
            "betfair",
            "1.1",
            "Match Odds",
            "CD Castellon v Almeria",
            "Spanish Segunda Division",
            Instant.parse("2026-06-06T19:00:00Z"),
            42L,
            "The Draw",
            BigDecimal.valueOf(3.80),
            BigDecimal.valueOf(3.95),
            BigDecimal.valueOf(0.04),
            BigDecimal.valueOf(12_500)
        );
        RunnerAnalysis analysis = RunnerAnalysis.from(
            current,
            RecommendationType.BET,
            "liquidity_ok, spread_ok, favorable_odds_movement",
            new SignalScore(85, "High confidence", List.of(
                "Base market quality is acceptable",
                "Odds moved from 3.85 -> 3.80",
                "Volatility is low",
                "Movement stands out versus recent baseline"
            ))
        );
        return new DryRunSignalsResult(
            List.of(new BetSignal("betfair", "1.1", 42L, BetSide.BACK, BigDecimal.valueOf(3.80), BigDecimal.valueOf(5), analysis.reason(), "live")),
            List.of(),
            false,
            0,
            0,
            List.of(new MarketSnapshotChange(
                previous,
                current,
                new NumericChange(previous.bestBackPrice(), current.bestBackPrice(), BigDecimal.valueOf(-0.05), BigDecimal.valueOf(-1.30)),
                new NumericChange(previous.bestLayPrice(), current.bestLayPrice(), BigDecimal.valueOf(-0.05), BigDecimal.valueOf(-1.25)),
                new NumericChange(previous.spread(), current.spread(), BigDecimal.ZERO, BigDecimal.ZERO),
                new NumericChange(previous.liquidity(), current.liquidity(), BigDecimal.valueOf(500), BigDecimal.valueOf(4.17))
            )),
            List.of(analysis),
            0,
            0,
            0,
            0
        );
    }

    private DryRunSignalsResult resultOf() {
        return new DryRunSignalsResult(List.of(), List.of(), false);
    }

    private BetSignal signal(String exchange, String marketId, long selectionId, BigDecimal odds, BigDecimal stake) {
        return new BetSignal(exchange, marketId, selectionId, BetSide.BACK, odds, stake, "liquidity_ok", "live");
    }

    private BetSignal signal(String exchange, String marketId, long selectionId, BigDecimal odds, BigDecimal stake, String reason) {
        return new BetSignal(exchange, marketId, selectionId, BetSide.BACK, odds, stake, reason, "live");
    }

    private RunnerAnalysis analysis(String runnerName) {
        return analysis(runnerName, "1.1", 42L);
    }

    private RunnerAnalysis analysis(String runnerName, String marketId, long selectionId) {
        return new RunnerAnalysis(
            "betfair",
            marketId,
            "Match Odds",
            "Team A v Team B",
            "La Liga",
            Instant.parse("2026-06-01T18:00:00Z"),
            selectionId,
            runnerName,
            BigDecimal.valueOf(2.5),
            BigDecimal.valueOf(2.6),
            BigDecimal.valueOf(0.04),
            BigDecimal.valueOf(1_200),
            RecommendationType.BET,
            "liquidity ok"
        );
    }

    private BetxConfig configWithRisk(BigDecimal maxStake, BigDecimal maxDailyLoss, int maxOpenPositions, boolean liveBettingEnabled) {
        return configWithAutoBetting(maxStake, maxDailyLoss, maxOpenPositions, liveBettingEnabled, true);
    }

    private BetxConfig configWithAutoBetting(
        BigDecimal maxStake,
        BigDecimal maxDailyLoss,
        int maxOpenPositions,
        boolean enabled,
        boolean requestConfirmation
    ) {
        BetxConfig defaults = BetxConfig.defaults();
        return new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            defaults.betfair(),
            List.of(new ExchangeConfig(
                "betfair",
                true,
                new BetfairConfig(
                    "user",
                    "password",
                    "app-key",
                    null,
                    new BetfairAutoBettingConfig(enabled, requestConfirmation, maxStake, maxDailyLoss, maxOpenPositions)
                )
            )),
            defaults.marketData(),
            defaults.storage(),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml()
        );
    }

    private TelegramUpdate newCallbackUpdate(long updateId, String intentId, String action, Integer messageId) {
        return newCallbackUpdate(updateId, intentId, action, messageId, null);
    }

    private TelegramUpdate newCallbackUpdate(long updateId, String intentId, String action, Integer messageId, BigDecimal amount) {
        String callbackData = amount == null
            ? "bet:" + intentId + ":" + action
            : "bet:" + intentId + ":stake:" + amount.toPlainString();
        return new TelegramUpdate(
            updateId,
            "12345",
            null,
            "user",
            "Jose",
            "callback-" + updateId,
            callbackData,
            messageId
        );
    }

    private record StaticConfigRepository(BetxConfig config) implements BetxConfigRepository {
        @Override
        public BetxConfig load(ConfigPath path) {
            return config;
        }

        @Override
        public boolean writeDefault(ConfigPath path, boolean force) {
            return false;
        }

        @Override
        public void saveTelegramFields(ConfigPath path, Map<String, Object> fields) {
        }
    }

    private static final class RecordingTelegramConnectionService extends TelegramConnectionService {
        private final List<SentMessage> sentMessages = new ArrayList<>();
        private final List<EditedMessage> editedMessages = new ArrayList<>();
        private final List<String> callbackAnswers = new ArrayList<>();
        private boolean connected = true;
        private boolean failSends;
        private boolean failEdits;
        private boolean failCallbackAnswers;

        private RecordingTelegramConnectionService() {
            super(null, null, null);
        }

        @Override
        public Optional<TelegramConnectionContext> connectionContext(ConfigPath configPath) {
            return connected ? Optional.of(new TelegramConnectionContext("token", "12345")) : Optional.empty();
        }

        @Override
        public boolean sendMessageIfConnected(
            ConfigPath configPath,
            String text,
            com.betx.application.port.out.TelegramParseMode parseMode,
            Map<String, Object> replyMarkup
        ) {
            if (failSends) {
                throw new IllegalStateException("Telegram API request failed.");
            }
            sentMessages.add(new SentMessage(text, replyMarkup));
            return true;
        }

        @Override
        public boolean editMessageIfConnected(
            ConfigPath configPath,
            Integer messageId,
            String text,
            com.betx.application.port.out.TelegramParseMode parseMode,
            Map<String, Object> replyMarkup
        ) {
            if (failEdits) {
                throw new IllegalStateException("Telegram API request failed.");
            }
            editedMessages.add(new EditedMessage(messageId, text, replyMarkup));
            return true;
        }

        @Override
        public boolean answerCallbackIfConnected(ConfigPath configPath, String callbackQueryId, String text, boolean showAlert) {
            if (failCallbackAnswers) {
                throw new IllegalStateException("Telegram API request failed.");
            }
            callbackAnswers.add(callbackQueryId + ":" + text + ":" + showAlert);
            return true;
        }

        void clear() {
            sentMessages.clear();
            editedMessages.clear();
            callbackAnswers.clear();
        }

        List<SentMessage> sentMessages() {
            return sentMessages;
        }

        List<EditedMessage> editedMessages() {
            return editedMessages;
        }
    }

    private static final class RecordingTelegramGateway implements TelegramBotGateway {
        private final List<TelegramUpdate> updates = new ArrayList<>();

        @Override
        public String getBotUsername(String token) {
            return "bot";
        }

        @Override
        public List<TelegramUpdate> getUpdates(String token, Long offset, int timeoutSeconds) {
            return updates.stream()
                .filter(update -> offset == null || update.updateId() >= offset)
                .toList();
        }

        @Override
        public void sendMessage(String token, String chatId, String text) {
        }

        void addUpdate(TelegramUpdate update) {
            updates.add(update);
        }
    }

    private record SentMessage(String text, Map<String, Object> replyMarkup) {
    }

    private record EditedMessage(Integer messageId, String text, Map<String, Object> replyMarkup) {
    }

    private static final class RecordingIntentRepository implements TelegramBetIntentRepository {
        private final List<TelegramBetIntent> saved = new ArrayList<>();
        private final List<TelegramBetIntent> updated = new ArrayList<>();

        @Override
        public Optional<TelegramBetIntent> findActiveByKey(String databasePath, String exchange, String marketId, long selectionId) {
            return saved.stream()
                .filter(intent -> intent.exchange().equals(exchange)
                    && intent.marketId().equals(marketId)
                    && intent.selectionId() == selectionId
                    && intent.stage().isActive())
                .findFirst();
        }

        @Override
        public Optional<TelegramBetIntent> findLatestByKeySince(
            String databasePath,
            String exchange,
            String marketId,
            long selectionId,
            Instant since
        ) {
            return saved.stream()
                .filter(intent -> intent.exchange().equals(exchange)
                    && intent.marketId().equals(marketId)
                    && intent.selectionId() == selectionId
                    && !intent.updatedAt().isBefore(since))
                .findFirst();
        }

        @Override
        public Optional<TelegramBetIntent> findById(String databasePath, String id) {
            return saved.stream().filter(intent -> intent.id().equals(id)).findFirst();
        }

        @Override
        public List<TelegramBetIntent> listRecent(String databasePath, int limit) {
            return saved.stream()
                .limit(limit)
                .toList();
        }

        @Override
        public List<TelegramBetIntent> listByStages(String databasePath, List<TelegramBetIntentStage> stages, int limit) {
            return saved.stream()
                .filter(intent -> stages.contains(intent.stage()))
                .limit(limit)
                .toList();
        }

        @Override
        public long countByStages(String databasePath, List<TelegramBetIntentStage> stages) {
            return saved.stream()
                .filter(intent -> stages.contains(intent.stage()))
                .count();
        }

        @Override
        public BigDecimal sumSelectedStakeByStageSince(String databasePath, TelegramBetIntentStage stage, Instant since) {
            return saved.stream()
                .filter(intent -> intent.stage() == stage)
                .filter(intent -> !intent.updatedAt().isBefore(since))
                .map(TelegramBetIntent::selectedStake)
                .filter(stake -> stake != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        @Override
        public void save(String databasePath, TelegramBetIntent intent) {
            saved.add(intent);
        }

        @Override
        public void update(String databasePath, TelegramBetIntent intent) {
            updated.add(intent);
            for (int index = 0; index < saved.size(); index++) {
                if (saved.get(index).id().equals(intent.id())) {
                    saved.set(index, intent);
                    return;
                }
            }
            saved.add(intent);
        }

        @Override
        public long loadLastProcessedUpdateId(String databasePath) {
            return 0L;
        }

        @Override
        public void saveLastProcessedUpdateId(String databasePath, long updateId) {
        }

        List<TelegramBetIntent> saved() {
            return saved;
        }

        List<TelegramBetIntent> updated() {
            return updated;
        }
    }

    private static final class StaticAccountGateway implements ExchangeAccountGateway {
        private final BigDecimal balance;

        private StaticAccountGateway(BigDecimal balance) {
            this.balance = balance;
        }

        @Override
        public Optional<BigDecimal> availableBalance(BetxConfig config, String exchange) {
            return Optional.of(balance);
        }
    }

    private static final class RecordingExecutionGateway implements BetExecutionGateway {
        private final List<com.betx.domain.order.BetOrder> orders = new ArrayList<>();

        @Override
        public com.betx.domain.order.BetExecutionResult execute(com.betx.domain.order.BetOrder order) {
            orders.add(order);
            return new com.betx.domain.order.BetExecutionResult(true, "accepted");
        }

        List<com.betx.domain.order.BetOrder> orders() {
            return orders;
        }
    }
}
