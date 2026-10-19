package com.buildvisionai.ui.versions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.buildvisionai.AppContainer;
import com.buildvisionai.R;
import com.buildvisionai.collaboration.Permission;
import com.buildvisionai.collaboration.PermissionPolicy;
import com.buildvisionai.collaboration.ReviewGuide;
import com.buildvisionai.collaboration.Transition;
import com.buildvisionai.models.AppUser;
import com.buildvisionai.models.Approval;
import com.buildvisionai.models.Comment;
import com.buildvisionai.models.Member;
import com.buildvisionai.models.Project;
import com.buildvisionai.models.ProjectVersion;
import com.buildvisionai.models.Suggestion;
import com.buildvisionai.models.UserRole;
import com.buildvisionai.repository.RepoCallback;
import com.buildvisionai.ui.common.Event;
import com.buildvisionai.utils.Ids;

import java.util.Collections;
import java.util.List;

/**
 * Version history, review workflow, discussion and team of one project. One version is "selected"
 * at a time; the review, approvals, comments and suggestions shown are that version's. Every action
 * is done as the team member the device is currently acting as, and the repositories check the
 * permission again, so a button that slipped through would still be refused with a message.
 */
public final class VersionsViewModel extends ViewModel {
    public static final class Factory implements ViewModelProvider.Factory {
        private final AppContainer app;
        private final String projectId;
        @Nullable private final String versionId;

        public Factory(AppContainer app, String projectId, @Nullable String versionId) {
            this.app = app;
            this.projectId = projectId;
            this.versionId = versionId;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new VersionsViewModel(app, projectId, versionId);
        }
    }

    /** A message for the person: a string resource, or text that came from a rule (an error). */
    public static final class Notice {
        @StringRes public final int resId;
        @Nullable public final String text;

        private Notice(int resId, @Nullable String text) {
            this.resId = resId;
            this.text = text;
        }

        static Notice of(@StringRes int resId) { return new Notice(resId, null); }

        static Notice text(@Nullable String text) { return new Notice(0, text); }
    }

    private final AppContainer app;
    private final String projectId;

    private final MutableLiveData<Project> project = new MutableLiveData<>();
    private final MutableLiveData<String> selected = new MutableLiveData<>();
    private final MutableLiveData<String> acting = new MutableLiveData<>();
    private final MutableLiveData<Event<Notice>> messages = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> openVersion = new MutableLiveData<>();

    private final LiveData<List<ProjectVersion>> versions;
    private final LiveData<List<Member>> members;
    private final LiveData<List<Approval>> approvals;
    private final LiveData<List<Comment>> comments;
    private final LiveData<List<Suggestion>> suggestions;

    VersionsViewModel(AppContainer app, String projectId, @Nullable String versionId) {
        this.app = app;
        this.projectId = projectId;
        acting.setValue(app.profile.actingUserId(projectId));
        if (versionId != null) selected.setValue(versionId);

        versions = app.projects.observeVersions(projectId);
        members = app.collaboration.observeMembers(projectId);
        approvals = Transformations.switchMap(selected, id -> id == null
                ? new MutableLiveData<List<Approval>>(Collections.emptyList()) : app.collaboration.observeApprovals(id));
        comments = Transformations.switchMap(selected, id -> id == null
                ? new MutableLiveData<List<Comment>>(Collections.emptyList()) : app.collaboration.observeComments(id));
        suggestions = Transformations.switchMap(selected, id -> id == null
                ? new MutableLiveData<List<Suggestion>>(Collections.emptyList()) : app.collaboration.observeSuggestions(id));

        app.projects.run(() -> app.projects.projectSync(projectId), new RepoCallback<Project>() {
            @Override public void onSuccess(Project p) {
                project.setValue(p);
                if (selected.getValue() == null && p != null) selected.setValue(p.headVersionId);
            }

            @Override public void onError(Exception e) { messages.setValue(new Event<>(Notice.text(e.getMessage()))); }
        });
    }

    public String projectId() { return projectId; }

    public LiveData<Project> project() { return project; }

    public LiveData<String> selected() { return selected; }

    public LiveData<List<ProjectVersion>> versions() { return versions; }

    public LiveData<List<Member>> members() { return members; }

    public LiveData<List<Approval>> approvals() { return approvals; }

    public LiveData<List<Comment>> comments() { return comments; }

    public LiveData<List<Suggestion>> suggestions() { return suggestions; }

    public LiveData<Event<Notice>> messages() { return messages; }

    /** The id of a version to open in the editor (a new draft was made, or the person asked to open one). */
    public LiveData<Event<String>> openVersion() { return openVersion; }

    public LiveData<String> actingUserId() { return acting; }

    public void select(@Nullable String versionId) {
        if (versionId != null && !versionId.equals(selected.getValue())) selected.setValue(versionId);
    }

    /** Which member the device acts as now, or null when that person is no longer on the team. */
    @Nullable
    public Member acting(List<Member> team) {
        String id = acting.getValue();
        for (Member m : team) if (m.userId.equals(id)) return m;
        String own = app.profile.userId();                       // the device's own user, if the chosen person has gone
        for (Member m : team) if (m.userId.equals(own)) return m;
        return null;
    }

    public void setActing(String userId) {
        app.profile.setActingUserId(projectId, userId);
        acting.setValue(userId);
    }

    public static boolean can(@Nullable Member who, Permission p, ProjectVersion v) {
        return who != null && PermissionPolicy.can(who.role, p, v.workflowState);
    }

