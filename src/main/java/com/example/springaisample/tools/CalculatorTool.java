package com.example.springaisample.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class CalculatorTool {

    @Tool(description = "Add two numbers together")
    public double add(
            @ToolParam(description = "First number") double a,
            @ToolParam(description = "Second number") double b) {
        return a + b;
    }

    @Tool(description = "Multiply two numbers together")
    public double multiply(
            @ToolParam(description = "First number") double a,
            @ToolParam(description = "Second number") double b) {
        return a * b;
    }
}
