package com.buildvisionai.ui.editor;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.buildvisionai.BuildVisionApp;
import com.buildvisionai.R;
import com.buildvisionai.databinding.FragmentEditorBinding;
import com.buildvisionai.models.Floor;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.ProjectVersion;
import com.buildvisionai.planner.EditorController;
import com.buildvisionai.planner.EditorListener;
import com.buildvisionai.planner.EditorState;
import com.buildvisionai.planner.FloorPlan;
import com.buildvisionai.planner.ScaleModel;
import com.buildvisionai.planner.ToolType;
import com.buildvisionai.planner.props.ToolOptions;
import com.buildvisionai.repository.RepoCallback;
import com.buildvisionai.ui.common.ElementLabels;
import com.buildvisionai.ui.common.Nav;
import com.buildvisionai.utils.UnitSystem;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * The drawing screen: the floor-plan canvas, the tool strip, the options for the active tool, the
 * actions for the selection, and the entry points to the estimate, the versions screen, the assistant
 * and the suggestions. All state lives in {@link EditorViewModel}; this class is wiring and widgets.
 */
public final class EditorFragment extends Fragment {
    public static final String ARG_VERSION_ID = "versionId";

    /** The view model of an editor fragment; the bottom sheets use this to share it with their host. */
    static EditorViewModel viewModelOf(Fragment editor) {
        String versionId = editor.requireArguments().getString(ARG_VERSION_ID);
        return new ViewModelProvider(editor, new EditorViewModel.Factory(BuildVisionApp.of(editor.requireContext()), versionId))
                .get(EditorViewModel.class);
    }

    private FragmentEditorBinding b;
    private EditorViewModel vm;
    private boolean rebuildingChips;
    private int shownScaleRatio = -1;
    private int suggestionCount;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentEditorBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = viewModelOf(this);
        b.toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        b.toolbar.setOnMenuItemClickListener(this::onMenuItem);

