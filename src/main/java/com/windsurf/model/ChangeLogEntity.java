package com.windsurf.model;

import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection = "change_log")
public class ChangeLogEntity extends PanacheMongoEntityBase {

    public String id;
    public String spotExternalId;
    public String spotName;
    public String action;
    public String changedBy;
    public String changedAt;
    public String changeJson;
    public String restoredFromLogId;
}
