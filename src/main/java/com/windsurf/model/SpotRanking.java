package com.windsurf.model;

public record SpotRanking(
    Spot spot,
    WindData currentWind,
    double score,
    String conditions
) {}
