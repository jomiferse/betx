package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.port.out.BetExecutionGateway;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.ExchangeAccountGateway;
import com.betx.application.port.out.ExchangeExposureGateway;
import com.betx.application.port.out.MarketSnapshotRepository;
import com.betx.application.port.out.SignalHistoryRepository;
import com.betx.application.port.out.BetIntentRepository;
import com.betx.application.port.out.TelegramStateRepository;
import com.betx.application.port.out.TelegramBotGateway;
import com.betx.domain.betfair.BetfairAutoBettingConfig;
import com.betx.domain.betfair.BetfairConfig;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.ExchangeConfig;
import com.betx.domain.exposure.ExchangeExposure;
import com.betx.domain.exposure.ExchangeSettledOrder;
import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.BetSignal;
import com.betx.domain.signal.BetSide;
import com.betx.domain.signal.ObservedMarketSnapshot;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerAnalysis;
import com.betx.domain.signal.SignalScore;
import com.betx.domain.order.BetIntent;
import com.betx.domain.order.BetIntentSource;
import com.betx.domain.order.BetIntentStage;
import com.betx.domain.order.BetSettlementResult;
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
import java.util.Set;
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
            StaticExposureGateway.available(0, BigDecimal.ZERO),
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
        assertThat(intents.saved().getFirst().stage()).isEqualTo(BetIntentStage.AWAITING_CONFIRMATION);
        assertThat(intents.saved().getFirst().source()).isEqualTo(BetIntentSource.TELEGRAM_CONFIRMATION);
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
            StaticExposureGateway.available(0, BigDecimal.ZERO),
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
        assertThat(intents.updated().getFirst().stage()).isEqualTo(BetIntentStage.AWAITING_STAKE);
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
            StaticExposureGateway.available(0, BigDecimal.ZERO),
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
        assertThat(intents.updated().getFirst().stage()).isEqualTo(BetIntentStage.CANCELLED);
    }

    @Test
    void processCallbacksStartsAfterPersistedOffset() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        RecordingTelegramStateRepository stateRepository = new RecordingTelegramStateRepository();
        TelegramBetConfirmationService service = service(
            telegram,
            gateway,
            intents,
            stateRepository,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            StaticExposureGateway.available(0, BigDecimal.ZERO),
            new RecordingExecutionGateway()
        );

        service.sync(CONFIG_PATH, resultOf(signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)), analysis("Team A")));
        String intentId = intents.saved().getFirst().id();
        telegram.clear();
        stateRepository.lastProcessedUpdateId = 10L;
        gateway.addUpdate(newCallbackUpdate(10L, intentId, "no", 77));
        gateway.addUpdate(newCallbackUpdate(11L, intentId, "no", 78));

        service.sync(CONFIG_PATH, resultOf());

        assertThat(intents.updated()).singleElement().satisfies(intent -> assertThat(intent.stage()).isEqualTo(BetIntentStage.CANCELLED));
        assertThat(telegram.callbackAnswers).containsExactly("callback-11:Cancelled.:false");
        assertThat(stateRepository.lastProcessedUpdateId()).isEqualTo(11L);
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
            StaticExposureGateway.available(0, BigDecimal.ZERO),
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

        assertThat(intents.updated().getFirst().stage()).isEqualTo(BetIntentStage.CANCELLED);
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
            StaticExposureGateway.available(0, BigDecimal.ZERO),
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
            StaticExposureGateway.available(0, BigDecimal.ZERO),
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
            StaticExposureGateway.available(0, BigDecimal.ZERO),
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
        assertThat(intents.updated().getLast().stage()).isEqualTo(BetIntentStage.EXECUTED);
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
            StaticExposureGateway.available(0, BigDecimal.ZERO),
            executionGateway,
            Clock.fixed(Instant.parse("2026-06-01T18:10:00Z"), ZoneOffset.UTC)
        );
        String intentId = "pending-stake";
        intents.save("data.db", new BetIntent(
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
            BetIntentStage.AWAITING_STAKE,
            Instant.parse("2026-06-01T18:00:00Z"),
            Instant.parse("2026-06-01T18:00:00Z")
        ));

        telegram.clear();
        gateway.addUpdate(newCallbackUpdate(1L, intentId, "stake", 77, BigDecimal.valueOf(5)));

        service.sync(CONFIG_PATH, resultOf());

        assertThat(executionGateway.orders()).isEmpty();
        assertThat(intents.updated().getLast().stage()).isEqualTo(BetIntentStage.FAILED);
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
            StaticExposureGateway.available(0, BigDecimal.ZERO),
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
            assertThat(intent.stage()).isEqualTo(BetIntentStage.EXECUTED);
            assertThat(intent.availableBalance()).isEqualByComparingTo("12.5");
            assertThat(intent.selectedStake()).isEqualByComparingTo("3");
            assertThat(intent.resultMessage()).isEqualTo("accepted");
        });
        assertThat(telegram.sentMessages()).singleElement()
            .satisfies(message -> assertThat(message.text()).contains("REAL BET PLACED"));
    }

    @Test
    void automaticBettingStopsCurrentCycleWhenOpenPositionCapacityIsFilled() {
        RecordingIntentRepository intents = new RecordingIntentRepository();
        RecordingExecutionGateway executionGateway = new RecordingExecutionGateway();
        CountingAccountGateway accountGateway = new CountingAccountGateway(BigDecimal.valueOf(12.5));
        CountingExposureGateway exposureGateway = new CountingExposureGateway(new ExchangeExposure(
            true,
            0,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            List.of(),
            Set.of(),
            null
        ));
        TelegramBetConfirmationService service = service(
            configWithAutoBetting(BigDecimal.valueOf(1), BigDecimal.valueOf(25), 5, true, false),
            new RecordingTelegramConnectionService(),
            new RecordingTelegramGateway(),
            intents,
            accountGateway,
            exposureGateway,
            executionGateway
        );
        List<BetSignal> signals = java.util.stream.IntStream.range(0, 20)
            .mapToObj(index -> signal("betfair", "1." + index, 42L + index, BigDecimal.valueOf(2.5), BigDecimal.ONE))
            .toList();
        List<RunnerAnalysis> analyses = java.util.stream.IntStream.range(0, 20)
            .mapToObj(index -> analysis("Runner " + index, "1." + index, 42L + index))
            .toList();

        service.sync(CONFIG_PATH, resultOf(signals, analyses));

        assertThat(executionGateway.orders()).hasSize(5);
        assertThat(intents.saved()).hasSize(5);
        assertThat(exposureGateway.calls()).isEqualTo(1);
        assertThat(accountGateway.calls()).isEqualTo(1);
    }

    @Test
    void automaticBettingExecutesAtMostOneSelectionPerMarket() {
        RecordingIntentRepository intents = new RecordingIntentRepository();
        RecordingExecutionGateway executionGateway = new RecordingExecutionGateway();
        TelegramBetConfirmationService service = service(
            configWithAutoBetting(BigDecimal.valueOf(1), BigDecimal.valueOf(25), 3, true, false),
            new RecordingTelegramConnectionService(),
            new RecordingTelegramGateway(),
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            StaticExposureGateway.available(0, BigDecimal.ZERO),
            executionGateway
        );

        service.sync(CONFIG_PATH, resultOf(
            List.of(
                signal("betfair", "1.1", 47966L, BigDecimal.valueOf(1.84), BigDecimal.ONE),
                signal("betfair", "1.1", 58805L, BigDecimal.valueOf(3.70), BigDecimal.ONE)
            ),
            List.of(analysis("Iran", "1.1", 47966L), analysis("The Draw", "1.1", 58805L))
        ));

        assertThat(executionGateway.orders()).singleElement()
            .satisfies(order -> assertThat(order.marketId()).isEqualTo("1.1"));
        assertThat(intents.saved()).singleElement()
            .satisfies(intent -> assertThat(intent.marketId()).isEqualTo("1.1"));
    }

    @Test
    void automaticBettingSkipsSelectionWithExistingExecutedIntent() {
        RecordingIntentRepository intents = new RecordingIntentRepository();
        Instant previousBetAt = Instant.parse("2026-06-16T12:54:09Z");
        intents.save("data.db", new BetIntent(
            "executed-argentina-draw",
            BetIntentSource.AUTOMATIC,
            "betfair",
            "1.251397469",
            58805L,
            "Argentina v Algeria",
            "Match Odds",
            "The Draw",
            "liquidity_ok",
            BigDecimal.valueOf(4.8),
            BigDecimal.ONE,
            BigDecimal.valueOf(12.5),
            BigDecimal.ONE,
            "accepted",
            "bet-123",
            BetIntentStage.EXECUTED,
            previousBetAt,
            previousBetAt
        ));
        RecordingExecutionGateway executionGateway = new RecordingExecutionGateway();
        TelegramBetConfirmationService service = service(
            configWithAutoBetting(BigDecimal.valueOf(1), BigDecimal.valueOf(25), 3, true, false),
            new RecordingTelegramConnectionService(),
            new RecordingTelegramGateway(),
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            StaticExposureGateway.available(0, BigDecimal.ZERO),
            executionGateway
        );

        service.sync(CONFIG_PATH, resultOf(
            signal("betfair", "1.251397469", 58805L, BigDecimal.valueOf(4.7), BigDecimal.ONE),
            analysis("The Draw", "1.251397469", 58805L)
        ));

        assertThat(executionGateway.orders()).isEmpty();
        assertThat(intents.saved()).hasSize(1);
    }

    @Test
    void automaticBettingReservesBalanceBetweenOrdersInSameExchangeCycle() {
        RecordingIntentRepository intents = new RecordingIntentRepository();
        RecordingExecutionGateway executionGateway = new RecordingExecutionGateway();
        TelegramBetConfirmationService service = service(
            configWithAutoBetting(BigDecimal.valueOf(1), BigDecimal.valueOf(25), 3, true, false),
            new RecordingTelegramConnectionService(),
            new RecordingTelegramGateway(),
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(17.7)),
            StaticExposureGateway.available(0, BigDecimal.ZERO),
            executionGateway
        );

        service.sync(CONFIG_PATH, resultOf(
            List.of(
                signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.ONE),
                signal("betfair", "1.2", 43L, BigDecimal.valueOf(2.7), BigDecimal.ONE)
            ),
            List.of(analysis("Team A"), analysis("Team C", "1.2", 43L))
        ));

        assertThat(executionGateway.orders()).hasSize(2);
        assertThat(intents.saved()).hasSize(2);
        assertThat(intents.saved().get(0)).satisfies(intent -> {
            assertThat(intent.availableBalance()).isEqualByComparingTo("17.7");
            assertThat(intent.effectiveAvailableBalance()).isEqualByComparingTo("17.7");
            assertThat(intent.reservedBalance()).isEqualByComparingTo("0");
        });
        assertThat(intents.saved().get(1)).satisfies(intent -> {
            assertThat(intent.availableBalance()).isEqualByComparingTo("17.7");
            assertThat(intent.effectiveAvailableBalance()).isEqualByComparingTo("16.7");
            assertThat(intent.reservedBalance()).isEqualByComparingTo("1");
        });
    }

    @Test
    void automaticBettingBlocksOrderWhenReservedBalanceLeavesInsufficientEffectiveBalance() {
        RecordingIntentRepository intents = new RecordingIntentRepository();
        RecordingExecutionGateway executionGateway = new RecordingExecutionGateway();
        TelegramBetConfirmationService service = service(
            configWithAutoBetting(BigDecimal.valueOf(1), BigDecimal.valueOf(25), 3, true, false),
            new RecordingTelegramConnectionService(),
            new RecordingTelegramGateway(),
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(1.5)),
            StaticExposureGateway.available(0, BigDecimal.ZERO),
            executionGateway
        );

        service.sync(CONFIG_PATH, resultOf(
            List.of(
                signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.ONE),
                signal("betfair", "1.2", 43L, BigDecimal.valueOf(2.7), BigDecimal.ONE)
            ),
            List.of(analysis("Team A"), analysis("Team C", "1.2", 43L))
        ));

        assertThat(executionGateway.orders()).hasSize(1);
        assertThat(intents.saved()).hasSize(2);
        assertThat(intents.saved().get(0).stage()).isEqualTo(BetIntentStage.EXECUTED);
        assertThat(intents.saved().get(1)).satisfies(intent -> {
            assertThat(intent.stage()).isEqualTo(BetIntentStage.FAILED);
            assertThat(intent.resultMessage()).isEqualTo("Effective balance unavailable. Bet blocked for safety.");
            assertThat(intent.effectiveAvailableBalance()).isEqualByComparingTo("0.5");
            assertThat(intent.reservedBalance()).isEqualByComparingTo("1");
        });
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
            StaticExposureGateway.available(0, BigDecimal.ZERO),
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
            StaticExposureGateway.available(1, BigDecimal.ZERO),
            new RecordingExecutionGateway()
        );

        service.sync(CONFIG_PATH, resultOf(signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)), analysis("Team A")));
        telegram.clear();

        service.sync(CONFIG_PATH, resultOf(
            signal("betfair", "1.2", 43L, BigDecimal.valueOf(2.7), BigDecimal.valueOf(5)),
            analysis("Team C", "1.2", 43L)
        ));

        assertThat(intents.saved()).hasSize(2);
        assertThat(telegram.sentMessages()).hasSize(1);
    }

    @Test
    void expiresStalePendingIntentsBeforeApplyingOpenPositionLimit() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        intents.save("data.db", new BetIntent(
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
            BetIntentStage.AWAITING_CONFIRMATION,
            Instant.parse("2026-06-01T09:00:00Z"),
            Instant.parse("2026-06-01T09:00:00Z")
        ));
        TelegramBetConfirmationService service = service(
            configWithRisk(BigDecimal.valueOf(5), BigDecimal.valueOf(25), 1, true),
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            StaticExposureGateway.available(0, BigDecimal.ZERO),
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
                assertThat(intent.stage()).isEqualTo(BetIntentStage.CANCELLED);
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
        intents.save("data.db", new BetIntent(
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
            BetIntentStage.AWAITING_CONFIRMATION,
            Instant.parse("2026-06-01T09:00:00Z"),
            Instant.parse("2026-06-01T09:00:00Z")
        ));
        TelegramBetConfirmationService service = service(
            configWithRisk(BigDecimal.valueOf(5), BigDecimal.valueOf(25), 1, true),
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            StaticExposureGateway.available(0, BigDecimal.ZERO),
            new RecordingExecutionGateway(),
            Clock.fixed(Instant.parse("2026-06-01T10:00:01Z"), ZoneOffset.UTC)
        );

        service.sync(CONFIG_PATH, resultOf());

        assertThat(intents.updated()).singleElement()
            .satisfies(intent -> {
                assertThat(intent.id()).isEqualTo("stale-1");
                assertThat(intent.stage()).isEqualTo(BetIntentStage.CANCELLED);
                assertThat(intent.resultMessage()).isEqualTo("Expired before confirmation.");
            });
    }

    @Test
    void offersConfirmationWhenOnlyExecutedIntentReachedPositionLimit() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        intents.save("data.db", new BetIntent(
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
            BetIntentStage.EXECUTED,
            Instant.parse("2026-06-01T10:00:00Z"),
            Instant.parse("2026-06-01T10:01:00Z")
        ));
        TelegramBetConfirmationService service = service(
            configWithRisk(BigDecimal.valueOf(5), BigDecimal.valueOf(25), 1, true),
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            StaticExposureGateway.available(0, BigDecimal.ZERO),
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
            StaticExposureGateway.available(0, BigDecimal.ZERO),
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

        assertThat(executionGateway.orders()).hasSize(2);
    }

    @Test
    void blocksAutomaticBetWhenExposureIsUnavailable() {
        RecordingExecutionGateway executionGateway = new RecordingExecutionGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        TelegramBetConfirmationService service = service(
            configWithAutoBetting(BigDecimal.valueOf(3), BigDecimal.valueOf(25), 3, true, false),
            new RecordingTelegramConnectionService(),
            new RecordingTelegramGateway(),
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            StaticExposureGateway.unavailable("Betfair API request failed."),
            executionGateway
        );

        service.sync(CONFIG_PATH, resultOf(
            signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)),
            analysis("Team A")
        ));

        assertThat(executionGateway.orders()).isEmpty();
        assertThat(intents.saved()).singleElement().satisfies(intent -> {
            assertThat(intent.source()).isEqualTo(BetIntentSource.AUTOMATIC);
            assertThat(intent.stage()).isEqualTo(BetIntentStage.FAILED);
            assertThat(intent.resultMessage()).isEqualTo("Exposure unavailable. Bet blocked for safety.");
        });
    }

    @Test
    void blocksAutomaticBetWhenRealOpenPositionsReachLimit() {
        RecordingExecutionGateway executionGateway = new RecordingExecutionGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        TelegramBetConfirmationService service = service(
            configWithAutoBetting(BigDecimal.valueOf(3), BigDecimal.valueOf(25), 1, true, false),
            new RecordingTelegramConnectionService(),
            new RecordingTelegramGateway(),
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            StaticExposureGateway.available(1, BigDecimal.ZERO),
            executionGateway
        );

        service.sync(CONFIG_PATH, resultOf(
            signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)),
            analysis("Team A")
        ));

        assertThat(executionGateway.orders()).isEmpty();
        assertThat(intents.saved()).singleElement()
            .satisfies(intent -> assertThat(intent.resultMessage()).isEqualTo("Open position limit reached."));
    }

    @Test
    void maxOpenPositionsCreatesSingleBlockedIntentForExchangeCycle() {
        RecordingExecutionGateway executionGateway = new RecordingExecutionGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        TelegramBetConfirmationService service = service(
            configWithAutoBetting(BigDecimal.valueOf(3), BigDecimal.valueOf(25), 1, true, false),
            new RecordingTelegramConnectionService(),
            new RecordingTelegramGateway(),
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            StaticExposureGateway.available(1, BigDecimal.ZERO),
            executionGateway
        );

        service.sync(CONFIG_PATH, resultOf(
            List.of(
                signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)),
                signal("betfair", "1.2", 43L, BigDecimal.valueOf(2.7), BigDecimal.valueOf(5))
            ),
            List.of(analysis("Team A"), analysis("Team C", "1.2", 43L))
        ));

        assertThat(executionGateway.orders()).isEmpty();
        assertThat(intents.saved()).singleElement()
            .satisfies(intent -> assertThat(intent.resultMessage()).isEqualTo("Open position limit reached."));
    }

    @Test
    void maxOpenPositionsDoesNotCreateRepeatedBlockedIntentWithinDedupeWindow() {
        RecordingExecutionGateway executionGateway = new RecordingExecutionGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        Clock clock = Clock.fixed(Instant.parse("2026-06-17T00:45:00Z"), ZoneOffset.UTC);
        TelegramBetConfirmationService service = service(
            configWithAutoBetting(BigDecimal.valueOf(3), BigDecimal.valueOf(25), 1, true, false),
            new RecordingTelegramConnectionService(),
            new RecordingTelegramGateway(),
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            StaticExposureGateway.available(1, BigDecimal.ZERO),
            executionGateway,
            clock
        );

        service.sync(CONFIG_PATH, resultOf(
            signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)),
            analysis("Team A")
        ));
        service.sync(CONFIG_PATH, resultOf(
            signal("betfair", "1.2", 43L, BigDecimal.valueOf(2.7), BigDecimal.valueOf(5)),
            analysis("Team C", "1.2", 43L)
        ));

        assertThat(executionGateway.orders()).isEmpty();
        assertThat(intents.saved()).singleElement()
            .satisfies(intent -> assertThat(intent.resultMessage()).isEqualTo("Open position limit reached."));
    }

    @Test
    void blocksAutomaticBetWhenRealizedDailyLossReachesLimit() {
        RecordingExecutionGateway executionGateway = new RecordingExecutionGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        TelegramBetConfirmationService service = service(
            configWithAutoBetting(BigDecimal.valueOf(3), BigDecimal.valueOf(5), 3, true, false),
            new RecordingTelegramConnectionService(),
            new RecordingTelegramGateway(),
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            StaticExposureGateway.available(0, BigDecimal.valueOf(-5)),
            executionGateway
        );

        service.sync(CONFIG_PATH, resultOf(
            signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)),
            analysis("Team A")
        ));

        assertThat(executionGateway.orders()).isEmpty();
        assertThat(intents.saved()).singleElement()
            .satisfies(intent -> assertThat(intent.resultMessage()).isEqualTo("Daily realized loss limit reached."));
    }

    @Test
    void savesExternalOrderIdAfterAutomaticBetExecution() {
        RecordingExecutionGateway executionGateway = new RecordingExecutionGateway("bet-123");
        RecordingIntentRepository intents = new RecordingIntentRepository();
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        TelegramBetConfirmationService service = service(
            configWithAutoBetting(BigDecimal.valueOf(3), BigDecimal.valueOf(25), 3, true, false),
            telegram,
            new RecordingTelegramGateway(),
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            StaticExposureGateway.available(0, BigDecimal.ZERO),
            executionGateway
        );

        service.sync(CONFIG_PATH, resultOf(
            signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)),
            analysis("Team A")
        ));

        assertThat(intents.saved()).singleElement()
            .satisfies(intent -> {
                assertThat(intent.source()).isEqualTo(BetIntentSource.AUTOMATIC);
                assertThat(intent.externalOrderId()).isEqualTo("bet-123");
                assertThat(intent.availableBalance()).isEqualByComparingTo("12.5");
            });
        assertThat(telegram.sentMessages()).singleElement()
            .satisfies(message -> assertThat(message.text())
                .contains("REAL BET PLACED")
                .contains("Team A v Team B")
                .contains("Stake: 3")
                .contains("Betfair bet id: bet-123")
                .contains("Balance available: 12.5"));
    }

    @Test
    void blocksAutomaticBetWhenBalanceIsUnavailable() {
        RecordingExecutionGateway executionGateway = new RecordingExecutionGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        TelegramBetConfirmationService service = service(
            configWithAutoBetting(BigDecimal.valueOf(3), BigDecimal.valueOf(25), 3, true, false),
            new RecordingTelegramConnectionService(),
            new RecordingTelegramGateway(),
            intents,
            (config, exchange) -> Optional.empty(),
            StaticExposureGateway.available(0, BigDecimal.ZERO),
            executionGateway
        );

        service.sync(CONFIG_PATH, resultOf(
            signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)),
            analysis("Team A")
        ));

        assertThat(executionGateway.orders()).isEmpty();
        assertThat(intents.saved()).singleElement()
            .satisfies(intent -> {
                assertThat(intent.stage()).isEqualTo(BetIntentStage.FAILED);
                assertThat(intent.resultMessage()).isEqualTo("Balance unavailable. Bet blocked for safety.");
            });
    }

    @Test
    void reconcilesExecutedIntentToSettledWhenBetfairReportsSettlement() {
        RecordingIntentRepository intents = new RecordingIntentRepository();
        intents.save("data.db", new BetIntent(
            "executed-1",
            "betfair",
            "1.1",
            42L,
            "Team A v Team B",
            "Match Odds",
            "Team A",
            "liquidity_ok",
            BigDecimal.valueOf(2.5),
            BigDecimal.valueOf(5),
            null,
            BigDecimal.valueOf(5),
            "accepted",
            "bet-123",
            BetIntentStage.EXECUTED,
            Instant.parse("2026-06-01T10:00:00Z"),
            Instant.parse("2026-06-01T10:01:00Z")
        ));
        RecordingMarketSnapshotRepository snapshots = new RecordingMarketSnapshotRepository();
        TelegramBetConfirmationService service = service(
            new RecordingTelegramConnectionService(),
            new RecordingTelegramGateway(),
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            StaticExposureGateway.availableSettled(new ExchangeSettledOrder(
                "bet-123",
                "1.1",
                42L,
                BetSide.BACK,
                new BigDecimal("13.50"),
                Instant.parse("2026-06-01T18:30:00Z")
            )),
            snapshots,
            new RecordingExecutionGateway()
        );

        service.sync(CONFIG_PATH, resultOf());

        assertThat(intents.updated()).singleElement().satisfies(intent -> {
            assertThat(intent.id()).isEqualTo("executed-1");
            assertThat(intent.stage()).isEqualTo(BetIntentStage.SETTLED);
            assertThat(intent.externalOrderId()).isEqualTo("bet-123");
            assertThat(intent.resultMessage()).isEqualTo("Settled on exchange.");
            assertThat(intent.settlementResult()).isEqualTo(BetSettlementResult.WIN);
            assertThat(intent.realizedProfitLoss()).isEqualByComparingTo("13.50");
            assertThat(intent.settledAt()).isEqualTo(Instant.parse("2026-06-01T18:30:00Z"));
        });
        assertThat(snapshots.deletedMarkets()).containsExactly("./data/betx.db|betfair|1.1");
    }

    @Test
    void leavesExecutedIntentOpenWhenBetfairDoesNotReportItsSettlement() {
        RecordingIntentRepository intents = new RecordingIntentRepository();
        intents.save("data.db", new BetIntent(
            "executed-1",
            "betfair",
            "1.1",
            42L,
            "Team A v Team B",
            "Match Odds",
            "Team A",
            "liquidity_ok",
            BigDecimal.valueOf(2.5),
            BigDecimal.valueOf(5),
            null,
            BigDecimal.valueOf(5),
            "accepted",
            "bet-123",
            BetIntentStage.EXECUTED,
            Instant.parse("2026-06-01T10:00:00Z"),
            Instant.parse("2026-06-01T10:01:00Z")
        ));
        TelegramBetConfirmationService service = service(
            new RecordingTelegramConnectionService(),
            new RecordingTelegramGateway(),
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            StaticExposureGateway.availableSettled(new ExchangeSettledOrder(
                "different-bet",
                "1.1",
                42L,
                BetSide.BACK,
                new BigDecimal("13.50"),
                Instant.parse("2026-06-01T18:30:00Z")
            )),
            new RecordingMarketSnapshotRepository(),
            new RecordingExecutionGateway()
        );

        service.sync(CONFIG_PATH, resultOf());

        assertThat(intents.updated()).isEmpty();
    }

    @Test
    void linksSignalHistoryWhenCreatingConfirmationIntent() {
        RecordingIntentRepository intents = new RecordingIntentRepository();
        RecordingSignalHistoryRepository history = new RecordingSignalHistoryRepository();
        TelegramBetConfirmationService service = service(
            new RecordingTelegramConnectionService(),
            new RecordingTelegramGateway(),
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            StaticExposureGateway.available(0, BigDecimal.ZERO),
            new RecordingMarketSnapshotRepository(),
            history,
            new RecordingExecutionGateway()
        );

        service.sync(CONFIG_PATH, resultWithHistory(signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)), analysis("Team A")));

        assertThat(history.linked()).singleElement()
            .satisfies(link -> {
                assertThat(link.key()).isEqualTo(historyEntry().key());
                assertThat(link.intent().id()).isEqualTo(intents.saved().getFirst().id());
                assertThat(link.intent().source()).isEqualTo(BetIntentSource.TELEGRAM_CONFIRMATION);
                assertThat(link.intent().stage()).isEqualTo(BetIntentStage.AWAITING_CONFIRMATION);
            });
    }

    @Test
    void updatesSignalHistoryWhenTelegramOrderExecutes() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        RecordingSignalHistoryRepository history = new RecordingSignalHistoryRepository();
        TelegramBetConfirmationService service = service(
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            StaticExposureGateway.available(0, BigDecimal.ZERO),
            new RecordingMarketSnapshotRepository(),
            history,
            new RecordingExecutionGateway("bet-123")
        );
        service.sync(CONFIG_PATH, resultWithHistory(signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)), analysis("Team A")));
        String intentId = intents.saved().getFirst().id();
        gateway.addUpdate(newCallbackUpdate(1L, intentId, "yes", 77));
        service.sync(CONFIG_PATH, resultOf());
        gateway.addUpdate(newCallbackUpdate(2L, intentId, "stake", 77, BigDecimal.valueOf(5)));

        service.sync(CONFIG_PATH, resultOf());

        assertThat(history.updated()).last()
            .satisfies(intent -> {
                assertThat(intent.stage()).isEqualTo(BetIntentStage.EXECUTED);
                assertThat(intent.selectedStake()).isEqualByComparingTo("5");
                assertThat(intent.externalOrderId()).isEqualTo("bet-123");
                assertThat(intent.resultMessage()).isEqualTo("accepted");
            });
    }

    @Test
    void updatesSignalHistoryWhenAutomaticBetIsBlockedAndWhenSettled() {
        RecordingIntentRepository intents = new RecordingIntentRepository();
        RecordingSignalHistoryRepository history = new RecordingSignalHistoryRepository();
        TelegramBetConfirmationService blockedService = service(
            configWithAutoBetting(BigDecimal.valueOf(3), BigDecimal.valueOf(25), 3, true, false),
            new RecordingTelegramConnectionService(),
            new RecordingTelegramGateway(),
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            StaticExposureGateway.unavailable("Betfair API request failed."),
            new RecordingMarketSnapshotRepository(),
            history,
            new RecordingExecutionGateway()
        );

        blockedService.sync(CONFIG_PATH, resultWithHistory(
            signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)),
            analysis("Team A")
        ));

        assertThat(history.linked()).singleElement()
            .satisfies(link -> {
                assertThat(link.intent().source()).isEqualTo(BetIntentSource.AUTOMATIC);
                assertThat(link.intent().stage()).isEqualTo(BetIntentStage.FAILED);
            });
        assertThat(history.updated()).singleElement()
            .satisfies(intent -> assertThat(intent.resultMessage()).isEqualTo("Exposure unavailable. Bet blocked for safety."));

        intents.save("data.db", new BetIntent(
            "executed-1",
            "betfair",
            "1.2",
            43L,
            "Team C v Team D",
            "Match Odds",
            "Team C",
            "liquidity_ok",
            BigDecimal.valueOf(2.7),
            BigDecimal.valueOf(5),
            null,
            BigDecimal.valueOf(5),
            "accepted",
            "bet-456",
            BetIntentStage.EXECUTED,
            Instant.parse("2026-06-01T10:00:00Z"),
            Instant.parse("2026-06-01T10:01:00Z")
        ));
        TelegramBetConfirmationService settledService = service(
            new RecordingTelegramConnectionService(),
            new RecordingTelegramGateway(),
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            StaticExposureGateway.availableSettled(new ExchangeSettledOrder(
                "bet-456",
                "1.2",
                43L,
                BetSide.BACK,
                new BigDecimal("-5.00"),
                Instant.parse("2026-06-01T18:30:00Z")
            )),
            new RecordingMarketSnapshotRepository(),
            history,
            new RecordingExecutionGateway()
        );

        settledService.sync(CONFIG_PATH, resultOf());

        assertThat(history.updated()).last()
            .satisfies(intent -> {
                assertThat(intent.id()).isEqualTo("executed-1");
                assertThat(intent.stage()).isEqualTo(BetIntentStage.SETTLED);
                assertThat(intent.resultMessage()).isEqualTo("Settled on exchange.");
                assertThat(intent.settlementResult()).isEqualTo(BetSettlementResult.LOSE);
                assertThat(intent.realizedProfitLoss()).isEqualByComparingTo("-5.00");
            });
    }

    private TelegramBetConfirmationService service(
        RecordingTelegramConnectionService telegram,
        RecordingTelegramGateway gateway,
        RecordingIntentRepository intents,
        RecordingTelegramStateRepository stateRepository,
        ExchangeAccountGateway accountGateway,
        ExchangeExposureGateway exposureGateway,
        BetExecutionGateway executionGateway
    ) {
        return service(
            configWithAutoBetting(BigDecimal.valueOf(5), BigDecimal.valueOf(25), 3, true, true),
            telegram,
            gateway,
            intents,
            stateRepository,
            accountGateway,
            exposureGateway,
            new RecordingMarketSnapshotRepository(),
            new RecordingSignalHistoryRepository(),
            executionGateway,
            Clock.systemUTC()
        );
    }

    private TelegramBetConfirmationService service(
        RecordingTelegramConnectionService telegram,
        RecordingTelegramGateway gateway,
        RecordingIntentRepository intents,
        ExchangeAccountGateway accountGateway,
        ExchangeExposureGateway exposureGateway,
        BetExecutionGateway executionGateway
    ) {
        return service(
            telegram,
            gateway,
            intents,
            accountGateway,
            exposureGateway,
            new RecordingMarketSnapshotRepository(),
            executionGateway
        );
    }

    private TelegramBetConfirmationService service(
        RecordingTelegramConnectionService telegram,
        RecordingTelegramGateway gateway,
        RecordingIntentRepository intents,
        ExchangeAccountGateway accountGateway,
        ExchangeExposureGateway exposureGateway,
        MarketSnapshotRepository snapshotRepository,
        BetExecutionGateway executionGateway
    ) {
        return service(
            configWithAutoBetting(BigDecimal.valueOf(5), BigDecimal.valueOf(25), 3, true, true),
            telegram,
            gateway,
            intents,
            accountGateway,
            exposureGateway,
            snapshotRepository,
            new RecordingSignalHistoryRepository(),
            executionGateway
        );
    }

    private TelegramBetConfirmationService service(
        BetxConfig config,
        RecordingTelegramConnectionService telegram,
        RecordingTelegramGateway gateway,
        RecordingIntentRepository intents,
        ExchangeAccountGateway accountGateway,
        ExchangeExposureGateway exposureGateway,
        BetExecutionGateway executionGateway
    ) {
        return service(config, telegram, gateway, intents, accountGateway, exposureGateway, executionGateway, Clock.systemUTC());
    }

    private TelegramBetConfirmationService service(
        RecordingTelegramConnectionService telegram,
        RecordingTelegramGateway gateway,
        RecordingIntentRepository intents,
        ExchangeAccountGateway accountGateway,
        ExchangeExposureGateway exposureGateway,
        MarketSnapshotRepository snapshotRepository,
        SignalHistoryRepository historyRepository,
        BetExecutionGateway executionGateway
    ) {
        return service(
            configWithAutoBetting(BigDecimal.valueOf(5), BigDecimal.valueOf(25), 3, true, true),
            telegram,
            gateway,
            intents,
            accountGateway,
            exposureGateway,
            snapshotRepository,
            historyRepository,
            executionGateway,
            Clock.systemUTC()
        );
    }

    private TelegramBetConfirmationService service(
        BetxConfig config,
        RecordingTelegramConnectionService telegram,
        RecordingTelegramGateway gateway,
        RecordingIntentRepository intents,
        ExchangeAccountGateway accountGateway,
        ExchangeExposureGateway exposureGateway,
        BetExecutionGateway executionGateway,
        Clock clock
    ) {
        return service(
            config,
            telegram,
            gateway,
            intents,
            accountGateway,
            exposureGateway,
            new RecordingMarketSnapshotRepository(),
            new RecordingSignalHistoryRepository(),
            executionGateway,
            clock
        );
    }

    private TelegramBetConfirmationService service(
        BetxConfig config,
        RecordingTelegramConnectionService telegram,
        RecordingTelegramGateway gateway,
        RecordingIntentRepository intents,
        RecordingTelegramStateRepository stateRepository,
        ExchangeAccountGateway accountGateway,
        ExchangeExposureGateway exposureGateway,
        MarketSnapshotRepository snapshotRepository,
        SignalHistoryRepository historyRepository,
        BetExecutionGateway executionGateway,
        Clock clock
    ) {
        return new TelegramBetConfirmationService(
            new StaticConfigRepository(config),
            telegram,
            gateway,
            intents,
            stateRepository,
            accountGateway,
            exposureGateway,
            snapshotRepository,
            historyRepository,
            executionGateway,
            clock
        );
    }

    private TelegramBetConfirmationService service(
        BetxConfig config,
        RecordingTelegramConnectionService telegram,
        RecordingTelegramGateway gateway,
        RecordingIntentRepository intents,
        ExchangeAccountGateway accountGateway,
        ExchangeExposureGateway exposureGateway,
        MarketSnapshotRepository snapshotRepository,
        BetExecutionGateway executionGateway,
        Clock clock
    ) {
        return service(
            config,
            telegram,
            gateway,
            intents,
            accountGateway,
            exposureGateway,
            snapshotRepository,
            new RecordingSignalHistoryRepository(),
            executionGateway,
            clock
        );
    }

    private TelegramBetConfirmationService service(
        BetxConfig config,
        RecordingTelegramConnectionService telegram,
        RecordingTelegramGateway gateway,
        RecordingIntentRepository intents,
        ExchangeAccountGateway accountGateway,
        ExchangeExposureGateway exposureGateway,
        MarketSnapshotRepository snapshotRepository,
        SignalHistoryRepository historyRepository,
        BetExecutionGateway executionGateway
    ) {
        return service(
            config,
            telegram,
            gateway,
            intents,
            accountGateway,
            exposureGateway,
            snapshotRepository,
            historyRepository,
            executionGateway,
            Clock.systemUTC()
        );
    }

    private TelegramBetConfirmationService service(
        BetxConfig config,
        RecordingTelegramConnectionService telegram,
        RecordingTelegramGateway gateway,
        RecordingIntentRepository intents,
        ExchangeAccountGateway accountGateway,
        ExchangeExposureGateway exposureGateway,
        MarketSnapshotRepository snapshotRepository,
        SignalHistoryRepository historyRepository,
        BetExecutionGateway executionGateway,
        Clock clock
    ) {
        return new TelegramBetConfirmationService(
            new StaticConfigRepository(config),
            telegram,
            gateway,
            intents,
            accountGateway,
            exposureGateway,
            snapshotRepository,
            historyRepository,
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

    private DryRunSignalsResult resultOf(List<BetSignal> signals, List<RunnerAnalysis> analyses) {
        return new DryRunSignalsResult(
            signals,
            List.of(),
            false,
            0,
            0,
            List.of(),
            analyses,
            0,
            0,
            0,
            0
        );
    }

    private DryRunSignalsResult resultWithHistory(BetSignal signal, RunnerAnalysis analysis) {
        return new DryRunSignalsResult(
            List.of(signal),
            List.of(),
            false,
            0,
            0,
            List.of(),
            List.of(analysis),
            List.of(),
            List.of(historyEntry()),
            0,
            0,
            0,
            0
        );
    }

    private SignalHistoryEntry historyEntry() {
        return new SignalHistoryEntry(
            Instant.parse("2026-05-31T10:01:00Z"),
            "betfair",
            "1.1",
            42L,
            "Team A v Team B",
            "Match Odds",
            "Team A",
            "La Liga",
            Instant.parse("2026-06-01T18:00:00Z"),
            RecommendationType.BET,
            85,
            "High confidence",
            "liquidity_ok",
            BigDecimal.valueOf(2.5),
            BigDecimal.valueOf(2.6),
            BigDecimal.valueOf(0.04),
            BigDecimal.valueOf(1_200),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
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

    private static final class RecordingTelegramStateRepository implements TelegramStateRepository {
        private long lastProcessedUpdateId;

        @Override
        public long loadLastProcessedUpdateId(String databasePath) {
            return lastProcessedUpdateId;
        }

        @Override
        public void saveLastProcessedUpdateId(String databasePath, long updateId) {
            lastProcessedUpdateId = updateId;
        }

        private long lastProcessedUpdateId() {
            return lastProcessedUpdateId;
        }
    }

    private record SentMessage(String text, Map<String, Object> replyMarkup) {
    }

    private record EditedMessage(Integer messageId, String text, Map<String, Object> replyMarkup) {
    }

    private static final class RecordingIntentRepository implements BetIntentRepository {
        private final List<BetIntent> saved = new ArrayList<>();
        private final List<BetIntent> updated = new ArrayList<>();

        @Override
        public Optional<BetIntent> findActiveByKey(String databasePath, String exchange, String marketId, long selectionId) {
            return saved.stream()
                .filter(intent -> intent.exchange().equals(exchange)
                    && intent.marketId().equals(marketId)
                    && intent.selectionId() == selectionId
                    && intent.stage().isActive())
                .findFirst();
        }

        @Override
        public Optional<BetIntent> findActiveByMarket(String databasePath, String exchange, String marketId) {
            return saved.stream()
                .filter(intent -> intent.exchange().equals(exchange)
                    && intent.marketId().equals(marketId)
                    && intent.stage().isActive())
                .findFirst();
        }

        @Override
        public Optional<BetIntent> findLatestByKeySince(
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
        public Optional<BetIntent> findLatestByMarketSince(
            String databasePath,
            String exchange,
            String marketId,
            Instant since
        ) {
            return saved.stream()
                .filter(intent -> intent.exchange().equals(exchange)
                    && intent.marketId().equals(marketId)
                    && !intent.updatedAt().isBefore(since))
                .findFirst();
        }

        @Override
        public Optional<BetIntent> findLatestByExchangeResultSince(
            String databasePath,
            String exchange,
            String resultMessage,
            Instant since
        ) {
            return saved.stream()
                .filter(intent -> intent.exchange().equals(exchange)
                    && resultMessage.equals(intent.resultMessage())
                    && !intent.updatedAt().isBefore(since))
                .findFirst();
        }

        @Override
        public Optional<BetIntent> findById(String databasePath, String id) {
            return saved.stream().filter(intent -> intent.id().equals(id)).findFirst();
        }

        @Override
        public List<BetIntent> listRecent(String databasePath, int limit) {
            return saved.stream()
                .limit(limit)
                .toList();
        }

        @Override
        public List<BetIntent> listByStages(String databasePath, List<BetIntentStage> stages, int limit) {
            return saved.stream()
                .filter(intent -> stages.contains(intent.stage()))
                .limit(limit)
                .toList();
        }

        @Override
        public long countByStages(String databasePath, List<BetIntentStage> stages) {
            return saved.stream()
                .filter(intent -> stages.contains(intent.stage()))
                .count();
        }

        @Override
        public BigDecimal sumSelectedStakeByStageSince(String databasePath, BetIntentStage stage, Instant since) {
            return saved.stream()
                .filter(intent -> intent.stage() == stage)
                .filter(intent -> !intent.updatedAt().isBefore(since))
                .map(BetIntent::selectedStake)
                .filter(stake -> stake != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        @Override
        public void save(String databasePath, BetIntent intent) {
            saved.add(intent);
        }

        @Override
        public void update(String databasePath, BetIntent intent) {
            updated.add(intent);
            for (int index = 0; index < saved.size(); index++) {
                if (saved.get(index).id().equals(intent.id())) {
                    saved.set(index, intent);
                    return;
                }
            }
            saved.add(intent);
        }

        List<BetIntent> saved() {
            return saved;
        }

        List<BetIntent> updated() {
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

    private static final class CountingAccountGateway implements ExchangeAccountGateway {
        private final BigDecimal balance;
        private int calls;

        private CountingAccountGateway(BigDecimal balance) {
            this.balance = balance;
        }

        @Override
        public Optional<BigDecimal> availableBalance(BetxConfig config, String exchange) {
            calls++;
            return Optional.of(balance);
        }

        int calls() {
            return calls;
        }
    }

    private static final class RecordingMarketSnapshotRepository implements MarketSnapshotRepository {
        private final List<String> deletedMarkets = new ArrayList<>();

        @Override
        public Optional<ObservedMarketSnapshot> findLatest(String databasePath, String exchange, String marketId, long selectionId) {
            return Optional.empty();
        }

        @Override
        public void save(String databasePath, ObservedMarketSnapshot snapshot) {
        }

        @Override
        public int deleteMarket(String databasePath, String exchange, String marketId) {
            deletedMarkets.add(databasePath + "|" + exchange + "|" + marketId);
            return 0;
        }

        List<String> deletedMarkets() {
            return deletedMarkets;
        }
    }

    private static final class RecordingSignalHistoryRepository implements SignalHistoryRepository {
        private final List<HistoryLink> linked = new ArrayList<>();
        private final List<BetIntent> updated = new ArrayList<>();

        @Override
        public void saveDecision(String databasePath, SignalHistoryEntry entry) {
        }

        @Override
        public void linkIntent(String databasePath, SignalHistoryKey key, BetIntent intent) {
            linked.add(new HistoryLink(key, intent));
        }

        @Override
        public void updateOrderState(String databasePath, BetIntent intent) {
            updated.add(intent);
        }

        List<HistoryLink> linked() {
            return linked;
        }

        List<BetIntent> updated() {
            return updated;
        }
    }

    private record HistoryLink(SignalHistoryKey key, BetIntent intent) {
    }

    private record StaticExposureGateway(ExchangeExposure exposure) implements ExchangeExposureGateway {
        static StaticExposureGateway available(int openPositions, BigDecimal realizedProfitLoss) {
            return available(openPositions, realizedProfitLoss, Set.of());
        }

        static StaticExposureGateway available(int openPositions, BigDecimal realizedProfitLoss, Set<String> settledExternalOrderIds) {
            return new StaticExposureGateway(new ExchangeExposure(
                true,
                openPositions,
                BigDecimal.ZERO,
                realizedProfitLoss,
                List.of(),
                settledExternalOrderIds,
                null
            ));
        }

        static StaticExposureGateway availableSettled(ExchangeSettledOrder... settledOrders) {
            List<ExchangeSettledOrder> orders = List.of(settledOrders);
            BigDecimal realizedProfitLoss = orders.stream()
                .map(ExchangeSettledOrder::realizedProfitLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new StaticExposureGateway(new ExchangeExposure(
                true,
                0,
                BigDecimal.ZERO,
                realizedProfitLoss,
                List.of(),
                orders,
                null
            ));
        }

        static StaticExposureGateway unavailable(String message) {
            return new StaticExposureGateway(ExchangeExposure.unavailable(message));
        }

        @Override
        public ExchangeExposure exposure(BetxConfig config, String exchange, Instant settledSince) {
            return exposure;
        }
    }

    private static final class CountingExposureGateway implements ExchangeExposureGateway {
        private final ExchangeExposure exposure;
        private int calls;

        private CountingExposureGateway(ExchangeExposure exposure) {
            this.exposure = exposure;
        }

        @Override
        public ExchangeExposure exposure(BetxConfig config, String exchange, Instant settledSince) {
            calls++;
            return exposure;
        }

        int calls() {
            return calls;
        }
    }

    private static final class RecordingExecutionGateway implements BetExecutionGateway {
        private final List<com.betx.domain.order.BetOrder> orders = new ArrayList<>();
        private final String externalOrderId;

        private RecordingExecutionGateway() {
            this(null);
        }

        private RecordingExecutionGateway(String externalOrderId) {
            this.externalOrderId = externalOrderId;
        }

        @Override
        public com.betx.domain.order.BetExecutionResult execute(com.betx.domain.order.BetOrder order) {
            orders.add(order);
            return new com.betx.domain.order.BetExecutionResult(true, "accepted", externalOrderId);
        }

        List<com.betx.domain.order.BetOrder> orders() {
            return orders;
        }
    }
}
