package com.buildvisionai.ui.projects;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.buildvisionai.BuildVisionApp;
import com.buildvisionai.R;
import com.buildvisionai.databinding.DialogNewProjectBinding;
import com.buildvisionai.domain.ProjectForm;
import com.buildvisionai.estimation.Counties;
import com.buildvisionai.models.BuildingType;
import com.buildvisionai.ui.common.Dropdowns;
import com.buildvisionai.utils.PlotPresets;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The "new project" form. It checks the fields with {@link ProjectForm} and stays open, with a message
 * under each field that needs fixing, until everything is valid. Plot presets use feet because that is
 * how plots are quoted in Kenya.
 */
public final class NewProjectDialog extends DialogFragment {
    public static final String TAG = "new_project";

    private DialogNewProjectBinding b;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        b = DialogNewProjectBinding.inflate(LayoutInflater.from(requireContext()));
        ProjectListViewModel vm = new ViewModelProvider(requireParentFragment(),
                new ProjectListViewModel.Factory(BuildVisionApp.of(requireContext()))).get(ProjectListViewModel.class);

        b.inputCounty.setAdapter(new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, Counties.all()));

        List<String> presetLabels = new ArrayList<>();
        for (PlotPresets.Preset p : PlotPresets.all()) presetLabels.add(p.label);
        b.inputPreset.setAdapter(new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, presetLabels));
        b.inputPreset.setOnItemClickListener((parent, view, position, id) -> {
            int i = Dropdowns.indexOfTapped(parent, position, presetLabels);   // by label: the popup may be filtered after a rotation
            if (i < 0) return;
            PlotPresets.Preset p = PlotPresets.all().get(i);
            b.inputWidth.setText(String.format(Locale.US, "%.1f", p.widthM));
            b.inputLength.setText(String.format(Locale.US, "%.1f", p.lengthM));
        });

        List<String> typeLabels = new ArrayList<>();
        for (BuildingType t : BuildingType.values()) typeLabels.add(t.label);
        b.inputType.setAdapter(new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, typeLabels));
        if (savedInstanceState == null) {
            b.inputType.setText(BuildingType.BUNGALOW.label, false);
            b.inputOwner.setText(vm.deviceUserName());
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.new_project_title)
                .setView(b.getRoot())
                .setPositiveButton(R.string.new_project_create, null)      // set below, so a bad form keeps the dialog open
                .setNegativeButton(R.string.common_cancel, null)
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> submit(vm)));
        return dialog;
    }

    private void submit(ProjectListViewModel vm) {
        ProjectForm.Result r = ProjectForm.validate(
                text(b.inputName.getText()), text(b.inputOwner.getText()), text(b.inputLocation.getText()),
                text(b.inputCounty.getText()), text(b.inputWidth.getText()), text(b.inputLength.getText()),
                text(b.inputBudget.getText()), typeOf(text(b.inputType.getText())));
        showErrors(r.errors);
        if (!r.ok()) return;
        vm.create(r.project);
        dismiss();
    }

    private void showErrors(Map<String, String> errors) {
        TextInputLayout[] layouts = {b.layoutName, b.layoutOwner, b.layoutLocation, b.layoutCounty, b.layoutWidth, b.layoutLength, b.layoutBudget};
        String[] keys = {ProjectForm.NAME, ProjectForm.OWNER, ProjectForm.LOCATION, ProjectForm.COUNTY,
                ProjectForm.PLOT_WIDTH, ProjectForm.PLOT_LENGTH, ProjectForm.BUDGET};
        boolean focused = false;
        for (int i = 0; i < keys.length; i++) {
            String message = errors.get(keys[i]);
            layouts[i].setError(message);
            if (message != null && !focused) {
                layouts[i].requestFocus();
                focused = true;
            }
        }
    }

    private static BuildingType typeOf(String label) {
        for (BuildingType t : BuildingType.values()) if (t.label.equalsIgnoreCase(label)) return t;
        return BuildingType.BUNGALOW;
    }

    private static String text(CharSequence c) { return c == null ? "" : c.toString(); }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
