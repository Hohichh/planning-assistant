package io.hohichh.planning_assistant.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = OpenRouterChatClientIntegrationTest.TestApp.class,
        webEnvironment = WebEnvironment.NONE
)
class OpenRouterChatClientIntegrationTest {

    private static final WireMockServer WIRE_MOCK_SERVER = new WireMockServer(0);

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @BeforeAll
    static void beforeAll() {
        WIRE_MOCK_SERVER.start();
    }

    @AfterAll
    static void afterAll() {
        WIRE_MOCK_SERVER.stop();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.api-key", () -> "test-openrouter-key");
        registry.add("spring.ai.openai.base-url", () -> WIRE_MOCK_SERVER.baseUrl() + "/v1");
        registry.add("spring.ai.openai.chat.options.model", () -> "deepseek/deepseek-v4-flash:free");
    }

    @BeforeEach
    void setUpStub() {
        WIRE_MOCK_SERVER.resetAll();
        WIRE_MOCK_SERVER.stubFor(post(urlPathMatching(".*/chat/completions"))
                .willReturn(okJson("""
                        {
                          "id": "chatcmpl-test",
                          "object": "chat.completion",
                          "created": 1710000000,
                          "model": "deepseek/deepseek-v4-flash:free",
                          "choices": [
                            {
                              "index": 0,
                              "message": {
                                "role": "assistant",
                                "content": "ok-from-openrouter-compatible-endpoint"
                              },
                              "finish_reason": "stop"
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 10,
                            "completion_tokens": 20,
                            "total_tokens": 30
                          }
                        }
                        """)));
    }

    @Test
    void chatClient_shouldCallOpenAiCompatibleEndpoint() {
        String response = chatClientBuilder.build()
                .prompt()
                .user("Составь подзадачи")
                .call()
                .content();

        assertTrue(response.contains("openrouter-compatible-endpoint"));

        WIRE_MOCK_SERVER.verify(postRequestedFor(urlPathMatching(".*/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer test-openrouter-key"))
                .withRequestBody(matching("(?s).*deepseek/deepseek-v4-flash:free.*")));
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({
            ToolCallingAutoConfiguration.class,
            OpenAiChatAutoConfiguration.class,
            ChatClientAutoConfiguration.class
    })
    static class TestApp {

        @Bean
        String markerBean() {
            return "test";
        }
    }
}
