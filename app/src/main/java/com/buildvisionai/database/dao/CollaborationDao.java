package com.buildvisionai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Upsert;

import com.buildvisionai.database.entity.ApprovalEntity;
import com.buildvisionai.database.entity.CommentEntity;
import com.buildvisionai.database.entity.MemberEntity;
import com.buildvisionai.database.entity.SuggestionEntity;
import com.buildvisionai.database.entity.UserEntity;

import java.util.List;

/**
 * People, roles, comments, suggestions and approvals. Approval rows are append-only (enforced by
 * {@link com.buildvisionai.database.VersionGuard}), so there is no update or delete for them here.
 */
@Dao
public interface CollaborationDao {
    // ---- people and roles ----------------------------------------------------------------

    @Upsert
    void upsertUser(UserEntity user);

    @Query("SELECT * FROM users WHERE id = :id")
    UserEntity user(String id);

    @Upsert
    void upsertMember(MemberEntity member);

    @Query("SELECT * FROM members WHERE projectId = :projectId ORDER BY addedAt")
    List<MemberEntity> members(String projectId);

    @Query("SELECT * FROM members WHERE projectId = :projectId ORDER BY addedAt")
    LiveData<List<MemberEntity>> observeMembers(String projectId);

    @Query("SELECT * FROM members WHERE projectId = :projectId AND userId = :userId")
    MemberEntity member(String projectId, String userId);

    @Query("DELETE FROM members WHERE projectId = :projectId AND userId = :userId")
    void removeMember(String projectId, String userId);

    // ---- comments --------------------------------------------------------------------------

    @Insert
    void insertComment(CommentEntity comment);

    @Query("UPDATE comments SET resolved = :resolved WHERE id = :id")
    void setCommentResolved(String id, boolean resolved);

    @Query("SELECT * FROM comments WHERE versionId = :versionId ORDER BY createdAt")
    List<CommentEntity> comments(String versionId);

    @Query("SELECT * FROM comments WHERE versionId = :versionId ORDER BY createdAt")
    LiveData<List<CommentEntity>> observeComments(String versionId);

    // ---- suggestions -----------------------------------------------------------------------

    @Insert
    void insertSuggestion(SuggestionEntity suggestion);

    @Query("SELECT * FROM suggestions WHERE id = :id")
    SuggestionEntity suggestion(String id);

    @Query("SELECT * FROM suggestions WHERE versionId = :versionId ORDER BY createdAt DESC")
    List<SuggestionEntity> suggestions(String versionId);

    @Query("SELECT * FROM suggestions WHERE versionId = :versionId ORDER BY createdAt DESC")
    LiveData<List<SuggestionEntity>> observeSuggestions(String versionId);

    /**
     * Decides a suggestion only while it is still in {@code fromStatus} (normally PROPOSED), so two
     * people deciding at once cannot both win. Returns the number of rows changed: 1 or 0.
     */
    @Query("UPDATE suggestions SET status = :toStatus, decidedBy = :decidedBy, decidedAt = :decidedAt "
         + "WHERE id = :id AND status = :fromStatus")
    int decideSuggestion(String id, String fromStatus, String toStatus, String decidedBy, long decidedAt);

    // ---- approvals -------------------------------------------------------------------------

    @Insert
    void insertApproval(ApprovalEntity approval);

    @Query("SELECT * FROM approvals WHERE versionId = :versionId ORDER BY createdAt")
    List<ApprovalEntity> approvals(String versionId);

    @Query("SELECT * FROM approvals WHERE versionId = :versionId ORDER BY createdAt")
    LiveData<List<ApprovalEntity>> observeApprovals(String versionId);

    @Query("SELECT * FROM approvals WHERE versionId = :versionId AND stage = :stage ORDER BY createdAt")
    List<ApprovalEntity> approvalsAtStage(String versionId, String stage);
}
