package com.windsurf.model;

import java.util.List;

public record UpdateSpotRequest(
    String name,
    String description,
    String accessInfo,
    String region,
    SpotType type,
    DifficultyLevel difficulty,
    double idealWindSpeed,
    double minWindSpeed,
    double maxWindSpeed,
    List<String> bestDirections
) {}
