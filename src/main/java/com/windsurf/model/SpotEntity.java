package com.windsurf.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "spots")
public class SpotEntity extends PanacheEntity {

    public String externalId;
    public String name;
    public double latitude;
    public double longitude;
    public String region;

    @Enumerated(EnumType.STRING)
    public SpotType type;

    @Enumerated(EnumType.STRING)
    public DifficultyLevel difficulty;

    public double idealWindSpeed;
    public double minWindSpeed;
    public double maxWindSpeed;

    @Column(length = 500)
    public String bestDirections; // comma-separated: "W,SW,NW"

    @Column(length = 2000)
    public String description;

    @Column(length = 1000)
    public String accessInfo;

    @Enumerated(EnumType.STRING)
    public SpotSource source;

    public boolean approved = true;

    public String createdBy;
    public String updatedBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public Spot toSpot() {
        List<String> dirs = bestDirections != null
            ? Arrays.asList(bestDirections.split(","))
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
            createdAt != null ? createdAt.toString() : null,
            updatedAt != null ? updatedAt.toString() : null
        );
    }
}
