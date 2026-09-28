package com.windsurf.service;

import com.windsurf.client.NominatimClient;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves country name for a tile via OSM Nominatim reverse geocoding.
 * Cached per tile in memory — Nominatim asks for &lt;1 req/s and no repeat queries.
 */
@ApplicationScoped
public class CountryLookupService {

    private static final Logger LOG = Logger.getLogger(CountryLookupService.class);
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @RestClient
    NominatimClient nominatim;

    public String countryFor(Tile tile) {
        String cached = cache.get(tile.key());
        if (cached != null) return cached;
        String resolved = resolve(tile);
        // Only cache successful lookups so failed tiles retry on the next call.
        if (!"?".equals(resolved)) cache.put(tile.key(), resolved);
        return resolved;
    }

    private String resolve(Tile tile) {
        try {
            double lat = tile.lat() + Tile.SIZE / 2;
            double lon = tile.lon() + Tile.SIZE / 2;
            NominatimClient.Response res = nominatim.reverse("json", lat, lon, 3);
            if (res == null) return "?";
            if (res.address() != null && res.address().country() != null) return res.address().country();
            if (res.display_name() != null && !res.display_name().isBlank()) return res.display_name();
            return "havsområde";
        } catch (Exception e) {
            LOG.debugf("Nominatim lookup failed tile=%s: %s", tile.key(), e.getMessage());
            return "?";
        }
    }
}
