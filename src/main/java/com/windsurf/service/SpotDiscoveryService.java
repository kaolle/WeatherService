package com.windsurf.service;

import com.windsurf.client.OverpassDiscoveryClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import java.time.Clock;
import java.util.*;

@ApplicationScoped
public class SpotDiscoveryService {
    private static final double MAX_DISCOVERY_SPAN = 1.0;
    private static final Logger LOG = Logger.getLogger(SpotDiscoveryService.class);
    @RestClient
    OverpassDiscoveryClient client;
    Clock clock = Clock.systemUTC();
    private final Map<String, Entry> cache = new LinkedHashMap<>();
    private long retryAfter;
    private record Entry(List<Candidate> spots, long expires) {}
    public record Candidate(String id, String name, double latitude, double longitude,
                            String region, String description, String accessInfo, String url) {}
    public record Result(List<Candidate> spots, String message) {}

    // One request at a time per instance; repeated boxes share the cache.
    public synchronized Result discover(double south, double west, double north, double east) {
        if (!Double.isFinite(south) || !Double.isFinite(west) || !Double.isFinite(north) || !Double.isFinite(east)
                || south < -85 || north > 85 || west < -180 || east > 180
                || south >= north || west >= east || north - south > MAX_DISCOVERY_SPAN || east - west > MAX_DISCOVERY_SPAN) {
            throw new BadRequestException("Zooma in till ett mindre område för att söka i OpenStreetMap.");
        }
        // Round outwards so small pans reuse results without omitting viewport edges.
        double s = Math.floor(south * 10) / 10, w = Math.floor(west * 10) / 10;
        double n = Math.ceil(north * 10) / 10, e = Math.ceil(east * 10) / 10;
        String box = String.format(Locale.ROOT, "%.1f,%.1f,%.1f,%.1f", s, w, n, e);
        long now = clock.millis();
        cache.entrySet().removeIf(entry -> entry.getValue().expires <= now);
        Entry entry = cache.get(box);
        if (entry != null) return within(entry.spots, south, west, north, east);
        if (now < retryAfter) return new Result(List.of(), "OpenStreetMap-sökningen är tillfälligt pausad. Försök igen om en minut.");
        long startedAt = System.nanoTime();
        String query = "[out:json][timeout:12];nwr[\"sport\"~\"(^|;)(windsurfing|kitesurfing|kiteboarding|kite_surfing|kite|kiting|surfing)(;|$)\",i](" + box + ");out center 100;";
        try {
            LOG.infof("Overpass discovery started: bbox=%s", box);
            var response = client.query(query);
            if (response == null) {
                throw new IllegalStateException("response was null");
            }
            if (response.elements() == null) {
                throw new IllegalStateException("response did not contain elements");
            }
            if (response.remark() != null && !response.remark().isBlank()) {
                throw new IllegalStateException("Overpass remark: " + response.remark());
            }
            Map<String, Candidate> unique = new LinkedHashMap<>();
            for (var el : response.elements()) {
                if (el.id() <= 0 || !Set.of("node", "way", "relation").contains(el.type())) continue;
                Double lat = el.center() != null ? Double.valueOf(el.center().lat()) : el.lat();
                Double lon = el.center() != null ? Double.valueOf(el.center().lon()) : el.lon();
                if (lat == null || lon == null || !Double.isFinite(lat) || !Double.isFinite(lon)) continue;
                String id = "osm-" + (el.type().equals("node") ? "" : el.type() + "-") + el.id();
                Map<String, String> tags = el.tags() == null ? Map.of() : el.tags();
                String url = "https://www.openstreetmap.org/" + el.type() + "/" + el.id();
                unique.put(id, new Candidate(id, tags.getOrDefault("name", "Surfspot från OpenStreetMap"), lat, lon,
                        tags.getOrDefault("addr:city", tags.getOrDefault("addr:country", "")),
                        tags.getOrDefault("description", "") + "\nKälla: OpenStreetMap (ODbL), " + url,
                        tags.getOrDefault("access", ""), url));
            }
            if (cache.size() >= 100) cache.remove(cache.keySet().iterator().next());
            var spots = List.copyOf(unique.values());
            cache.put(box, new Entry(spots, clock.millis() + 3_600_000));
            LOG.infof("Overpass discovery completed: bbox=%s elements=%d candidates=%d durationMs=%d",
                    box, response.elements().size(), spots.size(), elapsedMillis(startedAt));
            return within(spots, south, west, north, east);
        } catch (RuntimeException ex) {
            retryAfter = clock.millis() + 60_000;
            LOG.errorf(ex, "Overpass discovery failed: bbox=%s durationMs=%d exception=%s message=%s",
                    box, elapsedMillis(startedAt), ex.getClass().getName(), ex.getMessage());
            return new Result(List.of(), "OpenStreetMap kunde inte nås. Sparade spotar visas fortfarande. Försök igen senare.");
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private Result within(List<Candidate> spots, double s, double w, double n, double e) {
        return new Result(spots.stream().filter(p -> p.latitude >= s && p.latitude <= n && p.longitude >= w && p.longitude <= e).toList(),
                spots.size() >= 100 ? "Sökningen visar högst 100 OSM-fynd. Zooma in för fler detaljer." : null);
    }
}
