package com.betx.adapter.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.betx.application.port.out.TelegramParseMode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestTelegramBotClientTest {
    @Test
    void resolvesBotUsernameFromGetMe() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestTelegramBotClient client = new RestTelegramBotClient(builder);
        server.expect(requestTo("https://api.telegram.org/bottoken/getMe"))
            .andRespond(withSuccess("{\"ok\":true,\"result\":{\"username\":\"betx_bot\"}}", APPLICATION_JSON));

        assertThat(client.getBotUsername("token")).isEqualTo("betx_bot");
        server.verify();
    }

    @Test
    void parsesRelevantUpdatesAndSkipsInvalidOnes() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestTelegramBotClient client = new RestTelegramBotClient(builder);
        server.expect(requestTo("https://api.telegram.org/bottoken/getUpdates?timeout=10&offset=11"))
            .andRespond(withSuccess("""
                {
                  "ok": true,
                  "result": [
                    {"update_id": 11, "message": {"chat": {"id": 12345}, "text": "/start abc", "from": {"username": "user", "first_name": "Jose"}}},
                    {"update_id": 12, "edited_message": {"text": "ignored"}}
                  ]
                }
                """, APPLICATION_JSON));

        var updates = client.getUpdates("token", 11L, 10);

        assertThat(updates).singleElement().satisfies(update -> {
            assertThat(update.updateId()).isEqualTo(11L);
            assertThat(update.chatId()).isEqualTo("12345");
            assertThat(update.startPayload()).isEqualTo("abc");
            assertThat(update.username()).isEqualTo("user");
            assertThat(update.firstName()).isEqualTo("Jose");
        });
        server.verify();
    }

    @Test
    void parsesCallbackQueryUpdates() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestTelegramBotClient client = new RestTelegramBotClient(builder);
        server.expect(requestTo("https://api.telegram.org/bottoken/getUpdates?timeout=10&offset=21"))
            .andRespond(withSuccess("""
                {
                  "ok": true,
                  "result": [
                    {
                      "update_id": 21,
                      "callback_query": {
                        "id": "callback-1",
                        "data": "bet:abc:yes",
                        "message": {
                          "message_id": 77,
                          "chat": {"id": 12345}
                        },
                        "from": {"username": "user", "first_name": "Jose"}
                      }
                    }
                  ]
                }
                """, APPLICATION_JSON));

        var updates = client.getUpdates("token", 21L, 10);

        assertThat(updates).singleElement().satisfies(update -> {
            assertThat(update.updateId()).isEqualTo(21L);
            assertThat(update.chatId()).isEqualTo("12345");
            assertThat(update.callbackQueryId()).isEqualTo("callback-1");
            assertThat(update.callbackData()).isEqualTo("bet:abc:yes");
            assertThat(update.messageId()).isEqualTo(77);
            assertThat(update.username()).isEqualTo("user");
            assertThat(update.firstName()).isEqualTo("Jose");
        });
        server.verify();
    }

    @Test
    void sendsMessageAndUnwrapsOkResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestTelegramBotClient client = new RestTelegramBotClient(builder);
        server.expect(requestTo("https://api.telegram.org/bottoken/sendMessage"))
            .andExpect(content().json("{\"chat_id\":\"12345\",\"text\":\"hello\"}", true))
            .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":1}}", APPLICATION_JSON));

        client.sendMessage("token", "12345", "hello");

        server.verify();
    }

    @Test
    void sendsHtmlMessageWithParseMode() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestTelegramBotClient client = new RestTelegramBotClient(builder);
        server.expect(requestTo("https://api.telegram.org/bottoken/sendMessage"))
            .andExpect(content().json("{\"chat_id\":\"12345\",\"text\":\"<b>hello</b>\",\"parse_mode\":\"HTML\"}", true))
            .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":1}}", APPLICATION_JSON));

        client.sendMessage("token", "12345", "<b>hello</b>", TelegramParseMode.HTML);

        server.verify();
    }

    @Test
    void failsWithTelegramDescriptionWhenResponseIsNotOk() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestTelegramBotClient client = new RestTelegramBotClient(builder);
        server.expect(requestTo("https://api.telegram.org/bottoken/getMe"))
            .andRespond(withSuccess("{\"ok\":false,\"description\":\"Unauthorized\"}", APPLICATION_JSON));

        assertThatThrownBy(() -> client.getBotUsername("token"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Unauthorized");
        server.verify();
    }
}
