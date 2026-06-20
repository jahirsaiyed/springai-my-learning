package com.example.springaisample.config;

import com.example.springaisample.tools.CalculatorTool;
import com.example.springaisample.tools.WeatherTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 WeatherTool weatherTool,
                                 CalculatorTool calculatorTool) {
        return builder
                .defaultSystem("""
                        You are a helpful assistant with access to tools.
                        When the user asks a question that requires real-time data or computation,
                        use the available tools to find the answer.
                        Always explain your reasoning step by step.
                        """)
                .defaultTools(weatherTool, calculatorTool)
                .build();
    }
}