    // review 
    /** Runs one review step as the acting member. A refusal from the workflow comes back as a message. */
    public void review(ReviewGuide.Action action, ProjectVersion v, @Nullable String comment) {
        String who = acting.getValue();
        String text = comment == null ? "" : comment.trim();
        app.collaboration.run(() -> {
            switch (action) {
                case SUBMIT: return app.collaboration.submitForReviewSync(v.id, who);
                case PROFESSIONAL_APPROVE:
                case PROFESSIONAL_REQUEST_CHANGES:
                    return app.collaboration.professionalDecisionSync(v.id, who, action.decision(), text);
                case OWNER_APPROVE:
                case OWNER_REQUEST_CHANGES:
                    return app.collaboration.ownerDecisionSync(v.id, who, action.decision(), text);
                default: return app.collaboration.lockSync(v.id, who);
            }
        }, new RepoCallback<Transition>() {
            @Override public void onSuccess(Transition t) {
                if (!t.ok) messages.setValue(new Event<>(Notice.text(t.error)));
                else messages.setValue(new Event<>(Notice.of(action == ReviewGuide.Action.SUBMIT ? R.string.versions_submitted
                        : action == ReviewGuide.Action.LOCK ? R.string.versions_locked : R.string.versions_decision_recorded)));
            }

            @Override public void onError(Exception e) { messages.setValue(new Event<>(Notice.text(e.getMessage()))); }
        });
    }

    // versions 

    /** Starts a new draft as a copy of {@code v} (or restores it); the original stays exactly as it was. */
    public void copyToDraft(ProjectVersion v, boolean restore) {
        if (creatingDraft) return;                           // a second tap must not make a second draft
        creatingDraft = true;
        String who = acting.getValue();
        app.projects.run(() -> restore ? app.projects.restoreSync(v.id, who) : app.projects.newDraftSync(v.id, who, null),
                new RepoCallback<ProjectVersion>() {
                    @Override public void onSuccess(ProjectVersion nv) {
                        creatingDraft = false;
                        messages.setValue(new Event<>(Notice.of(R.string.versions_draft_created)));
                        openVersion.setValue(new Event<>(nv.id));
                    }

                    @Override public void onError(Exception e) {
                        creatingDraft = false;
                        messages.setValue(new Event<>(Notice.text(e.getMessage())));
                    }
                });
    }

    /** Main thread only: set when a draft is requested, cleared when the request ends. */
    private boolean creatingDraft;

    public void discard(ProjectVersion v) {
        app.projects.run(() -> app.projects.discardDraftSync(v.id), new RepoCallback<Boolean>() {
            @Override public void onSuccess(Boolean done) {
                messages.setValue(new Event<>(Notice.of(done ? R.string.versions_discarded : R.string.versions_discard_refused)));
            }

            @Override public void onError(Exception e) { messages.setValue(new Event<>(Notice.text(e.getMessage()))); }
        });
    }

    //  discussion 

    public void addComment(ProjectVersion v, String text, RepoCallback<Boolean> done) {
        String who = acting.getValue();
        app.collaboration.run(() -> {
            app.collaboration.addCommentSync(v.id, who, null, text.trim());
            return Boolean.TRUE;
        }, new RepoCallback<Boolean>() {
            @Override public void onSuccess(Boolean ok) { done.onSuccess(ok); }

            @Override public void onError(Exception e) {
                messages.setValue(new Event<>(Notice.text(e.getMessage())));
                done.onError(e);
            }
        });
    }

    public void setResolved(ProjectVersion v, Comment c, boolean resolved) {
        String who = acting.getValue();
        app.collaboration.run(() -> {
            app.collaboration.setCommentResolvedSync(v.id, who, c.id, resolved);
            return Boolean.TRUE;
        }, new RepoCallback<Boolean>() {
            @Override public void onSuccess(Boolean ok) { /* the list is observed and refreshes itself */ }

            @Override public void onError(Exception e) { messages.setValue(new Event<>(Notice.text(e.getMessage()))); }
        });
    }

    //  team 
    public void addMember(String name, UserRole role, @Nullable String registrationNo, RepoCallback<Boolean> done) {
        String who = acting.getValue();
        AppUser user = new AppUser();
        user.id = Ids.newId();
        user.displayName = name.trim();
        String reg = registrationNo == null || registrationNo.trim().isEmpty() ? null : registrationNo.trim();
        app.collaboration.run(() -> {
            app.collaboration.addMemberSync(who, projectId, user, role, reg);
            return Boolean.TRUE;
        }, new RepoCallback<Boolean>() {
            @Override public void onSuccess(Boolean ok) {
                messages.setValue(new Event<>(Notice.of(R.string.versions_member_added)));
                done.onSuccess(ok);
            }

            @Override public void onError(Exception e) {
                messages.setValue(new Event<>(Notice.text(e.getMessage())));
                done.onError(e);
            }
        });
    }

    public void removeMember(Member m) {
        String who = acting.getValue();
        app.collaboration.run(() -> {
            app.collaboration.removeMemberSync(who, projectId, m.userId);
            return Boolean.TRUE;
        }, new RepoCallback<Boolean>() {
            @Override public void onSuccess(Boolean ok) {
                // if the device was acting as the person just removed, go back to the device's own user
                if (m.userId.equals(acting.getValue())) setActing(app.profile.userId());
                messages.setValue(new Event<>(Notice.of(R.string.versions_member_removed)));
            }

            @Override public void onError(Exception e) { messages.setValue(new Event<>(Notice.text(e.getMessage()))); }
        });
    }
}
