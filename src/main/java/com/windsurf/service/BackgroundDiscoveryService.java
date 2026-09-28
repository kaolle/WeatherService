package com.windsurf.service;

import com.windsurf.client.OverpassClient;
import com.windsurf.client.OverpassResponse;
import com.windsurf.model.*;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Periodically imports OSM spots for the most-popular viewport tile that hasn't
 * been imported yet. Runs one tile per tick to stay under Overpass API limits.
 */
@ApplicationScoped
public class BackgroundDiscoveryService {

    private static final Logger LOG = Logger.getLogger(BackgroundDiscoveryService.class);

    // Skip re-imports of successful tiles within this window; failed tiles retry sooner.
    private static final Duration REIMPORT_SUCCESS = Duration.ofDays(30);
    private static final Duration REIMPORT_FAILED  = Duration.ofMinutes(30);

    @RestClient
    OverpassClient overpassClient;

    @Inject
    CountryLookupService countryLookup;

    @Scheduled(every = "3m", delayed = "30s", concurrentExecution = ConcurrentExecution.SKIP)
    void importNextPopularTile() {
        long interests = ViewportInterestEntity.count();
        long areas = ImportedAreaEntity.count();
        Tile tile = pickNextTile();
        if (tile == null) {
            LOG.infof("Scheduler tick — interests=%d importedAreas=%d — no tile to import", interests, areas);
            return;
        }
        LOG.infof("Scheduler tick — interests=%d importedAreas=%d — next tile: %s (%s)",
            interests, areas, tile.key(), countryLookup.countryFor(tile));
        importTile(tile);
    }

    Tile pickNextTile() {
        Set<String> recentlyAttempted = recentAttemptKeys();
        var candidates = ViewportInterestEntity
            .<ViewportInterestEntity>findAll(io.quarkus.panache.common.Sort.descending("hitCount"))
            .page(0, 200);
        do {
            for (ViewportInterestEntity v : candidates.list()) {
                if (!recentlyAttempted.contains(v.id)) return new Tile(v.tileLat, v.tileLon);
            }
            if (!candidates.hasNextPage()) break;
            candidates.nextPage();
        } while (true);
        return null;
    }

    private Set<String> recentAttemptKeys() {
        Instant successCutoff = Instant.now().minus(REIMPORT_SUCCESS);
        Instant failedCutoff  = Instant.now().minus(REIMPORT_FAILED);
        Set<String> keys = new HashSet<>();
        for (ImportedAreaEntity a : ImportedAreaEntity.<ImportedAreaEntity>listAll()) {
            Instant attempted = parseIsoLocal(a.lastAttemptAt);
            if (attempted == null) continue;
            boolean success = "SUCCESS".equals(a.status);
            if (attempted.isAfter(success ? successCutoff : failedCutoff)) keys.add(a.id);
        }
        return keys;
    }

    void importTile(Tile tile) {
        String bbox = String.format(Locale.ROOT, "%.4f,%.4f,%.4f,%.4f",
            tile.south(), tile.west(), tile.north(), tile.east());
        String query = "[out:json][timeout:60];"
            + "("
            + "node[\"sport\"~\"(^|;)(windsurfing|kitesurfing|kiteboarding|kite_surfing|kiting)(;|$)\",i](" + bbox + ");"
            + "way[\"sport\"~\"(^|;)(windsurfing|kitesurfing|kiteboarding|kite_surfing|kiting)(;|$)\",i](" + bbox + ");"
            + "node[\"natural\"=\"beach\"][\"surface\"=\"sand\"](" + bbox + ");"
            + "way[\"natural\"=\"beach\"][\"surface\"=\"sand\"](" + bbox + ");"
            + ");out center 300;";
        String country = countryLookup.countryFor(tile);
        ImportedAreaEntity area = new ImportedAreaEntity();
        area.id = tile.key();
        area.tileLat = tile.lat();
        area.tileLon = tile.lon();
        area.country = country;
        area.lastAttemptAt = LocalDateTime.now().toString();
        try {
            OverpassResponse response = overpassClient.query(query);
            int imported = 0;
            if (response != null && response.elements() != null) {
                for (OverpassResponse.Element el : response.elements()) {
                    if (persistPending(el)) imported++;
                }
            }
            area.status = "SUCCESS";
            area.spotsFound = imported;
            LOG.infof("Imported tile=%s country=%s spots=%d", tile.key(), country, imported);
        } catch (Exception ex) {
            area.status = "FAILED";
            area.errorMessage = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            LOG.warnf("Import failed tile=%s country=%s error=%s", tile.key(), country, area.errorMessage);
        }
        upsertImportedArea(area);
    }

