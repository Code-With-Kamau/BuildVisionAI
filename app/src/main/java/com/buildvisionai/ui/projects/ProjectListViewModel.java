package com.buildvisionai.ui.projects;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.buildvisionai.AppContainer;
import com.buildvisionai.models.AppUser;
import com.buildvisionai.models.Project;
import com.buildvisionai.repository.RepoCallback;
import com.buildvisionai.ui.common.Event;

import java.util.List;

/** The project list: live projects (or archived ones), and creating, archiving and deleting. */
public final class ProjectListViewModel extends ViewModel {
    public static final class Factory implements ViewModelProvider.Factory {
        private final AppContainer app;

        public Factory(AppContainer app) { this.app = app; }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) { return (T) new ProjectListViewModel(app); }
    }

    private final AppContainer app;
    private final MutableLiveData<Boolean> showArchived = new MutableLiveData<>(false);
    private final LiveData<List<Project>> projects;
    private final MutableLiveData<Event<Project>> created = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();

    ProjectListViewModel(AppContainer app) {
        this.app = app;
        projects = Transformations.switchMap(showArchived, archived -> app.projects.observeProjects(Boolean.TRUE.equals(archived)));
    }

    public LiveData<List<Project>> projects() { return projects; }

    public LiveData<Event<Project>> created() { return created; }

    public LiveData<Event<String>> messages() { return messages; }

    public boolean showArchived() { return Boolean.TRUE.equals(showArchived.getValue()); }

    public void setShowArchived(boolean value) { showArchived.setValue(value); }

    public String deviceUserName() { return app.profile.displayName(); }

    public void setDeviceUserName(String name) { app.profile.setDisplayName(name); }

    /** Creates the project, its first draft version and the owner membership, then announces it. */
    public void create(Project draft) {
        // The first project names the device user, so later ones are pre-filled and reviews show who acted.
        if (app.profile.displayName().isEmpty()) app.profile.setDisplayName(draft.ownerName);
        AppUser user = app.profile.user();
        app.projects.run(() -> app.projects.createProjectSync(draft, user), new RepoCallback<Project>() {
            @Override public void onSuccess(Project p) { created.setValue(new Event<>(p)); }

            @Override public void onError(Exception e) { post("Could not create the project: " + e.getMessage()); }
        });
    }

    public void setArchived(Project p, boolean archived) {
        app.projects.run(() -> {
            app.projects.setArchivedSync(p.projectId, archived);
            return Boolean.TRUE;
        }, quiet());
    }

    public void delete(Project p) {
        app.projects.run(() -> {
            app.projects.deleteProjectSync(p.projectId);
            return Boolean.TRUE;
        }, quiet());
    }

    private RepoCallback<Boolean> quiet() {
        return new RepoCallback<Boolean>() {
            @Override public void onSuccess(Boolean value) { /* the list is observed, so it refreshes itself */ }

            @Override public void onError(Exception e) { post(e.getMessage() == null ? "Something went wrong." : e.getMessage()); }
        };
    }

    private void post(String text) { messages.setValue(new Event<>(text)); }
}
