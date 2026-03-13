package com.notifyglance.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.notifyglance.model.NotificationEntity;

import java.util.List;

@Dao
public interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(NotificationEntity entity);

    @Update
    void update(NotificationEntity entity);

    /** All notifications ordered newest-first */
    @Query("SELECT * FROM notifications ORDER BY postedAt DESC")
    List<NotificationEntity> getAll();

    /** Items not yet presented (for queue) within lookback window */
    @Query("SELECT * FROM notifications WHERE presented = 0 AND capturedAt >= :threshold ORDER BY postedAt ASC")
    List<NotificationEntity> getUnpresentedSince(long threshold);

    /** All items (for repeat cycle after all viewed) within lookback window */
    @Query("SELECT * FROM notifications WHERE capturedAt >= :threshold ORDER BY postedAt ASC")
    List<NotificationEntity> getAllForCycleSince(long threshold);

    /** Mark lookback-window items as presented = 0 so the cycle can restart */
    @Query("UPDATE notifications SET presented = 0 WHERE capturedAt >= :threshold")
    void resetPresentedSince(long threshold);

    /** Mark all as presented = 0 so the cycle can restart */
    @Query("UPDATE notifications SET presented = 0")
    void resetAllPresented();

    /** Mark single item presented */
    @Query("UPDATE notifications SET presented = 1 WHERE id = :id")
    void markPresented(long id);

    /** Delete notifications older than given timestamp */
    @Query("DELETE FROM notifications WHERE postedAt < :threshold")
    void deleteOlderThan(long threshold);

    /** Count total */
    @Query("SELECT COUNT(*) FROM notifications")
    int countAll();

    /** Latest postedAt */
    @Query("SELECT MAX(postedAt) FROM notifications")
    long lastPostedAt();
}