    private boolean persistPending(OverpassResponse.Element el) {
        if (el.id() <= 0) return false;
        if (!Set.of("node", "way", "relation").contains(el.type())) return false;
        if (!"node".equals(el.type()) && el.center() == null) return false;
        double lat = "node".equals(el.type()) ? el.lat() : el.center().lat();
        double lon = "node".equals(el.type()) ? el.lon() : el.center().lon();
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat == 0 && lon == 0) return false;

        String externalId = "osm-" + (el.type().equals("node") ? "" : el.type() + "-") + el.id();
        if (SpotEntity.find("externalId", externalId).count() > 0) return false;

        Map<String, String> tags = el.tags() == null ? Map.of() : el.tags();
        String name = tags.getOrDefault("name", tags.getOrDefault("ref", "OSM-fynd " + el.id()));

        SpotEntity spot = new SpotEntity();
        spot.externalId = externalId;
        spot.name = name;
        spot.latitude = lat;
        spot.longitude = lon;
        spot.region = tags.getOrDefault("addr:city",
            tags.getOrDefault("addr:country", tags.getOrDefault("is_in:county", "Okänd region")));
        spot.description = tags.getOrDefault("description",
            "Källa: OpenStreetMap (ODbL). https://www.openstreetmap.org/" + el.type() + "/" + el.id());
        spot.accessInfo = tags.get("access");
        spot.type = tags.containsKey("natural") ? SpotType.FLAT_WATER : inferType(tags.get("sport"));
        spot.difficulty = DifficultyLevel.INTERMEDIATE;
        spot.idealWindSpeed = 8.0;
        spot.minWindSpeed = 5.0;
        spot.maxWindSpeed = 15.0;
        spot.bestDirections = List.of("W", "SW", "S", "SE", "E", "NE", "N", "NW");
        spot.source = SpotSource.OSM;
        spot.approved = false; // pending — user must review
        spot.createdBy = "background-discovery";
        spot.createdAt = LocalDateTime.now().toString();
        try {
            spot.persist();
            return true;
        } catch (com.mongodb.MongoWriteException ex) {
            return false; // duplicate — already saved
        }
    }

    private static SpotType inferType(String sport) {
        if (sport == null) return SpotType.FLAT_WATER;
        return switch (sport.toLowerCase()) {
            case "kitesurfing", "kiteboarding", "kite_surfing", "kiting" -> SpotType.BUMP_N_JUMP;
            default -> SpotType.FLAT_WATER;
        };
    }

    private void upsertImportedArea(ImportedAreaEntity area) {
        ImportedAreaEntity existing = ImportedAreaEntity.findById(area.id);
        if (existing == null) {
            area.id = area.id != null ? area.id : UUID.randomUUID().toString();
            area.persist();
        } else {
            existing.country = area.country;
            existing.lastAttemptAt = area.lastAttemptAt;
            existing.status = area.status;
            existing.spotsFound = area.spotsFound;
            existing.errorMessage = area.errorMessage;
            existing.update();
        }
    }

    private static Instant parseIsoLocal(String iso) {
        try {
            return iso != null ? LocalDateTime.parse(iso).atZone(java.time.ZoneId.systemDefault()).toInstant() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
