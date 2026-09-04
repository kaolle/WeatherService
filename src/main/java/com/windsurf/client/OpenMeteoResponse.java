package com.windsurf.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenMeteoResponse(@JsonProperty("current_weather") CurrentWeather currentWeather) {

    public record CurrentWeather(
        double windspeed,      // km/h
        double winddirection   // degrees 0-360
    ) {}
}
