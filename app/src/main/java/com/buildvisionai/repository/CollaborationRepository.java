package com.buildvisionai.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.buildvisionai.collaboration.ApprovalWorkflow;
import com.buildvisionai.collaboration.ChangeProposal;
import com.buildvisionai.collaboration.Permission;
import com.buildvisionai.collaboration.PermissionPolicy;
import com.buildvisionai.collaboration.ProposalCodec;
import com.buildvisionai.collaboration.Transition;
import com.buildvisionai.collaboration.VersionPolicy;
import com.buildvisionai.data.mapper.CollaborationMapper;
import com.buildvisionai.database.AppDatabase;
import com.buildvisionai.database.entity.ApprovalEntity;
import com.buildvisionai.database.entity.MemberEntity;
import com.buildvisionai.database.entity.VersionEntity;
import com.buildvisionai.models.AppUser;
import com.buildvisionai.models.Approval;
import com.buildvisionai.models.ApprovalDecision;
import com.buildvisionai.models.Comment;
import com.buildvisionai.models.Member;
import com.buildvisionai.models.Suggestion;
import com.buildvisionai.models.SuggestionStatus;
import com.buildvisionai.models.UserRole;
import com.buildvisionai.models.WorkflowState;
import com.buildvisionai.utils.Ids;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Members, comments, suggestions and the review workflow. Every write first looks up the actor's
 * role on the project and asks {@link PermissionPolicy} and {@link ApprovalWorkflow}; this class only
 * persists what they allow. A refused action comes back as a {@link Transition} with a message (for
 * workflow moves) or a {@link SecurityException} with a message (for the rest) that is fit to show.
 *
 * <p>Methods ending in {@code Sync} block: call them off the main thread.
 */
public final class CollaborationRepository {
    private final AppDatabase db;
    private final AppExecutors executors;

    public CollaborationRepository(AppDatabase db, AppExecutors executors) {
        this.db = db;
        this.executors = executors;
    }

    public <T> void run(Callable<T> work, RepoCallback<T> callback) {
        executors.run(work, callback);
    }

    // observation 
    public LiveData<List<Member>> observeMembers(String projectId) {
        return Transformations.map(db.collaboration().observeMembers(projectId), rows -> {
            List<Member> out = new ArrayList<>(rows.size());
            for (MemberEntity e : rows) out.add(CollaborationMapper.toModel(e, null));
            return out;
        });
    }

    public LiveData<List<Comment>> observeComments(String versionId) {
        return Transformations.map(db.collaboration().observeComments(versionId), rows -> {
            List<Comment> out = new ArrayList<>(rows.size());
            for (var e : rows) out.add(CollaborationMapper.toModel(e, null));
            return out;
        });
    }

    public LiveData<List<Suggestion>> observeSuggestions(String versionId) {
        return Transformations.map(db.collaboration().observeSuggestions(versionId), rows -> {
            List<Suggestion> out = new ArrayList<>(rows.size());
            for (var e : rows) out.add(CollaborationMapper.toModel(e, null));
            return out;
        });
    }

    public LiveData<List<Approval>> observeApprovals(String versionId) {
        return Transformations.map(db.collaboration().observeApprovals(versionId), rows -> {
            List<Approval> out = new ArrayList<>(rows.size());
            for (ApprovalEntity e : rows) out.add(CollaborationMapper.toModel(e, null));
            return out;
        });
    }

    // who is acting 

    private static final class Actor {
        final VersionEntity version;
        final MemberEntity member;
        final UserRole role;
        final WorkflowState state;

        Actor(VersionEntity version, MemberEntity member) {
            this.version = version;
            this.member = member;
            this.role = CollaborationMapper.toModel(member, null).role;
            this.state = com.buildvisionai.data.mapper.ProjectMapper.toModel(version, null).workflowState;
        }
    }

    /** Null when the version does not exist or the user is not a member of its project. */
    private Actor actor(String versionId, String userId) {
        VersionEntity v = db.versions().get(versionId);
        if (v == null) return null;
        MemberEntity m = db.collaboration().member(v.projectId, userId);
        return m == null ? null : new Actor(v, m);
    }

