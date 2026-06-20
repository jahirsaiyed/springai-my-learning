package com.example.springaisample.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WeatherTool {

    private static final Map<String, String> MOCK_WEATHER = Map.of(
            "dubai", "45C, Sunny",
            "london", "18C, Cloudy",
            "new york", "28C, Partly Cloudy",
            "tokyo", "32C, Humid",
            "paris", "22C, Clear"
    );

    @Tool(description = "Get the current weather for a given city")
    public String getWeather(@ToolParam(description = "The city name") String city) {
        String weather = MOCK_WEATHER.get(city.toLowerCase());
        if (weather == null) {
            return "Weather data not available for " + city;
        }
        return "Weather in " + city + ": " + weather;
    }
}
