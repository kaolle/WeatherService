package com.windsurf.model;

import java.util.List;

public record Spot(
    String id,
    String name,
    double latitude,
    double longitude,
    String region,
    SpotType type,
    DifficultyLevel difficulty,
    double idealWindSpeed,
    double minWindSpeed,
    double maxWindSpeed,
    List<String> bestDirections,
    String description,
    String accessInfo,
    String createdBy,
    String updatedBy,
    String createdAt,
    String updatedAt
) {}
