package com.windsurf.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windsurf.model.*;
import com.windsurf.service.OverpassImportService;
import com.windsurf.service.SpotService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/spots")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Spots", description = "Windsurf spot discovery and wind conditions")
public class SpotResource {

    @Inject
    SpotService spotService;

    @Inject
    OverpassImportService overpassImportService;

    @Inject
    ObjectMapper objectMapper;

    @GET
    @Path("/top")
    @Operation(summary = "Top windsurf spots ranked by current wind conditions")
    public List<SpotRanking> getTopSpots(
        @Parameter(description = "Center latitude (default: southern Sweden)")
        @QueryParam("lat") @DefaultValue("58.0") double lat,

        @Parameter(description = "Center longitude")
        @QueryParam("lon") @DefaultValue("16.5") double lon,

        @Parameter(description = "Search radius in km")
        @QueryParam("radius_km") @DefaultValue("600") double radiusKm,

        @Parameter(description = "Max results")
        @QueryParam("limit") @DefaultValue("50") int limit
    ) {
        return spotService.getTopSpots(lat, lon, radiusKm, limit);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Add a user-contributed windsurf spot")
    public Response addSpot(UserSpotRequest req, @HeaderParam("X-User-Name") String userName) {
        if (req.name() == null || req.name().isBlank()) {
            return Response.status(400).entity(Map.of("error", "name krävs")).build();
        }
        SpotEntity entity = new SpotEntity();
        entity.externalId = UUID.randomUUID().toString();
        entity.name = req.name().strip();
        entity.latitude = req.latitude();
        entity.longitude = req.longitude();
        entity.description = req.description();
        entity.accessInfo = req.accessInfo();
        entity.region = req.region() != null ? req.region() : "Sverige";
        entity.type = req.type() != null ? req.type() : SpotType.FLAT_WATER;
        entity.difficulty = req.difficulty() != null ? req.difficulty() : DifficultyLevel.INTERMEDIATE;
        entity.idealWindSpeed = req.idealWindSpeed() > 0 ? req.idealWindSpeed() : 8.0;
        entity.minWindSpeed   = req.minWindSpeed()   > 0 ? req.minWindSpeed()   : 5.0;
        entity.maxWindSpeed   = req.maxWindSpeed()   > 0 ? req.maxWindSpeed()   : 15.0;
        entity.bestDirections = (req.bestDirections() != null && !req.bestDirections().isEmpty())
            ? String.join(",", req.bestDirections())
            : "W,SW,S,SE,E,NE,N,NW";
        entity.source = SpotSource.USER;
        entity.approved = true;
        entity.createdBy = userName;
        entity.createdAt = LocalDateTime.now();
        entity.persist();
        logChange(entity.externalId, entity.name, "CREATED", userName, entity);
        return Response.status(201).entity(Map.of("id", entity.externalId, "name", entity.name)).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Update an existing windsurf spot")
    public Response updateSpot(@PathParam("id") String id, UpdateSpotRequest req,
                               @HeaderParam("X-User-Name") String userName) {
        SpotEntity entity = SpotEntity.find("externalId", id).firstResult();
        if (entity == null) {
            return Response.status(404).entity(Map.of("error", "Spot hittades inte: " + id)).build();
        }
        if (req.name() != null && !req.name().isBlank())       entity.name = req.name().strip();
        if (req.description() != null)                          entity.description = req.description();
        if (req.accessInfo() != null)                           entity.accessInfo = req.accessInfo();
        if (req.region() != null && !req.region().isBlank())   entity.region = req.region();
        if (req.type() != null)                                 entity.type = req.type();
        if (req.difficulty() != null)                           entity.difficulty = req.difficulty();
        if (req.idealWindSpeed() > 0)                           entity.idealWindSpeed = req.idealWindSpeed();
        if (req.minWindSpeed() > 0)                             entity.minWindSpeed = req.minWindSpeed();
        if (req.maxWindSpeed() > 0)                             entity.maxWindSpeed = req.maxWindSpeed();
        if (req.bestDirections() != null && !req.bestDirections().isEmpty()) {
            entity.bestDirections = String.join(",", req.bestDirections());
        }
        entity.updatedBy = userName;
        entity.updatedAt = LocalDateTime.now();
        logChange(entity.externalId, entity.name, "UPDATED", userName, entity);
        return Response.ok(Map.of("id", entity.externalId, "name", entity.name)).build();
    }

    private void logChange(String spotId, String spotName, String action, String changedBy, SpotEntity entity) {
        logChange(spotId, spotName, action, changedBy, entity, null);
    }

    private void logChange(String spotId, String spotName, String action, String changedBy, SpotEntity entity, Long restoredFromLogId) {
        ChangeLogEntity log = new ChangeLogEntity();
        log.restoredFromLogId = restoredFromLogId;
        log.spotExternalId = spotId;
        log.spotName = spotName;
        log.action = action;
        log.changedBy = changedBy;
        log.changedAt = LocalDateTime.now();
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("name", entity.name);
            snapshot.put("region", entity.region);
            snapshot.put("description", entity.description);
            snapshot.put("accessInfo", entity.accessInfo);
            snapshot.put("type", entity.type != null ? entity.type.name() : null);
            snapshot.put("difficulty", entity.difficulty != null ? entity.difficulty.name() : null);
            snapshot.put("minWindSpeed", entity.minWindSpeed);
            snapshot.put("idealWindSpeed", entity.idealWindSpeed);
            snapshot.put("maxWindSpeed", entity.maxWindSpeed);
            snapshot.put("bestDirections", entity.bestDirections);
            log.changeJson = objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.changeJson = "{}";
        }
        log.persist();
    }

    @GET
    @Path("/{id}/history")
    @Operation(summary = "Change log for a specific spot")
    public Response getHistory(@PathParam("id") String id) {
        List<ChangeLogEntity> logs = ChangeLogEntity
            .find("spotExternalId", id)
            .list();
        logs.sort((a, b) -> b.changedAt.compareTo(a.changedAt));
        return Response.ok(logs).build();
    }

    @POST
    @Path("/{id}/restore/{logId}")
    @Transactional
    @Operation(summary = "Restore a spot to a previous version from change log")
    public Response restoreSpot(@PathParam("id") String id, @PathParam("logId") Long logId,
                                @HeaderParam("X-User-Name") String userName) {
        SpotEntity entity = SpotEntity.find("externalId", id).firstResult();
        if (entity == null) return Response.status(404).entity(Map.of("error", "Spot ej hittad")).build();

        ChangeLogEntity log = ChangeLogEntity.findById(logId);
        if (log == null || !id.equals(log.spotExternalId))
            return Response.status(404).entity(Map.of("error", "Loggpost ej hittad")).build();

        @SuppressWarnings("unchecked")
        Map<String, Object> snap;
        try {
            snap = objectMapper.readValue(log.changeJson, Map.class);
        } catch (Exception e) {
            return Response.status(500).entity(Map.of("error", "Kan inte läsa snapshot: " + e.getMessage())).build();
        }

        if (snap.containsKey("name") && snap.get("name") != null)
            entity.name = snap.get("name").toString();
        if (snap.containsKey("region"))
            entity.region = snap.get("region") != null ? snap.get("region").toString() : null;
        if (snap.containsKey("description"))
            entity.description = snap.get("description") != null ? snap.get("description").toString() : null;
        if (snap.containsKey("accessInfo"))
            entity.accessInfo = snap.get("accessInfo") != null ? snap.get("accessInfo").toString() : null;
        if (snap.containsKey("bestDirections") && snap.get("bestDirections") != null)
            entity.bestDirections = snap.get("bestDirections").toString();
        if (snap.containsKey("type") && snap.get("type") != null)
            entity.type = SpotType.valueOf(snap.get("type").toString());
        if (snap.containsKey("difficulty") && snap.get("difficulty") != null)
            entity.difficulty = DifficultyLevel.valueOf(snap.get("difficulty").toString());
        if (snap.containsKey("minWindSpeed") && snap.get("minWindSpeed") != null) {
            double v = ((Number) snap.get("minWindSpeed")).doubleValue();
            if (v > 0) entity.minWindSpeed = v;
        }
        if (snap.containsKey("idealWindSpeed") && snap.get("idealWindSpeed") != null) {
            double v = ((Number) snap.get("idealWindSpeed")).doubleValue();
            if (v > 0) entity.idealWindSpeed = v;
        }
        if (snap.containsKey("maxWindSpeed") && snap.get("maxWindSpeed") != null) {
            double v = ((Number) snap.get("maxWindSpeed")).doubleValue();
            if (v > 0) entity.maxWindSpeed = v;
        }

        entity.updatedBy = userName;
        entity.updatedAt = LocalDateTime.now();
        logChange(entity.externalId, entity.name, "RESTORED", userName, entity, logId);
        return Response.ok(Map.of("id", entity.externalId, "restoredFrom", logId)).build();
    }

    @POST
    @Path("/import/osm")
    @Operation(summary = "Import windsurf spots from OpenStreetMap (Overpass API) for southern Sweden")
    public Response importFromOSM() {
        OverpassImportService.ImportResult result = overpassImportService.importFromOSM();
        return Response.ok(Map.of(
            "imported", result.imported(),
            "skipped", result.skipped(),
            "message", result.imported() + " nya spotar importerade från OSM"
        )).build();
    }
}
