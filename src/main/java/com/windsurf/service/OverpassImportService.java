package com.windsurf.service;

import com.windsurf.client.OverpassClient;
import com.windsurf.client.OverpassResponse;
import com.windsurf.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
public class OverpassImportService {

    private static final String QUERY = """
        [out:json][timeout:90];
        (
          node["sport"="windsurfing"](55.0,10.5,61.5,25.0);
          node["sport"="kitesurfing"](55.0,10.5,61.5,25.0);
          node["sport"="kiteboarding"](55.0,10.5,61.5,25.0);
          node["leisure"="beach"]["sport"="windsurfing"](55.0,10.5,61.5,25.0);
        );
        out body;
        """;

    @RestClient
    OverpassClient overpassClient;

    public ImportResult importFromOSM() {
        OverpassResponse response = overpassClient.query(QUERY);
        int imported = 0;
        int skipped = 0;

        for (OverpassResponse.Element el : response.elements()) {
            if (!"node".equals(el.type())) continue;

            String osmId = "osm-" + el.id();
            if (SpotEntity.find("externalId", osmId).count() > 0) {
                skipped++;
                continue;
            }

            SpotEntity entity = new SpotEntity();
            entity.externalId = osmId;
            entity.name = el.tags().getOrDefault("name",
                el.tags().getOrDefault("ref", "Surfspot #" + el.id()));
            entity.latitude = el.lat();
            entity.longitude = el.lon();
            entity.region = el.tags().getOrDefault("addr:county",
                el.tags().getOrDefault("is_in:county", "Sverige"));
            entity.description = el.tags().getOrDefault("description",
                "Importerad från OpenStreetMap. Sport: " + el.tags().getOrDefault("sport", "windsurfing"));
            entity.accessInfo = el.tags().getOrDefault("access",
                el.tags().getOrDefault("note", null));
            entity.type = spotType(el.tags().getOrDefault("sport", "windsurfing"));
            entity.difficulty = DifficultyLevel.INTERMEDIATE;
            entity.idealWindSpeed = 8.0;
            entity.minWindSpeed = 5.0;
            entity.maxWindSpeed = 15.0;
            entity.bestDirections = List.of("W", "SW", "S", "SE", "E", "NE", "N", "NW");
            entity.source = SpotSource.OSM;
            entity.approved = true;
            entity.persist();
            imported++;
        }

        return new ImportResult(imported, skipped);
    }

    private SpotType spotType(String sport) {
        return switch (sport) {
            case "kitesurfing", "kiteboarding" -> SpotType.BUMP_N_JUMP;
            default -> SpotType.FLAT_WATER;
        };
    }

    public record ImportResult(int imported, int skipped) {}
}
