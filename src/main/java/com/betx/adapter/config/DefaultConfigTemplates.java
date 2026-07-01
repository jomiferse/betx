package com.betx.adapter.config;

public final class DefaultConfigTemplates {
    private DefaultConfigTemplates() {
    }

    public static String defaultConfig() {
        return """
            app:
              log_level: info
              structured_logs:
                enabled: true
                directory: ./logs/events
                retention_days: 30

            telegram:
              enabled: true
              bot_token:
              bot_username:
              chat_id:
              connected_at:
              username:
              first_name:
              pending_link_code:
              alerts:
                # key_events sends real order, settlement, and critical risk events by default.
                # Use all_signals only while diagnosing strategy behavior.
                mode: key_events
                signal_dedupe_ttl: 30m

            resilience:
              betfair:
                failure_threshold: 3
                cooldown: 5m
              telegram:
                failure_threshold: 3
                cooldown: 5m
              openrouter:
                failure_threshold: 3
                cooldown: 5m

            execution:
              queue:
                enabled: true
                max_pending_per_exchange: 20
                order_ttl: 10s
                stale_balance_ttl: 5s
                revalidate_odds_after: 3s
                min_effective_balance: 0.01

            staking:
              # Live stake sizing remains disabled. Shadow mode records what policies would have recommended.
              enabled: false
              shadow_enabled: true
              mode: FLAT
              base_stake: 1.00
              min_stake: 1.00
              max_stake: 10.00
              bankroll: 500.00
              risk_profile: CONSERVATIVE
              limits:
                max_daily_loss: 25.00
                max_total_exposure: 50.00
                max_market_exposure: 5.00
                max_open_positions: 10
              shadow:
                enabled: true
                policies:
                  - FLAT
                  - RISK_ADJUSTED
                  - TIERED_CONFIDENCE
                  - FRACTIONAL_KELLY_SHADOW
                risk_profiles:
                  - CONSERVATIVE
                  - BALANCED

            exchanges:
              - name: betfair
                enabled: false
                betfair:
                  # Available countries: global, australia_new_zealand, italy, spain, romania
                  country: spain
                  username:
                  password:
                  app_key:
                  auto_betting:
                    enabled: false
                    request_confirmation: true
                    max_stake: 5
                    # Daily realized/liquidated loss limit read from Betfair settlements.
                    max_daily_loss: 25
                    # Real open positions read from Betfair, including manual external bets.
                    max_open_positions: 3

            storage:
              type: sqlite
              path: ./data/betx.db
              cleanup_market_snapshots_enabled: true
              market_snapshot_retention_hours: 48
              paper_evaluations:
                detail_retention_days: 7
                rejection_sample_rate: 0.0

            paper:
              continuous: false
              poll_interval: 60s
              closing_capture_minutes_before_start: 2
              settlement_poll_interval: 5m
              readiness_gate:
                enabled: true
                minimum_settled_trades: 100
                required_evidence_status: CANDIDATE_EDGE
                minimum_executable_roi: 0.01
                minimum_median_clv: 0.00
                rolling_window_size: 100
                minimum_rolling_roi: 0.00
                block_on_execution_failure: true

            market_data:
              poll_interval_seconds: 60
              scan_all_markets: true
              max_markets: 0
              betfair_event_batch_size: 50
              event_type_ids:
                - "1"
              market_type_codes:
                - MATCH_ODDS

            strategies:
              - name: value-football
                enabled: true
                min_edge: 0.06
                min_liquidity: 500

            ml:
              enabled: false
              model_path: ./models/value_model.pkl
              min_confidence: 0.70

            intelligence:
              enabled: false
              provider: openrouter
              model: x-ai/grok-4.3
              api_key:
              timeout_seconds: 20
              min_confidence: 70
              auto_betting_policy: strict_approve
            """;
    }
}
