package com.buildvisionai.ui.estimate;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.buildvisionai.AppContainer;
import com.buildvisionai.collaboration.Permission;
import com.buildvisionai.collaboration.PermissionPolicy;
import com.buildvisionai.estimation.EstimateRunner;
import com.buildvisionai.estimation.EstimateSettings;
import com.buildvisionai.estimation.Item;
import com.buildvisionai.estimation.Rate;
import com.buildvisionai.estimation.RatePack;
import com.buildvisionai.models.Member;
import com.buildvisionai.models.Project;
import com.buildvisionai.models.ProjectVersion;
import com.buildvisionai.planner.PlanDocument;
import com.buildvisionai.reports.ExportReportWorker;
import com.buildvisionai.repository.ProjectRepository;
import com.buildvisionai.repository.RepoCallback;
import com.buildvisionai.ui.common.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The estimate for one version: runs the quantity and cost engines, re-runs them when assumptions or
 * the project's own prices change, and saves, exports and shares. The drawing is only read here.
 *
 * <p>Two kinds of input, with different rules (see {@code Permission.EDIT_ESTIMATE}): assumptions are part
 * of the design and can change only on a draft by someone who may edit the plan; prices are the project's
 * own quotes and can be changed in any state but locked, by owners, quantity surveyors and contractors.
 */
