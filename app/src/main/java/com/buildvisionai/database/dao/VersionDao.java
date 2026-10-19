package com.buildvisionai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import com.buildvisionai.database.entity.VersionEntity;

import java.util.List;

/**
 * Version headers. Versions are append-only history: the only edits are the label and the workflow
 * state, and the state can only move forward (see {@link com.buildvisionai.database.VersionGuard}).
 * There is deliberately no delete method here; a draft can be discarded with {@link #discardDraft}.
 */
@Dao
public interface VersionDao {
    @Upsert
    void upsert(VersionEntity version);

    @Query("SELECT * FROM versions WHERE id = :id")
    VersionEntity get(String id);

    @Query("SELECT * FROM versions WHERE id = :id")
    LiveData<VersionEntity> observe(String id);

    @Query("SELECT * FROM versions WHERE projectId = :projectId ORDER BY number DESC")
    List<VersionEntity> listForProject(String projectId);

    @Query("SELECT * FROM versions WHERE projectId = :projectId ORDER BY number DESC")
    LiveData<List<VersionEntity>> observeForProject(String projectId);

    @Query("SELECT * FROM versions WHERE projectId = :projectId ORDER BY number DESC LIMIT 1")
    VersionEntity latest(String projectId);

    @Query("SELECT COALESCE(MAX(number), 0) + 1 FROM versions WHERE projectId = :projectId")
    int nextNumber(String projectId);

    @Query("UPDATE versions SET workflowState = :state WHERE id = :id")
    void setWorkflowState(String id, String state);

    @Query("UPDATE versions SET label = :label WHERE id = :id")
    void setLabel(String id, String label);

    /** Only drafts can be removed; for any other state the guard trigger aborts the statement. */
    @Query("DELETE FROM versions WHERE id = :id AND workflowState = 'DRAFT'")
    int discardDraft(String id);
}
