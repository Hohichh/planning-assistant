package io.hohichh.planning_assistant.config;

import io.hohichh.planning_assistant.web.AgentRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AgentRateLimitInterceptor agentRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(agentRateLimitInterceptor)
                .addPathPatterns("/api/v1/users/*/agent/process");
    }
}