public final class EstimateViewModel extends ViewModel {
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
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) { return (T) new EstimateViewModel(app, versionId); }
    }

    /** Everything the screen shows; replaced as a whole when something changes. */
    public static final class State {
        public final Project project;
        public final ProjectVersion version;
        public final PlanDocument doc;
        public final EstimateSettings settings;
        /** The prices being used: the built-in pack plus this project's own quotes. */
        public final RatePack pack;
        /** The built-in prices alone, to tell which items carry a quote of the project's own. */
        public final RatePack builtIn;
        public final EstimateRunner.Result run;
        public final List<String> priceWarnings;
        public final Member acting;

        State(Project project, ProjectVersion version, PlanDocument doc, EstimateSettings settings, RatePack pack,
              RatePack builtIn, EstimateRunner.Result run, List<String> priceWarnings, Member acting) {
            this.project = project;
            this.version = version;
            this.doc = doc;
            this.settings = settings;
            this.pack = pack;
            this.builtIn = builtIn;
            this.run = run;
            this.priceWarnings = priceWarnings;
            this.acting = acting;
        }

        State with(EstimateSettings s, RatePack p, EstimateRunner.Result r, List<String> w) {
            return new State(project, version, doc, s, p, builtIn, r, w, acting);
        }

        /** True when the project has its own quote for this item. */
        public boolean hasOwnPrice(Item item) {
            Rate used = pack.base(item), builtin = builtIn.base(item);
            if (used == null || builtin == null) return false;
            return used.min != builtin.min || used.avg != builtin.avg || used.max != builtin.max;
        }

        public boolean can(Permission p) {
            return acting != null && PermissionPolicy.can(acting.role, p, version.workflowState);
        }

        /** Assumptions are frozen with the version, so they follow the right to edit the drawing. */
        public boolean canEditAssumptions() { return can(Permission.EDIT_PLAN); }

        public boolean canEditPrices() { return can(Permission.EDIT_ESTIMATE); }
    }

    private final AppContainer app;
    private final String versionId;
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final MutableLiveData<String> loadError = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private EstimateSettings latestSettings;          // newest assumptions, even before the background save finishes

    EstimateViewModel(AppContainer app, String versionId) {
        this.app = app;
        this.versionId = versionId;
        load();
    }

    public LiveData<State> state() { return state; }

    public LiveData<String> loadError() { return loadError; }

    public LiveData<Event<String>> messages() { return messages; }

    public String versionId() { return versionId; }

    private void load() {
        app.projects.run(() -> {
            ProjectRepository.LoadedVersion lv = app.projects.loadVersionSync(versionId);
            Project p = app.projects.projectSync(lv.version.projectId);
            if (p == null) throw new IllegalStateException("The project of this version no longer exists.");
            Member m = app.collaboration.memberSync(p.projectId, app.profile.actingUserId(p.projectId));
            if (m == null) m = app.collaboration.memberSync(p.projectId, app.profile.userId());
            List<String> warnings = new ArrayList<>();
            RatePack builtIn = EstimateRunner.basePack(p);
            RatePack pack = app.projects.ratePackSync(p.projectId, builtIn, warnings);
            EstimateRunner.Result run = EstimateRunner.run(lv.plan, lv.settings, pack);
            return new State(p, lv.version, lv.plan, lv.settings, pack, builtIn, run, warnings, m);
        }, new RepoCallback<State>() {
            @Override public void onSuccess(State s) {
                latestSettings = s.settings;
                state.setValue(s);
            }

            @Override public void onError(Exception e) { loadError.setValue(e.getMessage() == null ? e.toString() : e.getMessage()); }
        });
    }

    /**
     * Changes assumptions on a draft: saves them (settings only, not the drawing) and re-prices.
     * Changes made in quick succession build on each other even before the first save has finished.
     */
    public void changeSettings(Consumer<EstimateSettings> change) {
        State s = state.getValue();
        if (s == null || !s.canEditAssumptions()) return;
        EstimateSettings next = latestSettings.copy();
        change.accept(next);
        latestSettings = next;
        app.projects.run(() -> {
            app.projects.saveSettingsSync(versionId, next);
            return EstimateRunner.run(s.doc, next, s.pack);
        }, new RepoCallback<EstimateRunner.Result>() {
            @Override public void onSuccess(EstimateRunner.Result r) {
                State cur = state.getValue();
                if (cur != null) state.setValue(cur.with(next, cur.pack, r, cur.priceWarnings));
            }

            @Override public void onError(Exception e) { post(e.getMessage()); }
        });
    }

    /** The prices and numbers after a quote changed. */
    private static final class Repriced {
        final RatePack pack;
        final EstimateRunner.Result run;
        final List<String> warnings;

        Repriced(RatePack pack, EstimateRunner.Result run, List<String> warnings) {
            this.pack = pack;
            this.run = run;
            this.warnings = warnings;
        }
    }

    /** Sets the project's own price for one item (null clears it back to the built-in rate). */
    public void setPrice(Item item, @Nullable Rate rate) {
        State s = state.getValue();
        if (s == null || !s.canEditPrices()) return;
        EstimateSettings settingsNow = latestSettings;
        app.projects.run(() -> {
            if (rate == null) app.projects.clearRateOverrideSync(s.project.projectId, item);
            else app.projects.setRateOverrideSync(s.project.projectId, item, rate);
            List<String> warnings = new ArrayList<>();
            RatePack pack = app.projects.ratePackSync(s.project.projectId, s.builtIn, warnings);
            return new Repriced(pack, EstimateRunner.run(s.doc, settingsNow, pack), warnings);
        }, new RepoCallback<Repriced>() {
            @Override public void onSuccess(Repriced r) {
                State cur = state.getValue();
                if (cur != null) state.setValue(cur.with(settingsNow, r.pack, r.run, r.warnings));
            }

            @Override public void onError(Exception e) { post(e.getMessage()); }
        });
    }

    /** Stores the current numbers with the version, so a later price change can be compared with them. */
    public void saveEstimate(RepoCallback<Boolean> callback) {
        State s = state.getValue();
        if (s == null) return;
        app.projects.run(() -> {
            app.projects.saveEstimateSync(versionId, s.run.cost);
            return Boolean.TRUE;
        }, callback);
    }

    /** A background export: which file kind, and whether its result has already been handed to the person. */
    public static final class ExportJob {
        public final UUID id;
        public final String format;
        private boolean delivered;

        ExportJob(UUID id, String format) {
            this.id = id;
            this.format = format;
        }

        public boolean delivered() { return delivered; }
    }

    private final MutableLiveData<ExportJob> exportJob = new MutableLiveData<>();
    private boolean startingExport;                   // main thread only

    /** The export in progress or last finished; null before the first one. Survives rotation. */
    public LiveData<ExportJob> exportJob() { return exportJob; }

    /** Called once the finished file has been opened in the share sheet, so a rotation does not open it again. */
    public void markDelivered(ExportJob job) { job.delivered = true; }

    /**
     * Starts a background export ("pdf" or "png"). The job is queued from the database thread, after any
     * assumption or price save already waiting there, so the file always matches what the screen shows.
     */
    public void startExport(String format) {
        if (startingExport) return;
        startingExport = true;
        app.projects.run(() -> ExportReportWorker.enqueue(app.appContext(), versionId, format), new RepoCallback<UUID>() {
            @Override public void onSuccess(UUID id) {
                startingExport = false;
                exportJob.setValue(new ExportJob(id, format));
            }

            @Override public void onError(Exception e) {
                startingExport = false;
                post(e.getMessage());
            }
        });
    }

    private void post(@Nullable String text) {
        messages.setValue(new Event<>(text == null ? "Something went wrong." : text));
    }
}
