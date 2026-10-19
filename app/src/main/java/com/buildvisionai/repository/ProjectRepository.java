package com.buildvisionai.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.buildvisionai.collaboration.VersionPolicy;
import com.buildvisionai.data.mapper.EstimateMapper;
import com.buildvisionai.data.mapper.PlanMapper;
import com.buildvisionai.data.mapper.ProjectMapper;
import com.buildvisionai.database.AppDatabase;
import com.buildvisionai.database.EstimateRows;
import com.buildvisionai.database.PlanRows;
import com.buildvisionai.database.entity.EstimateEntity;
import com.buildvisionai.database.entity.EstimateSettingsEntity;
import com.buildvisionai.database.entity.VersionEntity;
import com.buildvisionai.estimation.CostEstimate;
import com.buildvisionai.estimation.EstimateSettings;
import com.buildvisionai.estimation.Item;
import com.buildvisionai.estimation.Rate;
import com.buildvisionai.estimation.RatePack;
import com.buildvisionai.models.AppUser;
import com.buildvisionai.models.EstimateSnapshot;
import com.buildvisionai.models.Floor;
import com.buildvisionai.models.Member;
import com.buildvisionai.models.Project;
import com.buildvisionai.models.ProjectStatus;
import com.buildvisionai.models.ProjectVersion;
import com.buildvisionai.models.UserRole;
import com.buildvisionai.models.WorkflowState;
import com.buildvisionai.data.mapper.CollaborationMapper;
import com.buildvisionai.planner.PlanDocument;
import com.buildvisionai.utils.Ids;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Projects, versions, plans and estimates. The methods ending in {@code Sync} block and must be
 * called off the main thread (use {@link #run} or {@link AppExecutors#io}); the {@code observe...}
 * methods return LiveData that Room keeps up to date.
 *
 * <p>The version rules live in two places on purpose: {@link VersionPolicy} here (so the user gets a
 * clear message) and the triggers in {@code VersionGuard} (so a bug cannot get past it).
 *
 * <p>Saving replaces the whole plan of the version in one transaction. That is simple and safe at
 * the size of a house plan (a few hundred elements, a few thousand rows); for much larger plans the
 * edit transactions already carry exactly which elements changed and could drive row-level writes.
 */
public final class ProjectRepository {
    /** Stored estimates kept per version; older ones are pruned. */
    public static final int MAX_STORED_ESTIMATES = 20;

    /** A version opened for work: its header, drawing, assumptions and anything the mapper had to repair. */
    public static final class LoadedVersion {
        public final ProjectVersion version;
        public final PlanDocument plan;
        public final EstimateSettings settings;
        public final List<String> warnings;

        LoadedVersion(ProjectVersion version, PlanDocument plan, EstimateSettings settings, List<String> warnings) {
            this.version = version;
            this.plan = plan;
            this.settings = settings;
            this.warnings = warnings;
        }
    }

    private final AppDatabase db;
    private final AppExecutors executors;

    public ProjectRepository(AppDatabase db, AppExecutors executors) {
        this.db = db;
        this.executors = executors;
    }

    public <T> void run(Callable<T> work, RepoCallback<T> callback) {
        executors.run(work, callback);
    }

    //  observation 
    public LiveData<List<Project>> observeProjects(boolean archived) {
        return Transformations.map(db.projects().observeAll(archived), rows -> {
            List<Project> out = new ArrayList<>(rows.size());
            for (var e : rows) out.add(ProjectMapper.toModel(e, null));
            return out;
        });
    }

    public LiveData<List<ProjectVersion>> observeVersions(String projectId) {
        return Transformations.map(db.versions().observeForProject(projectId), rows -> {
            List<ProjectVersion> out = new ArrayList<>(rows.size());
            for (VersionEntity e : rows) out.add(ProjectMapper.toModel(e, null));
            return out;
        });
    }

    //  projects 

    /**
     * Creates a project with its first draft version, a ground floor, default estimate assumptions
     * and the creator as its owner, all in one transaction. {@code draft} supplies the details the
     * user typed; ids, dates and status are set here.
     */
    public Project createProjectSync(Project draft, AppUser owner) {
        long now = System.currentTimeMillis();
        Project p = draft;
        p.projectId = Ids.newId();
        p.createdAt = now;
        p.updatedAt = now;
        p.status = ProjectStatus.DRAFT;

        ProjectVersion v = new ProjectVersion();
        v.id = Ids.newId();
        v.projectId = p.projectId;
        v.number = 1;
        v.label = "Version 1";
        v.createdAt = now;
        v.createdBy = owner.id;
        v.workflowState = WorkflowState.DRAFT;
        p.headVersionId = v.id;

        PlanDocument doc = new PlanDocument();
        doc.putFloor(new Floor(Ids.newId(), 0, Floor.defaultName(0)));
        PlanRows rows = PlanMapper.toRows(v.id, doc, null);

        Member m = new Member();
        m.projectId = p.projectId;
        m.userId = owner.id;
        m.displayName = owner.displayName;
        m.role = UserRole.OWNER;
        m.addedAt = now;

        db.runInTransaction(() -> {
            db.projects().upsert(ProjectMapper.toEntity(p, false));
            db.versions().upsert(ProjectMapper.toEntity(v));
            db.plans().replacePlan(v.id, rows);
            db.estimates().upsertSettings(EstimateMapper.toEntity(v.id, new EstimateSettings()));
            db.collaboration().upsertUser(CollaborationMapper.toEntity(owner));
            db.collaboration().upsertMember(CollaborationMapper.toEntity(m));
        });
        return p;
    }

    public Project projectSync(String projectId) {
        var e = db.projects().get(projectId);
        return e == null ? null : ProjectMapper.toModel(e, null);
    }

    public void setArchivedSync(String projectId, boolean archived) {
        db.projects().setArchived(projectId, archived, System.currentTimeMillis());
    }

    /** Deletes the project and everything under it. The UI must have asked the user to confirm. */
    public void deleteProjectSync(String projectId) {
        db.projects().delete(projectId);
    }

    //  versions and plans 
    public List<ProjectVersion> versionsSync(String projectId) {
        List<ProjectVersion> out = new ArrayList<>();
        for (VersionEntity e : db.versions().listForProject(projectId)) out.add(ProjectMapper.toModel(e, null));
        return out;
    }

    public ProjectVersion versionSync(String versionId) {
        VersionEntity e = db.versions().get(versionId);
        return e == null ? null : ProjectMapper.toModel(e, null);
    }

    public LoadedVersion loadVersionSync(String versionId) {
        VersionEntity ve = db.versions().get(versionId);
        if (ve == null) throw new IllegalArgumentException("No such version: " + versionId);
        List<String> warnings = new ArrayList<>();
        ProjectVersion version = ProjectMapper.toModel(ve, warnings);
        PlanRows rows = db.plans().loadPlan(versionId);
        PlanDocument plan = PlanMapper.toDocument(rows, warnings);
        EstimateSettingsEntity se = db.estimates().settings(versionId);
        EstimateSettings settings = se == null ? new EstimateSettings() : EstimateMapper.toSettings(se, warnings);
        return new LoadedVersion(version, plan, settings, warnings);
    }

    /**
     * Writes the drawing (and the estimate assumptions, when given) of a draft version. Refuses any
     * other state with a message the UI can show. Returns what the mapper left out, normally nothing.
     */
    public List<String> savePlanSync(String versionId, PlanDocument plan, EstimateSettings settings) {
        VersionEntity ve = db.versions().get(versionId);
        if (ve == null) throw new IllegalArgumentException("No such version: " + versionId);
        ProjectVersion v = ProjectMapper.toModel(ve, null);
        if (!VersionPolicy.isEditable(v)) {
            throw new IllegalStateException("Version " + v.number + " is " + v.workflowState.name().toLowerCase().replace('_', ' ')
                    + " and cannot be changed. Create a new draft to make changes.");
        }
        List<String> warnings = new ArrayList<>();
        PlanRows rows = PlanMapper.toRows(versionId, plan, warnings);
        EstimateSettingsEntity se = settings == null ? null : EstimateMapper.toEntity(versionId, settings);
        long now = System.currentTimeMillis();
        db.runInTransaction(() -> {
            db.plans().replacePlan(versionId, rows);
            if (se != null) db.estimates().upsertSettings(se);
            db.projects().touch(ve.projectId, now);
        });
        return warnings;
    }

    /**
     * Opens a version for editing: a draft is edited in place, anything else gets a new draft copy
     * (the original is never touched). Check {@code VersionPolicy.onEditRequested} first to tell the
     * user which of the two is about to happen.
     */
    public ProjectVersion openForEditSync(String versionId, String userId) {
        ProjectVersion v = versionSync(versionId);
        if (v == null) throw new IllegalArgumentException("No such version: " + versionId);
        if (VersionPolicy.onEditRequested(v) == VersionPolicy.OpenAction.EDIT_IN_PLACE) return v;
        return newDraftSync(versionId, userId, null);
    }

    /** A new draft that starts as a copy of {@code sourceVersionId} (plan and assumptions); it becomes the head. */
    public ProjectVersion newDraftSync(String sourceVersionId, String userId, String label) {
        return copyVersion(sourceVersionId, userId, label, false);
    }

    /** Brings an old version back as a new draft; the old one stays exactly as it was. */
    public ProjectVersion restoreSync(String oldVersionId, String userId) {
        return copyVersion(oldVersionId, userId, null, true);
    }

    private ProjectVersion copyVersion(String sourceId, String userId, String label, boolean restore) {
        VersionEntity se = db.versions().get(sourceId);
        if (se == null) throw new IllegalArgumentException("No such version: " + sourceId);
        ProjectVersion source = ProjectMapper.toModel(se, null);
        List<ProjectVersion> all = versionsSync(se.projectId);
        long now = System.currentTimeMillis();
        ProjectVersion nv = restore
                ? VersionPolicy.restore(source, all, userId, now)
                : VersionPolicy.newDraftFrom(source, all, userId, now, label);
        db.runInTransaction(() -> {
            db.versions().upsert(ProjectMapper.toEntity(nv));
            db.plans().clonePlan(sourceId, nv.id);
            db.estimates().copySettings(sourceId, nv.id);
            db.projects().setHead(se.projectId, nv.id, VersionPolicy.projectStatus(nv).name(), now);
        });
        return nv;
    }

    /**
     * Throws away a draft nobody has submitted. Returns false, changing nothing, when the version is not
     * a draft (the guard trigger would refuse too) or is the project's only version: a project always
     * keeps one version to open. When the discarded draft was the head, the newest remaining version
     * becomes the head, so the project list never points at a version that is gone.
     */
    public boolean discardDraftSync(String versionId) {
        VersionEntity ve = db.versions().get(versionId);
        if (ve == null) return false;
        boolean[] done = {false};
        db.runInTransaction(() -> {
            ProjectVersion newest = null;
            int remaining = 0;
            for (ProjectVersion v : versionsSync(ve.projectId)) {
                if (v.id.equals(versionId)) continue;
                remaining++;
                if (newest == null || v.number > newest.number) newest = v;
            }
            if (remaining == 0 || newest == null) return;
            var project = db.projects().get(ve.projectId);
            if (db.versions().discardDraft(versionId) != 1) return;
            if (project != null && versionId.equals(project.headVersionId)) {
                db.projects().setHead(ve.projectId, newest.id, VersionPolicy.projectStatus(newest).name(), System.currentTimeMillis());
            }
            done[0] = true;
        });
        return done[0];
    }

    /** The estimate assumptions of a version (defaults when none were stored). */
    public EstimateSettings settingsSync(String versionId) {
        EstimateSettingsEntity se = db.estimates().settings(versionId);
        return se == null ? new EstimateSettings() : EstimateMapper.toSettings(se, new ArrayList<>());
    }

    /**
     * Saves the assumptions of a draft without touching the drawing. Like {@link #savePlanSync}, any
     * other state is refused with a message the UI can show (the guard trigger would refuse it too).
     */
    public void saveSettingsSync(String versionId, EstimateSettings settings) {
        VersionEntity ve = db.versions().get(versionId);
        if (ve == null) throw new IllegalArgumentException("No such version: " + versionId);
        ProjectVersion v = ProjectMapper.toModel(ve, null);
        if (!VersionPolicy.isEditable(v)) {
            throw new IllegalStateException("Version " + v.number + " is " + v.workflowState.name().toLowerCase().replace('_', ' ')
                    + ", so its assumptions are frozen. Create a new draft to change them.");
        }
        long now = System.currentTimeMillis();
        db.runInTransaction(() -> {
            db.estimates().upsertSettings(EstimateMapper.toEntity(versionId, settings));
            db.projects().touch(ve.projectId, now);
        });
    }

    //  estimates 

    public void saveEstimateSync(String versionId, CostEstimate estimate) {
        EstimateRows rows = EstimateMapper.toRows(Ids.newId(), versionId, System.currentTimeMillis(), estimate);
        db.runInTransaction(() -> {
            db.estimates().saveEstimate(rows);
            db.estimates().prune(versionId, MAX_STORED_ESTIMATES);
        });
    }

    public EstimateSnapshot latestEstimateSync(String versionId) {
        EstimateEntity e = db.estimates().latestFor(versionId);
        if (e == null) return null;
        EstimateRows rows = db.estimates().load(e.id);
        return rows == null ? null : EstimateMapper.toSnapshot(rows);
    }

    public List<EstimateEntity> estimateHistorySync(String versionId) {
        return db.estimates().historyFor(versionId);
    }

    /** The pack to price with: the base pack plus this project's own quotes, if any. */
    public RatePack ratePackSync(String projectId, RatePack base, List<String> warnings) {
        return EstimateMapper.applyOverrides(base, db.estimates().rateOverrides(projectId), warnings);
    }

    public void setRateOverrideSync(String projectId, Item item, Rate rate) {
        db.estimates().upsertRateOverride(EstimateMapper.toEntity(projectId, item, rate, System.currentTimeMillis()));
    }

    public void clearRateOverrideSync(String projectId, Item item) {
        db.estimates().deleteRateOverride(projectId, item.code());
    }
}
