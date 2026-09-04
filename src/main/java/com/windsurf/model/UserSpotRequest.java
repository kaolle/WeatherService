package com.windsurf.model;

import java.util.List;

public record UserSpotRequest(
    String name,
    double latitude,
    double longitude,
    String description,
    String accessInfo,
    String region,
    SpotType type,
    DifficultyLevel difficulty,
    double minWindSpeed,
    double idealWindSpeed,
    double maxWindSpeed,
    List<String> bestDirections
) {}
