package com.buildvisionai.data.mapper;

import com.buildvisionai.database.entity.ApprovalEntity;
import com.buildvisionai.database.entity.CommentEntity;
import com.buildvisionai.database.entity.MemberEntity;
import com.buildvisionai.database.entity.SuggestionEntity;
import com.buildvisionai.database.entity.UserEntity;
import com.buildvisionai.models.AppUser;
import com.buildvisionai.models.Approval;
import com.buildvisionai.models.ApprovalDecision;
import com.buildvisionai.models.Comment;
import com.buildvisionai.models.Member;
import com.buildvisionai.models.Suggestion;
import com.buildvisionai.models.SuggestionSource;
import com.buildvisionai.models.SuggestionStatus;
import com.buildvisionai.models.UserRole;
import com.buildvisionai.models.WorkflowState;

import java.util.List;

/**
 * People, roles, comments, suggestions and approvals. Where a stored value is not recognised the
 * fallback is always the cautious one: the least privileged role, a suggestion that will not be
 * applied, an approval that does not count.
 */
public final class CollaborationMapper {
    private CollaborationMapper() {}

    public static UserEntity toEntity(AppUser u) {
        UserEntity e = new UserEntity();
        e.id = u.id;
        e.displayName = Codes.orEmpty(u.displayName);
        e.email = u.email;
        e.phone = u.phone;
        e.remoteId = u.remoteId;
        return e;
    }

    public static AppUser toModel(UserEntity e) {
        AppUser u = new AppUser();
        u.id = e.id;
        u.displayName = e.displayName;
        u.email = e.email;
        u.phone = e.phone;
        u.remoteId = e.remoteId;
        return u;
    }

    public static MemberEntity toEntity(Member m) {
        MemberEntity e = new MemberEntity();
        e.projectId = m.projectId;
        e.userId = m.userId;
        e.displayName = Codes.orEmpty(m.displayName);
        e.role = Codes.of(m.role == null ? UserRole.CONTRACTOR : m.role);
        e.registrationNo = m.registrationNo;
        e.addedAt = m.addedAt;
        return e;
    }

    public static Member toModel(MemberEntity e, List<String> warnings) {
        Member m = new Member();
        m.projectId = e.projectId;
        m.userId = e.userId;
        m.displayName = e.displayName;
        m.role = Codes.parse(UserRole.class, e.role, UserRole.CONTRACTOR, warnings, "Member " + e.userId);
        m.registrationNo = e.registrationNo;
        m.addedAt = e.addedAt;
        return m;
    }

    public static CommentEntity toEntity(Comment c) {
        CommentEntity e = new CommentEntity();
        e.id = c.id;
        e.versionId = c.versionId;
        e.elementId = c.elementId;
        e.authorId = c.authorId;
        e.authorName = Codes.orEmpty(c.authorName);
        e.authorRole = Codes.of(c.authorRole == null ? UserRole.CONTRACTOR : c.authorRole);
        e.text = Codes.orEmpty(c.text);
        e.createdAt = c.createdAt;
        e.resolved = c.resolved;
        return e;
    }

    public static Comment toModel(CommentEntity e, List<String> warnings) {
        Comment c = new Comment();
        c.id = e.id;
        c.versionId = e.versionId;
        c.elementId = e.elementId;
        c.authorId = e.authorId;
        c.authorName = e.authorName;
        c.authorRole = Codes.parse(UserRole.class, e.authorRole, UserRole.CONTRACTOR, warnings, "Comment " + e.id);
        c.text = e.text;
        c.createdAt = e.createdAt;
        c.resolved = e.resolved;
        return c;
    }

    public static SuggestionEntity toEntity(Suggestion s) {
        SuggestionEntity e = new SuggestionEntity();
        e.id = s.id;
        e.versionId = s.versionId;
        e.authorId = s.authorId;
        e.authorName = Codes.orEmpty(s.authorName);
        e.source = Codes.of(s.source == null ? SuggestionSource.AI : s.source);
        e.status = Codes.of(s.status == null ? SuggestionStatus.PROPOSED : s.status);
        e.title = Codes.orEmpty(s.title);
        e.rationale = s.rationale;
        e.payloadJson = s.payloadJson;
        e.createdAt = s.createdAt;
        e.decidedBy = s.decidedBy;
        e.decidedAt = s.decidedAt;
        return e;
    }

    public static Suggestion toModel(SuggestionEntity e, List<String> warnings) {
        Suggestion s = new Suggestion();
        s.id = e.id;
        s.versionId = e.versionId;
        s.authorId = e.authorId;
        s.authorName = e.authorName;
        s.source = Codes.parse(SuggestionSource.class, e.source, SuggestionSource.AI, warnings, "Suggestion " + e.id);
        s.status = Codes.parse(SuggestionStatus.class, e.status, SuggestionStatus.WITHDRAWN, warnings, "Suggestion " + e.id);
        s.title = e.title;
        s.rationale = e.rationale;
        s.payloadJson = e.payloadJson;
        s.createdAt = e.createdAt;
        s.decidedBy = e.decidedBy;
        s.decidedAt = e.decidedAt;
        return s;
    }

    public static ApprovalEntity toEntity(Approval a) {
        ApprovalEntity e = new ApprovalEntity();
        e.id = a.id;
        e.versionId = a.versionId;
        e.userId = a.userId;
        e.userName = Codes.orEmpty(a.userName);
        e.role = Codes.of(a.role == null ? UserRole.CONTRACTOR : a.role);
        e.stage = Codes.of(a.stage == null ? WorkflowState.DRAFT : a.stage);
        e.decision = Codes.of(a.decision == null ? ApprovalDecision.CHANGES_REQUESTED : a.decision);
        e.comment = a.comment;
        e.createdAt = a.createdAt;
        return e;
    }

    public static Approval toModel(ApprovalEntity e, List<String> warnings) {
        Approval a = new Approval();
        a.id = e.id;
        a.versionId = e.versionId;
        a.userId = e.userId;
        a.userName = e.userName;
        a.role = Codes.parse(UserRole.class, e.role, UserRole.CONTRACTOR, warnings, "Approval " + e.id);
        a.stage = Codes.parse(WorkflowState.class, e.stage, WorkflowState.DRAFT, warnings, "Approval " + e.id);
        a.decision = Codes.parse(ApprovalDecision.class, e.decision, ApprovalDecision.CHANGES_REQUESTED, warnings, "Approval " + e.id);
        a.comment = e.comment;
        a.createdAt = e.createdAt;
        return a;
    }
}
