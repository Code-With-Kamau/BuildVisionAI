package com.buildvisionai.ui.projects;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.buildvisionai.BuildVisionApp;
import com.buildvisionai.R;
import com.buildvisionai.databinding.DialogNameBinding;
import com.buildvisionai.databinding.FragmentProjectListBinding;
import com.buildvisionai.models.Project;
import com.buildvisionai.ui.common.Event;
import com.buildvisionai.ui.common.Nav;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/** Home screen: the user's projects, a button to start a new one. */
public final class ProjectListFragment extends Fragment {
    private FragmentProjectListBinding b;
    private ProjectListViewModel vm;
    private ProjectAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentProjectListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(this, new ProjectListViewModel.Factory(BuildVisionApp.of(requireContext())))
                .get(ProjectListViewModel.class);

        adapter = new ProjectAdapter(new ProjectAdapter.Listener() {
            @Override public void onOpen(Project p) { openEditor(p); }

            @Override public void onMore(Project p, View anchor) { showMenu(p, anchor); }
        });
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);

        b.fabNew.setOnClickListener(v -> new NewProjectDialog().show(getChildFragmentManager(), NewProjectDialog.TAG));

        b.toolbar.getMenu().findItem(R.id.action_show_archived).setChecked(vm.showArchived());
        b.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_show_archived) {
                item.setChecked(!item.isChecked());
                vm.setShowArchived(item.isChecked());
                return true;
            }
            if (item.getItemId() == R.id.action_profile) {
                askName();
                return true;
            }
            return false;
        });

        vm.projects().observe(getViewLifecycleOwner(), this::showProjects);
        vm.created().observe(getViewLifecycleOwner(), e -> {
            Project p = e.getIfNotHandled();
            if (p != null) openEditor(p);
        });
        vm.messages().observe(getViewLifecycleOwner(), (Event<String> e) -> {
            String m = e.getIfNotHandled();
            if (m != null) Snackbar.make(b.getRoot(), m, Snackbar.LENGTH_LONG).show();
        });
    }

    private void showProjects(List<Project> projects) {
        adapter.submitList(projects);
        boolean empty = projects.isEmpty();
        b.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        b.list.setVisibility(empty ? View.GONE : View.VISIBLE);
        b.emptyTitle.setText(vm.showArchived() ? R.string.projects_empty_archived_title : R.string.projects_empty_title);
        b.emptyBody.setText(vm.showArchived() ? R.string.projects_empty_archived_body : R.string.projects_empty_body);
    }

    private void openEditor(Project p) {
        if (p.headVersionId == null) {                      // should not happen: every project is created with a first version
            Snackbar.make(b.getRoot(), R.string.common_error, Snackbar.LENGTH_LONG).show();
            return;
        }
        Bundle args = new Bundle();
        args.putString("versionId", p.headVersionId);
        Nav.go(this, R.id.action_projects_to_editor, args);
    }

    private void showMenu(Project p, View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenuInflater().inflate(R.menu.menu_project_item, menu.getMenu());
        menu.getMenu().findItem(R.id.action_archive).setVisible(!vm.showArchived());
        menu.getMenu().findItem(R.id.action_unarchive).setVisible(vm.showArchived());
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_versions) {
                Bundle args = new Bundle();
                args.putString("projectId", p.projectId);
                Nav.go(this, R.id.action_projects_to_versions, args);
            } else if (id == R.id.action_archive) {
                vm.setArchived(p, true);
            } else if (id == R.id.action_unarchive) {
                vm.setArchived(p, false);
            } else if (id == R.id.action_delete) {
                confirmDelete(p);
            } else {
                return false;
            }
            return true;
        });
        menu.show();
    }

    private void confirmDelete(Project p) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.projects_delete_title, p.projectName))
                .setMessage(R.string.projects_delete_message)
                .setPositiveButton(R.string.common_delete, (d, w) -> vm.delete(p))
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private void askName() {
        DialogNameBinding d = DialogNameBinding.inflate(getLayoutInflater());
        d.inputProfileName.setText(vm.deviceUserName());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.projects_your_name)
                .setMessage(R.string.profile_name_explain)
                .setView(d.getRoot())
                .setPositiveButton(R.string.common_save, (dlg, w) -> {
                    CharSequence t = d.inputProfileName.getText();
                    vm.setDeviceUserName(t == null ? "" : t.toString());
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b.list.setAdapter(null);
        b = null;
    }
}
