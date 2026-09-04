package com.windsurf.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "open-meteo")
@Path("/v1/forecast")
public interface OpenMeteoClient {

    @GET
    OpenMeteoResponse getCurrent(
        @QueryParam("latitude") double latitude,
        @QueryParam("longitude") double longitude,
        @QueryParam("current_weather") boolean currentWeather
    );
}
