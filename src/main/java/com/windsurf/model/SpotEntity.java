package com.windsurf.model;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.util.List;

@MongoEntity(collection = "spots")
public class SpotEntity extends PanacheMongoEntity {

    public String externalId;
    public String name;
    public double latitude;
    public double longitude;
    public String region;
    public SpotType type;
    public DifficultyLevel difficulty;
    public double idealWindSpeed;
    public double minWindSpeed;
    public double maxWindSpeed;
    public List<String> bestDirections;
    public String description;
    public String accessInfo;
    public SpotSource source;
    public boolean approved = true;
    public String createdBy;
    public String updatedBy;
    public String createdAt;
    public String updatedAt;

    public Spot toSpot() {
        List<String> dirs = bestDirections != null && !bestDirections.isEmpty()
            ? bestDirections
            : List.of("W", "SW", "S");
        return new Spot(
            externalId != null ? externalId : id.toString(),
            name,
            latitude,
            longitude,
            region != null ? region : "Sverige",
            type != null ? type : SpotType.FLAT_WATER,
            difficulty != null ? difficulty : DifficultyLevel.INTERMEDIATE,
            idealWindSpeed,
            minWindSpeed,
            maxWindSpeed,
            dirs,
            description != null ? description : "",
            accessInfo,
            createdBy,
            updatedBy,
            createdAt,
            updatedAt
        );
    }
}