    private Actor require(String versionId, String userId, Permission p) {
        Actor a = actor(versionId, userId);
        if (a == null) throw new SecurityException("You are not a member of this project.");
        if (!PermissionPolicy.can(a.role, p, a.state)) {
            throw new SecurityException(a.role.label + "s cannot do this while the design is "
                    + a.state.name().toLowerCase().replace('_', ' ') + ".");
        }
        return a;
    }

    // reading members 
    public List<Member> membersSync(String projectId) {
        List<Member> out = new ArrayList<>();
        for (MemberEntity e : db.collaboration().members(projectId)) out.add(CollaborationMapper.toModel(e, null));
        return out;
    }

    public List<Approval> approvalsSync(String versionId) {
        List<Approval> out = new ArrayList<>();
        for (ApprovalEntity e : db.collaboration().approvals(versionId)) out.add(CollaborationMapper.toModel(e, null));
        return out;
    }

    /** Null when the person is not a member of the project. */
    public Member memberSync(String projectId, String userId) {
        MemberEntity e = db.collaboration().member(projectId, userId);
        return e == null ? null : CollaborationMapper.toModel(e, null);
    }

    // members

    /** Adds a person to the project. Only an owner can; there is exactly one owner, so OWNER cannot be assigned here. */
    public void addMemberSync(String actorId, String projectId, AppUser user, UserRole role, String registrationNo) {
        MemberEntity actor = db.collaboration().member(projectId, actorId);
        if (actor == null || !PermissionPolicy.can(CollaborationMapper.toModel(actor, null).role, Permission.MANAGE_MEMBERS, WorkflowState.DRAFT)) {
            throw new SecurityException("Only the project owner can add people.");
        }
        if (role == UserRole.OWNER) throw new IllegalArgumentException("A project has one owner; choose another role.");
        Member m = new Member();
        m.projectId = projectId;
        m.userId = user.id;
        m.displayName = user.displayName;
        m.role = role;
        m.registrationNo = registrationNo;
        m.addedAt = System.currentTimeMillis();
        db.runInTransaction(() -> {
            db.collaboration().upsertUser(CollaborationMapper.toEntity(user));
            db.collaboration().upsertMember(CollaborationMapper.toEntity(m));
        });
    }

    public void removeMemberSync(String actorId, String projectId, String userId) {
        MemberEntity actor = db.collaboration().member(projectId, actorId);
        if (actor == null || !PermissionPolicy.can(CollaborationMapper.toModel(actor, null).role, Permission.MANAGE_MEMBERS, WorkflowState.DRAFT)) {
            throw new SecurityException("Only the project owner can remove people.");
        }
        MemberEntity target = db.collaboration().member(projectId, userId);
        if (target != null && CollaborationMapper.toModel(target, null).role == UserRole.OWNER) {
            throw new IllegalArgumentException("The owner cannot be removed.");
        }
        db.collaboration().removeMember(projectId, userId);
    }

    // comments

    public Comment addCommentSync(String versionId, String userId, String elementId, String text) {
        Actor a = require(versionId, userId, Permission.COMMENT);
        Comment c = new Comment();
        c.id = Ids.newId();
        c.versionId = versionId;
        c.elementId = elementId;
        c.authorId = userId;
        c.authorName = a.member.displayName;
        c.authorRole = a.role;
        c.text = text;
        c.createdAt = System.currentTimeMillis();
        db.collaboration().insertComment(CollaborationMapper.toEntity(c));
        return c;
    }

    public void setCommentResolvedSync(String versionId, String userId, String commentId, boolean resolved) {
        require(versionId, userId, Permission.COMMENT);
        db.collaboration().setCommentResolved(commentId, resolved);
    }

    // suggestions 
    /**
     * Stores a proposed change for people to accept or reject. Nothing in the plan changes here, for
     * a human's proposal or the AI assistant's alike: the change is applied only when the owner or
     * architect accepts it on a draft.
     */
    public Suggestion proposeSync(String versionId, String userId, ChangeProposal proposal) {
        Actor a = require(versionId, userId, Permission.SUGGEST_CHANGES);
        Suggestion s = new Suggestion();
        s.id = Ids.newId();
        s.versionId = versionId;
        s.authorId = userId;
        s.authorName = a.member.displayName;
        s.source = proposal.source;
        s.status = SuggestionStatus.PROPOSED;
        s.title = proposal.title == null ? "Suggested change" : proposal.title;
        s.rationale = proposal.rationale;
        s.payloadJson = ProposalCodec.toJson(proposal);
        s.createdAt = System.currentTimeMillis();
        db.collaboration().insertSuggestion(CollaborationMapper.toEntity(s));
        return s;
    }

