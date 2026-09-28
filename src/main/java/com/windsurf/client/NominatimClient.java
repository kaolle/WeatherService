package com.windsurf.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "nominatim")
@Path("/reverse")
@Produces(MediaType.APPLICATION_JSON)
@ClientHeaderParam(name = "User-Agent", value = "WeatherService/1.0 (windsurf spot guide)")
public interface NominatimClient {

    @GET
    Response reverse(
        @QueryParam("format") String format,
        @QueryParam("lat")    double lat,
        @QueryParam("lon")    double lon,
        @QueryParam("zoom")   int zoom
    );

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(String display_name, Address address) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Address(String country, String country_code) {}
}
