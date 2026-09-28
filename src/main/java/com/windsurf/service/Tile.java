package com.windsurf.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record Tile(double lat, double lon) {

    public static final double SIZE = 0.5;

    public static Tile of(double lat, double lon) {
        return new Tile(Math.floor(lat / SIZE) * SIZE, Math.floor(lon / SIZE) * SIZE);
    }

    public String key() {
        return String.format(Locale.ROOT, "%.1f_%.1f", lat, lon);
    }

    public double south() { return lat; }
    public double north() { return lat + SIZE; }
    public double west()  { return lon; }
    public double east()  { return lon + SIZE; }

    public static List<Tile> covering(double south, double west, double north, double east) {
        List<Tile> tiles = new ArrayList<>();
        Tile start = Tile.of(south, west);
        for (double y = start.lat; y < north; y += SIZE) {
            for (double x = start.lon; x < east; x += SIZE) {
                tiles.add(new Tile(round(y), round(x)));
            }
        }
        return tiles;
    }

    private static double round(double v) {
        return Math.round(v * 2.0) / 2.0;
    }
}
