package io.hohichh.planning_assistant.config;

import io.hohichh.planning_assistant.ai.LocalChatMemory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiMemoryConfig {

    @Bean
    public ChatMemory chatMemory() {
        return new LocalChatMemory(20);
    }
}
