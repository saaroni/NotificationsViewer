package com.notifyglance.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notifications")
public class NotificationEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String packageName;
    public String appLabel;
    public String title;
    public String text;
    public String subText;
    public long postedAt;
    public boolean presented;   // shown in overlay at least once
    public boolean isOngoing;
    public int importance;

    public NotificationEntity() {}

    public NotificationEntity(String packageName, String appLabel,
                               String title, String text, String subText,
                               long postedAt, boolean isOngoing, int importance) {
        this.packageName = packageName;
        this.appLabel    = appLabel;
        this.title       = title;
        this.text        = text;
        this.subText     = subText;
        this.postedAt    = postedAt;
        this.presented   = false;
        this.isOngoing   = isOngoing;
        this.importance  = importance;
    }
}
