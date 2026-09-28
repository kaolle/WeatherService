package com.windsurf.resource;

import com.windsurf.model.ViewportInterestEntity;
import com.windsurf.service.Tile;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Path("/discovery/interest")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Discovery")
public class DiscoveryInterestResource {

    // Reject viewports larger than this to avoid a single request blowing up the hit-counter across the world.
    private static final double MAX_SPAN = 8.0;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Register user interest in a viewport bbox for background OSM discovery")
    public Response register(Map<String, Object> body) {
        double south = asDouble(body.get("south"));
        double west  = asDouble(body.get("west"));
        double north = asDouble(body.get("north"));
        double east  = asDouble(body.get("east"));
        if (!Double.isFinite(south) || !Double.isFinite(west) || !Double.isFinite(north) || !Double.isFinite(east)
            || south >= north || west >= east
            || north - south > MAX_SPAN || east - west > MAX_SPAN
            || south < -85 || north > 85 || west < -180 || east > 180) {
            return Response.status(400).entity(Map.of("error", "Ogiltig viewport")).build();
        }

        List<Tile> tiles = Tile.covering(south, west, north, east);
        String now = LocalDateTime.now().toString();
        for (Tile tile : tiles) {
            ViewportInterestEntity existing = ViewportInterestEntity.findById(tile.key());
            if (existing == null) {
                ViewportInterestEntity v = new ViewportInterestEntity();
                v.id = tile.key();
                v.tileLat = tile.lat();
                v.tileLon = tile.lon();
                v.hitCount = 1;
                v.firstSeenAt = now;
                v.lastAccessedAt = now;
                v.persist();
            } else {
                existing.hitCount++;
                existing.lastAccessedAt = now;
                existing.update();
            }
        }
        return Response.ok(Map.of("tiles", tiles.size())).build();
    }

    private static double asDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : Double.NaN;
    }
}
