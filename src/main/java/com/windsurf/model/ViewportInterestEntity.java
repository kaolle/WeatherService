package com.windsurf.model;

import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection = "viewport_interest")
public class ViewportInterestEntity extends PanacheMongoEntityBase {

    public String id;
    public double tileLat;
    public double tileLon;
    public String country;
    public long hitCount;
    public String firstSeenAt;
    public String lastAccessedAt;
}
