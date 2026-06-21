package com.example.ecommercemcp.config;

import com.example.ecommercemcp.tools.*;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.example.ecommerce.service")
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(
            McpProductTools productTools,
            McpOrderTools orderTools,
            McpRefundTools refundTools,
            McpCustomerTools customerTools,
            McpReviewTools reviewTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(productTools, orderTools, refundTools, customerTools, reviewTools)
                .build();
    }
}
