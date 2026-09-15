package com.windsurf.client;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.util.List;
import java.util.Map;

@RegisterRestClient(configKey = "overpass-discovery")
@Path("/api/interpreter")
public interface OverpassDiscoveryClient {
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    Result query(@FormParam("data") String query);

    record Result(List<Element> elements, String remark) {}
    record Center(double lat, double lon) {}
    record Element(String type, long id, Double lat, Double lon, Center center, Map<String, String> tags) {}
}
