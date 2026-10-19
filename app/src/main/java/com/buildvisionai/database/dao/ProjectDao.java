package com.buildvisionai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import com.buildvisionai.database.entity.ProjectEntity;

import java.util.List;

/**
 * Project headers. Saves use {@code @Upsert} (insert, else update) and never REPLACE: REPLACE deletes
 * the old row first, and with foreign keys on that would cascade away every version of the project.
 */
@Dao
public interface ProjectDao {
    @Upsert
    void upsert(ProjectEntity project);

    @Query("SELECT * FROM projects WHERE projectId = :projectId")
    ProjectEntity get(String projectId);

    @Query("SELECT * FROM projects WHERE projectId = :projectId")
    LiveData<ProjectEntity> observe(String projectId);

    @Query("SELECT * FROM projects WHERE archived = :archived ORDER BY updatedAt DESC")
    LiveData<List<ProjectEntity>> observeAll(boolean archived);

    @Query("UPDATE projects SET archived = :archived, updatedAt = :now WHERE projectId = :projectId")
    void setArchived(String projectId, boolean archived, long now);

    @Query("UPDATE projects SET headVersionId = :versionId, status = :status, updatedAt = :now WHERE projectId = :projectId")
    void setHead(String projectId, String versionId, String status, long now);

    /** Updates the cached status only when {@code versionId} is the head, so an old version cannot change it. */
    @Query("UPDATE projects SET status = :status, updatedAt = :now WHERE projectId = :projectId AND headVersionId = :versionId")
    int setStatusIfHead(String projectId, String versionId, String status, long now);

    @Query("UPDATE projects SET updatedAt = :now WHERE projectId = :projectId")
    void touch(String projectId, long now);

    /** Cascades to versions, plans, estimates, members and comments. The caller confirms with the user first. */
    @Query("DELETE FROM projects WHERE projectId = :projectId")
    int delete(String projectId);

    @Query("SELECT COUNT(*) FROM projects")
    int count();
}
