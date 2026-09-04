package com.windsurf.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "change_log")
public class ChangeLogEntity extends PanacheEntity {

    public String spotExternalId;
    public String spotName;
    public String action; // CREATED | UPDATED
    public String changedBy;
    public LocalDateTime changedAt;

    @Column(length = 4000)
    public String changeJson;

    public Long restoredFromLogId; // set when action=RESTORED
}
