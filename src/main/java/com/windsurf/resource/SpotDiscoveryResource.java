package com.windsurf.resource;

import com.windsurf.service.SpotDiscoveryService;
import com.windsurf.model.SpotEntity;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/spots/discover")
@Produces(MediaType.APPLICATION_JSON)
public class SpotDiscoveryResource {
    @Inject SpotDiscoveryService discovery;

    @GET
    public SpotDiscoveryService.Result discover(@QueryParam("south") double south, @QueryParam("west") double west,
            @QueryParam("north") double north, @QueryParam("east") double east) {
        var result = discovery.discover(south, west, north, east);
        if (result.spots().isEmpty()) return result;
        Set<String> saved = SpotEntity.<SpotEntity>find("externalId in ?1", result.spots().stream().map(SpotDiscoveryService.Candidate::id).toList())
                .list().stream().map(p -> p.externalId).collect(Collectors.toSet());
        return new SpotDiscoveryService.Result(result.spots().stream().filter(p -> !saved.contains(p.id())).toList(), result.message());
    }
}
