package com.buildvisionai.ui.editor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.buildvisionai.AppContainer;
import com.buildvisionai.ai.AiAssistant;
import com.buildvisionai.ai.AssistantReply;
import com.buildvisionai.ai.AssistantRequest;
import com.buildvisionai.ai.PlanSummarizer;
import com.buildvisionai.collaboration.ChangeProposal;
import com.buildvisionai.collaboration.Permission;
import com.buildvisionai.collaboration.PermissionPolicy;
import com.buildvisionai.collaboration.ProposalApplier;
import com.buildvisionai.collaboration.ProposalCodec;
import com.buildvisionai.estimation.EstimateRunner;
import com.buildvisionai.estimation.EstimateSettings;
import com.buildvisionai.estimation.RatePack;
import com.buildvisionai.models.Floor;
import com.buildvisionai.models.Member;
import com.buildvisionai.models.Project;
import com.buildvisionai.models.ProjectVersion;
import com.buildvisionai.models.Suggestion;
import com.buildvisionai.planner.EditorController;
import com.buildvisionai.planner.PlanDocument;
import com.buildvisionai.repository.ProjectRepository;
import com.buildvisionai.repository.RepoCallback;
import com.buildvisionai.ui.common.Event;
import com.buildvisionai.ui.common.MainThreadTimer;
import com.buildvisionai.utils.Debouncer;
import com.buildvisionai.utils.Ids;
import com.buildvisionai.utils.Strings;
import com.buildvisionai.utils.UnitSystem;

import java.util.List;

/**
 * Owns one open version: the document, the editor controller and the autosave. It survives rotation,
 * so a half-drawn wall and the undo history are still there afterwards. Edits are saved a moment
 * after the last change (debounced); {@link #flushSave()} writes at once and is called when the
 * screen is paused or the user leaves for another screen.
 *
 * <p>All database work goes through the single-thread executor in {@link AppContainer}, so a save
 * requested before another screen loads the same version always finishes first.
 */
public final class EditorViewModel extends ViewModel {
    public enum Phase { LOADING, READY, FAILED }

    public enum SaveState { IDLE, PENDING, SAVING, SAVED, FAILED }

    /** Why the plan cannot be edited, for the banner. */
    public enum ReadOnlyReason { NONE, VERSION_FROZEN, ROLE_CANNOT_EDIT }

    private static final long AUTOSAVE_DELAY_MS = 1200;
    private static final String APPLY_LABEL_PREFIX = "Apply suggestion: ";

    public static final class Factory implements ViewModelProvider.Factory {
        private final AppContainer app;
        private final String versionId;

        public Factory(AppContainer app, String versionId) {
            this.app = app;
            this.versionId = versionId;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new EditorViewModel(app, versionId);
        }
    }

    private static final class Loaded {
        final ProjectRepository.LoadedVersion data;
        final Project project;
        final Member acting;

        Loaded(ProjectRepository.LoadedVersion data, Project project, Member acting) {
            this.data = data;
            this.project = project;
            this.acting = acting;
        }
    }

    private final AppContainer app;
    private final String versionId;
    private final Debouncer saver = new Debouncer(new MainThreadTimer(), AUTOSAVE_DELAY_MS);

    private final MutableLiveData<Phase> phase = new MutableLiveData<>(Phase.LOADING);
    private final MutableLiveData<SaveState> saveState = new MutableLiveData<>(SaveState.IDLE);
    private final MutableLiveData<Integer> revision = new MutableLiveData<>(0);
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();

    private String error;
    private String saveError;
    private Project project;
    private ProjectVersion version;
    private PlanDocument doc;
    private EstimateSettings settings;
    private EditorController controller;
    private Member acting;
    private List<String> loadWarnings;
    private int editGeneration;

    EditorViewModel(AppContainer app, String versionId) {
        this.app = app;
        this.versionId = versionId;
        load();
    }

    // loading 

    private void load() {
        app.projects.run(() -> {
            ProjectRepository.LoadedVersion data = app.projects.loadVersionSync(versionId);
            Project p = app.projects.projectSync(data.version.projectId);
            if (p == null) throw new IllegalStateException("The project of this version no longer exists.");
            Member m = app.collaboration.memberSync(p.projectId, app.profile.actingUserId(p.projectId));
            if (m == null) m = app.collaboration.memberSync(p.projectId, app.profile.userId());
            return new Loaded(data, p, m);
        }, new RepoCallback<Loaded>() {
            @Override public void onSuccess(Loaded l) { onLoaded(l); }

            @Override public void onError(Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
                phase.setValue(Phase.FAILED);
            }
        });
    }