        vm.phase().observe(getViewLifecycleOwner(), phase -> {
            b.loading.setVisibility(phase == EditorViewModel.Phase.LOADING ? View.VISIBLE : View.GONE);
            b.loadError.setVisibility(phase == EditorViewModel.Phase.FAILED ? View.VISIBLE : View.GONE);
            if (phase == EditorViewModel.Phase.FAILED) b.loadError.setText(getString(R.string.editor_load_failed, vm.error()));
            if (phase == EditorViewModel.Phase.READY) setUpEditor();
        });
    }

    //  set-up once the version has loaded
    private void setUpEditor() {
        EditorController c = vm.controller();
        b.planView.attach(c, new EditorListener() {
            @Override public void onInvalidate() { updateScaleLabel(); }

            @Override public void onDocumentChanged() {
                vm.onDocumentChanged();
                refreshChrome();
            }

            @Override public void onSelectionChanged() { refreshSelection(); }

            @Override public void onToolChanged() {
                syncToolChips();
                refreshOptions();
            }

            @Override public void onMessage(String text) { Snackbar.make(b.getRoot(), text, Snackbar.LENGTH_SHORT).show(); }

            @Override public void onRoomEditRequested(String roomId) { showProperties(roomId); }
        });

        for (int i = 0; i < b.toolGroup.getChildCount(); i++) {
            Chip chip = (Chip) b.toolGroup.getChildAt(i);
            chip.setEnabled(!c.isReadOnly() || ToolType.SELECT.name().equals(chip.getTag()));
        }
        b.toolGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            View chip = group.findViewById(ids.get(0));
            if (chip != null && chip.getTag() instanceof String) c.setTool(ToolType.valueOf((String) chip.getTag()));
        });
        b.optionGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (rebuildingChips || ids.isEmpty()) return;
            View chip = group.findViewById(ids.get(0));
            if (chip != null && chip.getTag() instanceof String) {
                ToolOptions.select(c.state().tool, c.state(), (String) chip.getTag());
                c.invalidate();
            }
        });
        b.btnDone.setOnClickListener(v -> c.finishDrawing());

        b.btnZoomIn.setOnClickListener(v -> b.planView.zoomIn());
        b.btnZoomOut.setOnClickListener(v -> b.planView.zoomOut());
        b.btnFit.setOnClickListener(v -> b.planView.fitToContent());
        b.btnScale.setOnClickListener(v -> chooseScale());
        b.btnFloor.setOnClickListener(v -> chooseFloor());

        b.btnProps.setOnClickListener(v -> {
            if (c.selection().isSingle()) showProperties(c.selection().primaryId());
        });
        b.btnDuplicate.setOnClickListener(v -> c.duplicateSelection());
        b.btnRotate.setOnClickListener(v -> c.rotateSelection(90));
        b.btnDelete.setOnClickListener(v -> c.deleteSelection());

        vm.saveState().observe(getViewLifecycleOwner(), s -> {
            refreshChrome();
            if (s == EditorViewModel.SaveState.FAILED) {
                Snackbar.make(b.getRoot(), vm.saveError() == null ? getString(R.string.common_error) : vm.saveError(), Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.common_retry, v -> vm.retrySave()).show();
            }
        });
        vm.suggestions().observe(getViewLifecycleOwner(), list -> {
            int waiting = 0;
            for (var s : list) if (s.status == com.buildvisionai.models.SuggestionStatus.PROPOSED) waiting++;
            suggestionCount = waiting;
            refreshMenu();
        });
        vm.messages().observe(getViewLifecycleOwner(), e -> {
            String m = e.getIfNotHandled();
            if (m != null) Snackbar.make(b.getRoot(), m, Snackbar.LENGTH_LONG).show();
        });

        List<String> warnings = vm.loadWarnings();
        if (warnings != null && !warnings.isEmpty()) {
            Snackbar.make(b.getRoot(), getString(R.string.editor_load_warnings, warnings.size()), Snackbar.LENGTH_LONG).show();
        }

        syncToolChips();
        refreshBanner();
        refreshChrome();
        refreshSelection();
        refreshOptions();
        updateScaleLabel();
    }

    //  toolbar 
    private boolean onMenuItem(MenuItem item) {
        if (vm.controller() == null) return false;
        EditorController c = vm.controller();
        int id = item.getItemId();
        if (id == R.id.action_undo) {
            c.undo();
        } else if (id == R.id.action_redo) {
            c.redo();
        } else if (id == R.id.action_estimate) {
            vm.flushSave();
            Bundle args = new Bundle();
            args.putString("versionId", vm.versionId());
            Nav.go(this, R.id.action_editor_to_estimate, args);
        } else if (id == R.id.action_versions) {
            vm.flushSave();
            Bundle args = new Bundle();
            args.putString("projectId", vm.project().projectId);
            args.putString("versionId", vm.versionId());
            Nav.go(this, R.id.action_editor_to_versions, args);
        } else if (id == R.id.action_assistant) {
            new AssistantSheet().show(getChildFragmentManager(), AssistantSheet.TAG);
        } else if (id == R.id.action_suggestions) {
            new SuggestionsSheet().show(getChildFragmentManager(), SuggestionsSheet.TAG);
        } else if (id == R.id.action_view_options) {
            showViewOptions();
        } else if (id == R.id.action_units) {
            UnitSystem now = c.state().units;
            vm.setUnits(now == UnitSystem.METRIC ? UnitSystem.FEET_INCHES : UnitSystem.METRIC);
            refreshMenu();
        } else {
            return false;
        }
        return true;
    }

    /** Title, save status, undo/redo availability and the accessible plan summary. */
    private void refreshChrome() {
        EditorController c = vm.controller();
        ProjectVersion v = vm.version();
        b.toolbar.setTitle(vm.project().projectName);
        b.toolbar.setSubtitle(getString(R.string.editor_subtitle, v.number, saveText(vm.saveState().getValue())));
        Menu m = b.toolbar.getMenu();
        MenuItem undo = m.findItem(R.id.action_undo), redo = m.findItem(R.id.action_redo);
        undo.setEnabled(c.canUndo());
        redo.setEnabled(c.canRedo());
        undo.getIcon().mutate().setAlpha(c.canUndo() ? 255 : 97);          // 38% for disabled, the Material convention
        redo.getIcon().mutate().setAlpha(c.canRedo() ? 255 : 97);
        Floor f = c.doc().floor(c.activeFloorId());
        String floorName = f == null ? "" : f.name;
        b.btnFloor.setText(floorName);
        FloorPlan fp = c.plan();
        b.planView.setPlanDescription(getString(R.string.editor_plan_description, floorName, fp.rooms.size(), fp.walls.size()));
        refreshMenu();
    }

    private String saveText(@Nullable EditorViewModel.SaveState s) {
        if (s == null) return getString(R.string.save_saved);
        switch (s) {
            case PENDING: return getString(R.string.save_pending);
            case SAVING: return getString(R.string.save_saving);
            case FAILED: return getString(R.string.save_failed);
            default: return getString(R.string.save_saved);
        }
    }

    private void refreshMenu() {
        Menu m = b.toolbar.getMenu();
        m.findItem(R.id.action_suggestions).setTitle(suggestionCount > 0
                ? getString(R.string.editor_suggestions_count, suggestionCount) : getString(R.string.editor_suggestions));
        UnitSystem now = vm.controller().state().units;
        m.findItem(R.id.action_units).setTitle(getString(R.string.editor_units_switch,
                now == UnitSystem.METRIC ? getString(R.string.units_feet) : getString(R.string.units_metric)));
    }

    private void refreshBanner() {
        EditorViewModel.ReadOnlyReason reason = vm.readOnlyReason();
        if (reason == EditorViewModel.ReadOnlyReason.NONE) {
            b.banner.setVisibility(View.GONE);
            return;
        }
        b.banner.setVisibility(View.VISIBLE);
        if (reason == EditorViewModel.ReadOnlyReason.VERSION_FROZEN) {
            b.bannerText.setText(getString(R.string.editor_banner_frozen, ElementLabels.of(requireContext(), vm.version().workflowState)));
            b.bannerAction.setVisibility(View.VISIBLE);
            b.bannerAction.setOnClickListener(v -> makeEditableCopy());
        } else {
            b.bannerText.setText(R.string.editor_banner_role);
            b.bannerAction.setVisibility(View.GONE);
        }
    }

    private void makeEditableCopy() {
        b.bannerAction.setEnabled(false);
        vm.createEditableCopy(new RepoCallback<ProjectVersion>() {
            @Override public void onSuccess(ProjectVersion nv) {
                if (b == null) return;
                Bundle args = new Bundle();
                args.putString(ARG_VERSION_ID, nv.id);
                Nav.go(EditorFragment.this, R.id.action_editor_to_editor, args);
            }

            @Override public void onError(Exception e) {
                if (b == null) return;
                b.bannerAction.setEnabled(true);
                Snackbar.make(b.getRoot(), e.getMessage() == null ? getString(R.string.common_error) : e.getMessage(), Snackbar.LENGTH_LONG).show();
            }
        });
    }

    // tools and options 

    private void syncToolChips() {
        ToolType t = vm.controller().state().tool;
        for (int i = 0; i < b.toolGroup.getChildCount(); i++) {
            View chip = b.toolGroup.getChildAt(i);
            if (t.name().equals(chip.getTag())) {
                if (b.toolGroup.getCheckedChipId() != chip.getId()) b.toolGroup.check(chip.getId());
                return;
            }
        }
    }

    private void refreshOptions() {
        EditorState s = vm.controller().state();
        boolean drawing = s.tool != ToolType.SELECT;
        b.optionsScroll.setVisibility(drawing ? View.VISIBLE : View.GONE);
        b.btnDone.setVisibility(s.tool == ToolType.WALL || s.tool == ToolType.ROOM ? View.VISIBLE : View.GONE);
        rebuildingChips = true;
        b.optionGroup.removeAllViews();
        List<ToolOptions.Option> options = ToolOptions.of(s.tool, s);
        for (ToolOptions.Option o : options) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_chip_option, b.optionGroup, false);
            chip.setId(View.generateViewId());
            chip.setText(o.label);
            chip.setTag(o.id);
            b.optionGroup.addView(chip);
            if (o.selected) b.optionGroup.check(chip.getId());
        }
        rebuildingChips = false;
    }

    //  selection 

    private void refreshSelection() {
        EditorController c = vm.controller();
        int n = c.selection().size();
        b.selectionBar.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
        if (n == 0) return;
        boolean editable = c.canEdit();
        if (n == 1) {
            PlanElement e = c.doc().get(c.selection().primaryId());
            b.selectionLabel.setText(e == null ? "" : ElementLabels.of(requireContext(), e.type()));
        } else {
            b.selectionLabel.setText(getString(R.string.editor_selected_count, n));
        }
        b.btnProps.setVisibility(n == 1 ? View.VISIBLE : View.GONE);
        b.btnDuplicate.setVisibility(editable ? View.VISIBLE : View.GONE);
        b.btnRotate.setVisibility(editable ? View.VISIBLE : View.GONE);
        b.btnDelete.setVisibility(editable ? View.VISIBLE : View.GONE);
    }

    private void showProperties(String elementId) {
        if (elementId == null || getChildFragmentManager().findFragmentByTag(PropertiesSheet.TAG) != null) return;
        PropertiesSheet.newInstance(elementId).show(getChildFragmentManager(), PropertiesSheet.TAG);
    }

    //  scale, floors, view options

    private void updateScaleLabel() {
        int ratio = b.planView.drawingScaleRatio();
        if (ratio == shownScaleRatio) return;
        shownScaleRatio = ratio;
        b.btnScale.setText(getString(R.string.editor_scale_label, ScaleModel.label(ratio)));
    }

    private void chooseScale() {
        int[] ratios = ScaleModel.STANDARD_RATIOS;
        String[] labels = new String[ratios.length];
        int checked = 0;
        for (int i = 0; i < ratios.length; i++) {
            labels[i] = ScaleModel.label(ratios[i]);
            if (ratios[i] == b.planView.drawingScaleRatio()) checked = i;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.editor_scale_title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    b.planView.setDrawingScale(ratios[which]);
                    d.dismiss();
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private void chooseFloor() {
        EditorController c = vm.controller();
        List<Floor> floors = new ArrayList<>(c.doc().floors());
        floors.sort((x, y) -> Integer.compare(x.level, y.level));
        String[] names = new String[floors.size()];
        int checked = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = floors.get(i).name;
            if (floors.get(i).id.equals(c.activeFloorId())) checked = i;
        }
        MaterialAlertDialogBuilder d = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.editor_floors_title)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    c.setActiveFloor(floors.get(which).id);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.common_cancel, null);
        if (c.canEdit()) d.setNeutralButton(R.string.editor_add_floor, (dialog, which) -> c.addFloor());
        d.show();
    }

    private void showViewOptions() {
        EditorController c = vm.controller();
        EditorState s = c.state();
        String[] labels = {getString(R.string.view_grid), getString(R.string.view_dimensions), getString(R.string.view_room_names),
                getString(R.string.view_snap), getString(R.string.view_angle_lock)};
        boolean[] checked = {s.showGrid, s.showDimensions, s.showRoomLabels, s.snap.enabled, s.snap.orthoLock};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.editor_view_options)
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
                    switch (which) {
                        case 0: s.showGrid = isChecked; break;
                        case 1: s.showDimensions = isChecked; break;
                        case 2: s.showRoomLabels = isChecked; break;
                        case 3: s.snap.enabled = isChecked; break;
                        default: s.snap.orthoLock = isChecked; break;
                    }
                    c.invalidate();
                })
                .setPositiveButton(R.string.common_done, null)
                .show();
    }

    // lifecycle 

    @Override
    public void onResume() {
        super.onResume();
        if (vm != null) vm.refreshSettings();             
    }

    @Override
    public void onPause() {
        super.onPause();
        if (vm != null) vm.flushSave();                  // never lose a drawing to a phone call or the app being swiped away
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b.planView.detach();
        b = null;
    }
}
