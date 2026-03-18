package com.notifyglance.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.notifyglance.model.NotificationEntity;

import java.util.List;

@Dao
public interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(NotificationEntity entity);

    /** All items (for repeat cycle after all viewed) within lookback window */
    @Query("SELECT * FROM notifications WHERE capturedAt >= :threshold ORDER BY postedAt DESC")
    List<NotificationEntity> getAllForCycleSince(long threshold);

    /** Unpresented items within lookback window (for card mode) */
    @Query("SELECT * FROM notifications WHERE presented = 0 AND capturedAt >= :threshold ORDER BY postedAt DESC")
    List<NotificationEntity> getUnpresentedForCycleSince(long threshold);

    /** Mark single item presented */
    @Query("UPDATE notifications SET presented = 1 WHERE id = :id")
    void markPresented(long id);

    /** Count unpresented */
    @Query("SELECT COUNT(*) FROM notifications WHERE presented = 0")
    int countUnpresented();

    /** Count unpresented within lookback window */
    @Query("SELECT COUNT(*) FROM notifications WHERE presented = 0 AND capturedAt >= :threshold")
    int countUnpresentedSince(long threshold);

    /** Count all within lookback window */
    @Query("SELECT COUNT(*) FROM notifications WHERE capturedAt >= :threshold")
    int countAllSince(long threshold);

    @Query("DELETE FROM notifications WHERE notificationKey = :notificationKey")
    int deleteByNotificationKey(String notificationKey);

    /** Clear all notifications */
    @Query("DELETE FROM notifications")
    void clearAll();

    /** Count total */
    @Query("SELECT COUNT(*) FROM notifications")
    int countAll();

}