    private void onLoaded(Loaded l) {
        version = l.data.version;
        doc = l.data.plan;
        settings = l.data.settings;
        project = l.project;
        acting = l.acting;
        loadWarnings = l.data.warnings;
        if (doc.floors().isEmpty()) doc.putFloor(new Floor(Ids.newId(), 0, Floor.defaultName(0)));
        controller = new EditorController(doc, doc.floors().get(0).id);
        controller.state().units = app.profile.units();
        controller.setReadOnly(!canEdit());
        phase.setValue(Phase.READY);
    }

    //state for the screen 

    public LiveData<Phase> phase() { return phase; }

    public LiveData<SaveState> saveState() { return saveState; }

    /** Changes on every edit, undo or redo; observe it to refresh undo/redo buttons and counts. */
    public LiveData<Integer> revision() { return revision; }

    public LiveData<Event<String>> messages() { return messages; }

    @Nullable public String error() { return error; }

    /** Why the last save failed, when {@link #saveState()} is FAILED. */
    @Nullable public String saveError() { return saveError; }

    public List<String> loadWarnings() { return loadWarnings; }

    public String versionId() { return versionId; }

    public Project project() { return project; }

    public ProjectVersion version() { return version; }

    public EditorController controller() { return controller; }

    /**
     * True once the version has loaded. A sheet that the framework brings back after the process was killed is created
     * before that, while this model is new and still loading; such a sheet closes itself instead of reading a plan that
     * is not there yet.
     */
    public boolean isReady() { return controller != null && phase.getValue() == Phase.READY; }

    public EstimateSettings settings() { return settings; }

    @Nullable public Member acting() { return acting; }

    public String actingUserId() { return acting != null ? acting.userId : app.profile.userId(); }

    public boolean can(Permission p) {
        return acting != null && version != null && PermissionPolicy.can(acting.role, p, version.workflowState);
    }

    public boolean canEdit() { return can(Permission.EDIT_PLAN); }

    public ReadOnlyReason readOnlyReason() {
        if (version == null) return ReadOnlyReason.NONE;
        if (version.isFrozen()) return ReadOnlyReason.VERSION_FROZEN;
        return canEdit() ? ReadOnlyReason.NONE : ReadOnlyReason.ROLE_CANNOT_EDIT;
    }

    public LiveData<List<Suggestion>> suggestions() { return app.collaboration.observeSuggestions(versionId); }

    //  saving 

    /** Call when the document (or the estimate assumptions) changed. */
    public void onDocumentChanged() {
        Integer r = revision.getValue();
        revision.setValue(r == null ? 1 : r + 1);
        requestSave();
    }

    /** Schedules a save a moment from now; a burst of edits produces one write. */
    public void requestSave() {
        if (controller == null || controller.isReadOnly()) return;
        editGeneration++;
        saveState.setValue(SaveState.PENDING);
        saver.submit(this::saveNow);
    }

    /** Writes any pending change now. Safe to call at any time. */
    public void flushSave() { saver.flush(); }

    public void retrySave() { requestSave(); flushSave(); }

    /**
     * Picks up assumptions changed on the estimate screen, so the next autosave does not write the old
     * ones back over them. Skipped while an edit of this screen's own is waiting to be saved: that
     * edit's save carries this screen's assumptions, which were unchanged.
     */
    public void refreshSettings() {
        if (phase.getValue() != Phase.READY || controller == null || controller.isReadOnly()) return;
        int generation = editGeneration;
        app.projects.run(() -> app.projects.settingsSync(versionId), new RepoCallback<EstimateSettings>() {
            @Override public void onSuccess(EstimateSettings fresh) {
                if (fresh != null && generation == editGeneration && !saver.hasPending()) settings = fresh;
            }

            @Override public void onError(Exception e) {
                // keep what is already here; the estimate screen's values are still stored, and the next
                // resume tries again
            }
        });
    }

    private void saveNow() {
        if (doc == null || controller.isReadOnly()) return;
        // The database thread works on copies, so edits made while it writes cannot tear the snapshot.
        PlanDocument snapshot = doc.copy();
        EstimateSettings settingsCopy = settings.copy();
        int generation = editGeneration;
        saveState.setValue(SaveState.SAVING);
        app.projects.run(() -> app.projects.savePlanSync(versionId, snapshot, settingsCopy), new RepoCallback<List<String>>() {
            @Override public void onSuccess(List<String> warnings) {
                // A newer edit may have happened while this one was writing: stay PENDING for it.
                if (generation == editGeneration) saveState.setValue(SaveState.SAVED);
            }

            @Override public void onError(Exception e) {
                saveError = e.getMessage() == null ? "Could not save your changes." : e.getMessage();
                saveState.setValue(SaveState.FAILED);
            }
        });
    }