    /**
     * Records the decision on a suggestion if it is still waiting; returns false when someone else
     * got there first. Accepting is allowed on a draft only. The caller applies an accepted proposal
     * to the editor <em>before</em> calling this and undoes it when this returns false, so the plan
     * and the record cannot disagree.
     */
    public boolean decideSuggestionSync(String versionId, String userId, String suggestionId, boolean accept) {
        require(versionId, userId, Permission.DECIDE_SUGGESTION);
        return db.collaboration().decideSuggestion(suggestionId, SuggestionStatus.PROPOSED.name(),
                (accept ? SuggestionStatus.ACCEPTED : SuggestionStatus.REJECTED).name(), userId, System.currentTimeMillis()) == 1;
    }

    // review workflow 

    public Transition submitForReviewSync(String versionId, String userId) {
        Actor a = actor(versionId, userId);
        if (a == null) return Transition.denied(WorkflowState.DRAFT, "You are not a member of this project.");
        boolean hasContent = db.plans().wallCount(versionId) > 0;
        Transition t = ApprovalWorkflow.submitForReview(a.state, a.role, hasContent);
        if (t.ok) applyState(a, t, null, null);
        return t;
    }

    /** A professional's decision on a version in professional review; records it and moves the version when the round is complete. */
    public Transition professionalDecisionSync(String versionId, String userId, ApprovalDecision decision, String comment) {
        Actor a = actor(versionId, userId);
        if (a == null) return Transition.denied(WorkflowState.DRAFT, "You are not a member of this project.");
        List<Approval> history = new ArrayList<>();
        for (ApprovalEntity e : db.collaboration().approvals(versionId)) history.add(CollaborationMapper.toModel(e, null));
        Transition t = ApprovalWorkflow.professionalDecision(a.state, a.role, userId, decision,
                ApprovalWorkflow.currentRound(history), ApprovalWorkflow.DEFAULT_REQUIRED_PROFESSIONALS);
        if (t.ok) applyState(a, t, decision, comment);
        return t;
    }

    public Transition ownerDecisionSync(String versionId, String userId, ApprovalDecision decision, String comment) {
        Actor a = actor(versionId, userId);
        if (a == null) return Transition.denied(WorkflowState.DRAFT, "You are not a member of this project.");
        Transition t = ApprovalWorkflow.ownerDecision(a.state, a.role, decision);
        if (t.ok) applyState(a, t, decision, comment);
        return t;
    }

    public Transition lockSync(String versionId, String userId) {
        Actor a = actor(versionId, userId);
        if (a == null) return Transition.denied(WorkflowState.DRAFT, "You are not a member of this project.");
        Transition t = ApprovalWorkflow.lock(a.state, a.role);
        if (t.ok) applyState(a, t, ApprovalDecision.APPROVED, "Version locked");
        return t;
    }

    /** Writes the decision record (when there is one) and the new state in one transaction. */
    private void applyState(Actor a, Transition t, ApprovalDecision decision, String comment) {
        long now = System.currentTimeMillis();
        db.runInTransaction(() -> {
            if (decision != null) {
                Approval rec = new Approval();
                rec.id = Ids.newId();
                rec.versionId = a.version.id;
                rec.userId = a.member.userId;
                rec.userName = a.member.displayName;
                rec.role = a.role;
                rec.stage = t.from;
                rec.decision = decision;
                rec.comment = comment;
                rec.createdAt = now;
                db.collaboration().insertApproval(CollaborationMapper.toEntity(rec));
            }
            if (t.to != t.from) {
                db.versions().setWorkflowState(a.version.id, t.to.name());
                db.projects().setStatusIfHead(a.version.projectId, a.version.id, VersionPolicy.projectStatus(stateOnly(a, t.to)).name(), now);
            }
        });
    }

    private static com.buildvisionai.models.ProjectVersion stateOnly(Actor a, WorkflowState to) {
        com.buildvisionai.models.ProjectVersion v = com.buildvisionai.data.mapper.ProjectMapper.toModel(a.version, null);
        v.workflowState = to;
        return v;
    }
}
