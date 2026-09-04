package com.windsurf.service;

import com.windsurf.client.OpenMeteoClient;
import com.windsurf.client.OpenMeteoResponse;
import com.windsurf.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class SpotService {

    private static final double KMH_TO_MS = 1.0 / 3.6;
    private static final int CACHE_MINUTES = 30;

    @RestClient
    OpenMeteoClient openMeteoClient;

    @Inject
    SpotScoringService scoringService;

    private final Map<String, CachedWind> windCache = new ConcurrentHashMap<>();

    @Transactional
    public List<SpotRanking> getTopSpots(double lat, double lon, double radiusKm, int limit) {
        return SpotEntity.<SpotEntity>listAll().stream()
            .filter(e -> e.approved)
            .filter(e -> distanceKm(lat, lon, e.latitude, e.longitude) <= radiusKm)
            .map(entity -> {
                Spot spot = entity.toSpot();
                WindData wind = fetchWind(spot);
                double score = scoringService.score(spot, wind);
                String conditions = scoringService.conditions(spot, wind, score);
                return new SpotRanking(spot, wind, score, conditions);
            })
            .sorted(Comparator.comparingDouble(SpotRanking::score).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    private WindData fetchWind(Spot spot) {
        String key = String.format("%.2f_%.2f", spot.latitude(), spot.longitude());
        CachedWind cached = windCache.get(key);
        if (cached != null && !cached.isExpired()) return cached.data();

        OpenMeteoResponse response = openMeteoClient.getCurrent(spot.latitude(), spot.longitude(), true);
        double dir = response.currentWeather().winddirection();
        double speedMs = response.currentWeather().windspeed() * KMH_TO_MS;
        WindData wind = new WindData(speedMs, dir, 0.0, scoringService.directionLabel(dir));
        windCache.put(key, new CachedWind(wind, Instant.now()));
        return wind;
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private record CachedWind(WindData data, Instant cachedAt) {
        boolean isExpired() {
            return Instant.now().isAfter(cachedAt.plus(CACHE_MINUTES, ChronoUnit.MINUTES));
        }
    }
}
