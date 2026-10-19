package com.buildvisionai;

import android.content.Context;

import com.buildvisionai.ai.AiAssistant;
import com.buildvisionai.ai.OfflineAssistant;
import com.buildvisionai.data.remote.RemoteAiAssistant;
import com.buildvisionai.database.AppDatabase;
import com.buildvisionai.estimation.EstimateSettings;
import com.buildvisionai.estimation.RatePack;
import com.buildvisionai.models.Project;
import com.buildvisionai.planner.PlanDocument;
import com.buildvisionai.repository.AppExecutors;
import com.buildvisionai.repository.CollaborationRepository;
import com.buildvisionai.repository.ProjectRepository;
import com.buildvisionai.data.LocalProfile;

/**
 * Hand-wired dependencies: the database, the background executor, the two repositories and the
 * device user. Small enough that a DI framework would only add build time. ViewModel factories take
 * this container and pick what they need, which keeps them easy to construct in tests.
 */
public final class AppContainer {
    public final AppExecutors executors;
    public final ProjectRepository projects;
    public final CollaborationRepository collaboration;
    public final LocalProfile profile;
    private final Context appContext;

    AppContainer(Context context) {
        appContext = context.getApplicationContext();
        executors = new AppExecutors();
        AppDatabase db = AppDatabase.get(appContext);
        projects = new ProjectRepository(db, executors);
        collaboration = new CollaborationRepository(db, executors);
        profile = new LocalProfile(appContext);
    }

    /**
     * The cloud assistant when the build has a backend address, otherwise the on-device one. Either
     * way it only returns text and proposals; it is never given the editor, so it cannot change a plan.
     */
    public AiAssistant assistantFor(PlanDocument doc, Project project, EstimateSettings settings, RatePack pack) {
        AiAssistant offline = new OfflineAssistant(doc, project, settings, pack);
        if (BuildConfig.AI_BASE_URL.isEmpty()) return offline;
        return new RemoteAiAssistant(BuildConfig.AI_BASE_URL, offline);
    }

    public Context appContext() { return appContext; }
}
