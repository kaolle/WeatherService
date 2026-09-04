package com.windsurf.service;

import com.windsurf.model.Spot;
import com.windsurf.model.WindData;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SpotScoringService {

    public double score(Spot spot, WindData wind) {
        if (wind.speedMs() < spot.minWindSpeed() || wind.speedMs() > spot.maxWindSpeed()) {
            return 0.0;
        }

        double directionScore = spot.bestDirections().contains(wind.directionLabel()) ? 1.0 : 0.3;

        // Skala speedScore mot spotens eget intervall: 0 vid min/max, 1 vid idealvind
        double speedScore;
        if (wind.speedMs() <= spot.idealWindSpeed()) {
            double range = spot.idealWindSpeed() - spot.minWindSpeed();
            speedScore = range > 0 ? (wind.speedMs() - spot.minWindSpeed()) / range : 1.0;
        } else {
            double range = spot.maxWindSpeed() - spot.idealWindSpeed();
            speedScore = range > 0 ? (spot.maxWindSpeed() - wind.speedMs()) / range : 1.0;
        }
        speedScore = Math.max(0, speedScore);

        // Bydata saknas (gustsMs=0) — använd neutralt värde 0.6 istället för att ge full bonus
        double gustScore = wind.gustsMs() > 0
            ? 1.0 - Math.min(Math.max((wind.gustsMs() - wind.speedMs()) / wind.speedMs(), 0), 1.0)
            : 0.6;

        return (directionScore * 0.4) + (speedScore * 0.4) + (gustScore * 0.2);
    }

    public String conditions(Spot spot, WindData wind, double score) {
        if (score == 0.0) return "Ingen vind / utanför intervall";
        if (score >= 0.8) return "Utmärkt";
        if (score >= 0.6) return "Bra";
        if (score >= 0.4) return "Godkänt";
        return "Dåligt";
    }

    public String directionLabel(double degrees) {
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = (int) Math.round(degrees / 45.0) % 8;
        return dirs[index];
    }
}
