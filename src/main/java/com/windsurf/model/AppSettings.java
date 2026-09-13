package com.windsurf.model;

import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection = "app_settings")
public class AppSettings extends PanacheMongoEntityBase {

    public String id = "singleton";
    public String title = "Windsurf Spots";

    public static AppSettings get() {
        AppSettings s = findById("singleton");
        if (s == null) {
            s = new AppSettings();
            s.persist();
        }
        return s;
    }
}
