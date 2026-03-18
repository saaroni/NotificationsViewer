package com.notifyglance.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "notifications",
        indices = {@Index(value = {"notificationKey"}, unique = true)}
)
public class NotificationEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String notificationKey;
    public String packageName;
    public String appLabel;
    public String title;
    public String text;
    public String subText;
    public long postedAt;
    public long capturedAt;
    public boolean presented;   // shown in overlay at least once
    public boolean isOngoing;
    public int importance;

    public NotificationEntity() {}

    @Ignore
    public NotificationEntity(String packageName, String appLabel,
                              String title, String text, String subText,
                              long postedAt, long capturedAt,
                              boolean isOngoing, int importance) {
        this(null, packageName, appLabel, title, text, subText,
                postedAt, capturedAt, isOngoing, importance);
    }

    @Ignore
    public NotificationEntity(String notificationKey, String packageName, String appLabel,
                              String title, String text, String subText,
                              long postedAt, long capturedAt,
                              boolean isOngoing, int importance) {
        this.notificationKey = notificationKey;
        this.packageName = packageName;
        this.appLabel    = appLabel;
        this.title       = title;
        this.text        = text;
        this.subText     = subText;
        this.postedAt    = postedAt;
        this.capturedAt  = capturedAt;
        this.presented   = false;
        this.isOngoing   = isOngoing;
        this.importance  = importance;
    }
}
