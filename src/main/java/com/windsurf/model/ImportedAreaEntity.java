package com.windsurf.model;

import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection = "imported_areas")
public class ImportedAreaEntity extends PanacheMongoEntityBase {

    public String id;
    public double tileLat;
    public double tileLon;
    public String country;
    public String lastAttemptAt;
    public String status;
    public int spotsFound;
    public String errorMessage;
}
