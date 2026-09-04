package com.windsurf.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "app_settings")
public class AppSettings extends PanacheEntityBase {

    @Id
    public Long id = 1L;

    public String title = "Windsurf Spots";

    public static AppSettings get() {
        AppSettings s = findById(1L);
        if (s == null) {
            s = new AppSettings();
            s.persist();
        }
        return s;
    }
}
