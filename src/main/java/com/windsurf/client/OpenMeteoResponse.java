package com.windsurf.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenMeteoResponse(Current current) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Current(
        @JsonProperty("wind_speed_10m")    double windSpeed,
        @JsonProperty("wind_direction_10m") double windDirection,
        @JsonProperty("wind_gusts_10m")    double windGusts
    ) {}
}