    @Override
    protected void onCleared() {
        saver.flush();
        if (controller != null) controller.setListener(null);
    }

    // view settings

    public void setUnits(UnitSystem units) {
        app.profile.setUnits(units);
        if (controller != null) {
            controller.state().units = units;
            controller.invalidate();
        }
    }

    //  versions 
    /** Copies this (frozen) version into a new draft the person can edit; the original is untouched. */
    public void createEditableCopy(RepoCallback<ProjectVersion> callback) {
        if (!can(Permission.CREATE_VERSION)) {
            callback.onError(new SecurityException("Your role cannot create a new version."));
            return;
        }
        flushSave();
        app.projects.run(() -> app.projects.newDraftSync(versionId, actingUserId(), null), callback);
    }

    // assistant 

    /**
     * Asks the assistant. The question is answered from a copy of the plan, on the background thread,
     * so a slow reply never freezes drawing and the assistant never sees the live document.
     */
    public void ask(String question, AiAssistant.Callback callback) {
        PlanDocument docCopy = doc.copy();
        EstimateSettings settingsCopy = settings.copy();
        Project projectCopy = project;
        RatePack pack = EstimateRunner.basePack(project);
        app.executors.io(() -> {
            AiAssistant.Callback onMain = new AiAssistant.Callback() {
                @Override public void onReply(AssistantReply reply) { app.executors.main(() -> callback.onReply(reply)); }

                @Override public void onError(String message) { app.executors.main(() -> callback.onError(message)); }
            };
            try {
                EstimateRunner.Result run = EstimateRunner.run(docCopy, settingsCopy, pack);
                String summary = PlanSummarizer.summarize(projectCopy, docCopy, settingsCopy, run.cost);
                AiAssistant assistant = app.assistantFor(docCopy, projectCopy, settingsCopy, pack);
                assistant.ask(new AssistantRequest(question, summary), onMain);
            } catch (RuntimeException e) {
                onMain.onError("The assistant could not read this plan: " + e.getMessage());
            }
        });
    }

    /** Stores an assistant (or professional) proposal for review. Changes nothing in the plan. */
    public void propose(ChangeProposal proposal, RepoCallback<Suggestion> callback) {
        app.collaboration.run(() -> app.collaboration.proposeSync(versionId, actingUserId(), proposal), callback);
    }

    /**
     * Accepts a suggestion: applies it to the open draft as one undo step, then records the decision.
     * If the record cannot be written (someone else decided first, or the role may not decide) the
     * change is taken back so the plan and the record never disagree.
     */
    public void acceptSuggestion(Suggestion suggestion, RepoCallback<ProposalApplier.Result> callback) {
        ChangeProposal proposal;
        try {
            proposal = ProposalCodec.fromJson(suggestion.payloadJson);
        } catch (RuntimeException e) {
            proposal = null;
        }
        if (proposal == null || proposal.ops == null) {
            callback.onError(new IllegalStateException("This suggestion could not be read."));
            return;
        }
        if (!can(Permission.DECIDE_SUGGESTION)) {
            callback.onError(new SecurityException("Only the owner or architect can accept suggestions on a draft."));
            return;
        }
        EstimateSettings settingsBefore = settings.copy();
        ProposalApplier.Result result = ProposalApplier.apply(proposal, controller, settings);
        boolean planChanged = controller.canUndo() && (APPLY_LABEL_PREFIX + proposal.title).equals(controller.undoLabel());
        if (result.applied == 0 && proposal.hasAutomaticChanges()) {
            callback.onError(new IllegalStateException("Nothing could be applied: " + Strings.join("; ", result.skipped)));
            return;
        }
        Runnable revert = () -> {
            if (planChanged) controller.undo();
            settings = settingsBefore;
        };
        app.collaboration.run(() -> app.collaboration.decideSuggestionSync(versionId, actingUserId(), suggestion.id, true),
                new RepoCallback<Boolean>() {
                    @Override public void onSuccess(Boolean decided) {
                        if (!Boolean.TRUE.equals(decided)) {
                            revert.run();
                            callback.onError(new IllegalStateException("Someone else already decided this suggestion."));
                            return;
                        }
                        requestSave();
                        callback.onSuccess(result);
                    }

                    @Override public void onError(Exception e) {
                        revert.run();
                        callback.onError(e);
                    }
                });
    }

    public void rejectSuggestion(Suggestion suggestion, RepoCallback<Boolean> callback) {
        app.collaboration.run(() -> app.collaboration.decideSuggestionSync(versionId, actingUserId(), suggestion.id, false), callback);
    }

    private void post(String text) { messages.setValue(new Event<>(text)); }
}
