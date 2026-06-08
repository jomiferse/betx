package com.betx.adapter.config;

public final class DefaultConfigTemplates {
    private DefaultConfigTemplates() {
    }

    public static String defaultConfig() {
        return """
            app:
              log_level: info

            telegram:
              enabled: true
              bot_token:
              bot_username:
              chat_id:
              connected_at:
              username:
              first_name:
              pending_link_code:

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
                    max_daily_loss: 25
                    max_open_positions: 3

            storage:
              type: sqlite
              path: ./data/betx.db

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
            """;
    }
}
