package com.buildvisionai.ui.estimate;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.buildvisionai.BuildVisionApp;
import com.buildvisionai.R;
import com.buildvisionai.databinding.DialogPriceBinding;
import com.buildvisionai.databinding.FragmentEstimateBinding;
import com.buildvisionai.databinding.ItemLegendBinding;
import com.buildvisionai.estimation.BudgetPosition;
import com.buildvisionai.estimation.CostBreakdown;
import com.buildvisionai.estimation.CostEstimate;
import com.buildvisionai.estimation.CostLine;
import com.buildvisionai.estimation.EstimateSettings;
import com.buildvisionai.estimation.FinishLevel;
import com.buildvisionai.estimation.Item;
import com.buildvisionai.estimation.Rate;
import com.buildvisionai.estimation.RateInput;
import com.buildvisionai.estimation.RoofType;
import com.buildvisionai.estimation.SanitationSystem;
import com.buildvisionai.estimation.SettingsEditor;
import com.buildvisionai.estimation.StatutoryItem;
import com.buildvisionai.models.WorkflowState;
import com.buildvisionai.repository.RepoCallback;
import com.buildvisionai.reports.ExportReportWorker;
import com.buildvisionai.ui.common.Dropdowns;
import com.buildvisionai.ui.common.ElementLabels;
import com.buildvisionai.ui.common.Event;
import com.buildvisionai.utils.MoneyFormatter;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The estimate of one version, in three tabs: the cost (split chart, add-ons, budget position,
 * saving and sharing), the priced items (tap one to give your own quote) and the assumptions behind
 * the numbers. The numbers come from {@link EstimateViewModel}; this class shows them and passes
 * edits back.
 */
public final class EstimateFragment extends Fragment {
    private static final String ARG_VERSION_ID = "versionId";
    private static final String STATE_TAB = "tab";

    private FragmentEstimateBinding b;
    private EstimateViewModel vm;
    private CostLineAdapter adapter;
    private int tab;
    private boolean loaded;
    /** True while values are being written into the assumption fields, so their listeners ignore the change. */
    private boolean filling;
    private final List<Runnable> pendingCommits = new ArrayList<>();
    private final List<TextInputEditText> numberInputs = new ArrayList<>();
    @Nullable private LiveData<WorkInfo> watchedWork;
    private final Observer<WorkInfo> workObserver = this::onWorkInfo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentEstimateBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        String versionId = requireArguments().getString(ARG_VERSION_ID);
        vm = new ViewModelProvider(this, new EstimateViewModel.Factory(BuildVisionApp.of(requireContext()), versionId))
                .get(EstimateViewModel.class);
        tab = saved == null ? 0 : saved.getInt(STATE_TAB, 0);
        loaded = false;
        pendingCommits.clear();
        numberInputs.clear();

        b.toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        adapter = new CostLineAdapter(this::onLineClicked);
        b.tabItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.tabItems.setAdapter(adapter);

        TabLayout.Tab selected = b.tabs.getTabAt(tab);
        if (selected != null) selected.select();
        b.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) {
                tab = t.getPosition();
                showTab();
            }

            @Override public void onTabUnselected(TabLayout.Tab t) {}

            @Override public void onTabReselected(TabLayout.Tab t) {}
        });

        wireAssumptions();
        b.btnSaveEstimate.setOnClickListener(v -> saveEstimate());
        b.btnExportPdf.setOnClickListener(v -> vm.startExport("pdf"));
        b.btnExportPng.setOnClickListener(v -> vm.startExport("png"));

        vm.state().observe(getViewLifecycleOwner(), this::render);
        vm.loadError().observe(getViewLifecycleOwner(), error -> {
            if (error == null) return;
            b.loading.setVisibility(View.GONE);
            b.loadError.setText(getString(R.string.estimate_load_failed, error));
            b.loadError.setVisibility(View.VISIBLE);
        });
        vm.messages().observe(getViewLifecycleOwner(), (Event<String> e) -> {
            String m = e.getIfNotHandled();
            if (m != null) Snackbar.make(b.getRoot(), m, Snackbar.LENGTH_LONG).show();
        });
        vm.exportJob().observe(getViewLifecycleOwner(), job -> {
            if (watchedWork != null) {
                watchedWork.removeObserver(workObserver);
                watchedWork = null;
            }
            if (job == null) {
                showExportIdle();
                return;
            }
            watchedWork = WorkManager.getInstance(requireContext()).getWorkInfoByIdLiveData(job.id);
            watchedWork.observe(getViewLifecycleOwner(), workObserver);
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_TAB, tab);
    }

    /** A number typed but not yet confirmed is kept when the person leaves the screen. */
    @Override
    public void onPause() {
        super.onPause();
        for (Runnable commit : new ArrayList<>(pendingCommits)) commit.run();
    }

    private void showTab() {
        if (b == null) return;
        b.tabCost.setVisibility(loaded && tab == 0 ? View.VISIBLE : View.GONE);
        b.tabItems.setVisibility(loaded && tab == 1 ? View.VISIBLE : View.GONE);
        b.tabAssumptions.setVisibility(loaded && tab == 2 ? View.VISIBLE : View.GONE);
    }

    
    // showing the numbers

    private void render(@Nullable EstimateViewModel.State s) {
        if (s == null) return;
        loaded = true;
        b.loading.setVisibility(View.GONE);
        b.loadError.setVisibility(View.GONE);
        showTab();
        renderCost(s);
        adapter.submitList(CostLineAdapter.rowsFor(s.run.cost, line -> s.hasOwnPrice(line.item)));
        renderAssumptions(s);
        b.toolbar.setSubtitle(getString(R.string.estimate_subtitle, s.project.projectName, s.version.number));
    }

    private void renderCost(EstimateViewModel.State s) {
        CostEstimate c = s.run.cost;
        b.totalValue.setText(MoneyFormatter.kes(c.totalAvg));
        b.totalRange.setText(getString(R.string.estimate_range, MoneyFormatter.kes(c.totalMin), MoneyFormatter.kes(c.totalMax)));
        b.perM2.setText(c.grossFloorAreaM2 > 0
                ? getString(R.string.estimate_per_m2, MoneyFormatter.kes(c.perM2Avg), c.grossFloorAreaM2)
                : getString(R.string.estimate_no_area));
        b.budgetStatus.setText(budgetText(s.project.budgetKes, c));
        if (c.benchmark != null && c.benchmark.message != null && !c.benchmark.message.isEmpty()) {
            b.benchmark.setText(c.benchmark.message);
            b.benchmark.setVisibility(View.VISIBLE);
        } else {
            b.benchmark.setVisibility(View.GONE);
        }

        List<CostBreakdown.Slice> slices = CostBreakdown.slices(c);
        b.donut.setData(slices, getString(R.string.estimate_donut_caption), MoneyFormatter.compact(c.subtotalAvg));
        b.legend.removeAllViews();
        for (CostBreakdown.Slice sl : slices) {
            addRow(b.legend, ContextCompat.getColor(requireContext(), CostDonutView.colorRes(sl.colorIndex())),
                    sl.category.label, MoneyFormatter.kes(sl.avg), String.format(Locale.US, "%.0f%%", sl.share * 100));
        }

        b.addons.removeAllViews();
        addRow(b.addons, 0, getString(R.string.estimate_subtotal), MoneyFormatter.kes(c.subtotalAvg), "");
        for (StatutoryItem a : c.addOns) {
            addRow(b.addons, 0, String.format(Locale.US, "%s (%s%%)", a.label, trim(a.ratePct)), MoneyFormatter.kes(a.avg), "");
        }
        b.vatNote.setText(getString(R.string.estimate_vat_note, MoneyFormatter.kes(c.vatIncludedAvg)));

        b.warnings.removeAllViews();
        List<String> all = new ArrayList<>(c.warnings);
        all.addAll(s.priceWarnings);
        for (String w : all) addWarning(getString(R.string.estimate_warning_item, w));

        Set<Item> own = new HashSet<>();
        for (CostLine l : c.lines) if (s.hasOwnPrice(l.item)) own.add(l.item);
        String ownText = own.isEmpty() ? "" : " " + getResources().getQuantityString(R.plurals.estimate_own_prices, own.size(), own.size());
        b.ratePackNote.setText(getString(R.string.estimate_rate_pack, c.ratePackId, c.region.label, c.ratePackAsOf, c.disclaimer) + ownText);
    }

    private String budgetText(double budget, CostEstimate c) {
        BudgetPosition p = BudgetPosition.of(budget, c.totalMin, c.totalAvg, c.totalMax);
        switch (p.kind) {
            case WITHIN_EVEN_AT_HIGH:
                return getString(R.string.estimate_budget_within, MoneyFormatter.kes(p.budgetKes), MoneyFormatter.kes(p.amountKes));
            case WITHIN_AT_TYPICAL:
                return getString(R.string.estimate_budget_typical, MoneyFormatter.kes(p.budgetKes),
                        MoneyFormatter.kes(p.amountKes), MoneyFormatter.kes(p.overshootAtHighKes));
            case OVER:
                return getString(p.fitsAtLowestPrices ? R.string.estimate_budget_over_low : R.string.estimate_budget_over,
                        MoneyFormatter.kes(p.budgetKes), MoneyFormatter.kes(p.amountKes));
            default:
                return getString(R.string.estimate_budget_not_set);
        }
    }

    /** A row of the legend or add-on list. {@code swatchColor} 0 leaves out the colour square. */
    private void addRow(LinearLayout parent, int swatchColor, String label, String amount, String share) {
        ItemLegendBinding row = ItemLegendBinding.inflate(getLayoutInflater(), parent, false);
        if (swatchColor == 0) row.swatch.setVisibility(View.GONE);
        else row.swatch.setBackgroundColor(swatchColor);
        row.legendLabel.setText(label);
        row.legendAmount.setText(amount);
        row.legendShare.setText(share);
        row.legendShare.setVisibility(share.isEmpty() ? View.GONE : View.VISIBLE);
        parent.addView(row.getRoot());
    }

    private void addWarning(String text) {
        TextView t = new TextView(requireContext());
        TextViewCompat.setTextAppearance(t, R.style.BV_Text_Label);
        t.setTextColor(MaterialColors.getColor(t, androidx.appcompat.R.attr.colorError));
        t.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(8 * getResources().getDisplayMetrics().density);
        b.warnings.addView(t, lp);
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.US, "%.1f", v);
    }

       // prices

    private void onLineClicked(CostLine line) {
        EstimateViewModel.State s = vm.state().getValue();
        if (s == null) return;
        if (!s.canEditPrices()) {
            Snackbar.make(b.getRoot(), s.version.workflowState == WorkflowState.LOCKED ? R.string.price_locked : R.string.price_role,
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        showPriceDialog(line.item, s);
    }

    private void showPriceDialog(Item item, EstimateViewModel.State s) {
        Rate current = s.pack.base(item);
        Rate builtIn = s.builtIn.base(item);
        if (current == null || builtIn == null) return;
        DialogPriceBinding d = DialogPriceBinding.inflate(getLayoutInflater());
        String[] shown = RateInput.format(current);
        d.inputPriceMin.setText(shown[0]);
        d.inputPriceAvg.setText(shown[1]);
        d.inputPriceMax.setText(shown[2]);
        String[] original = RateInput.format(builtIn);
        String info = getString(R.string.price_current, item.unit, original[0], original[1], original[2]);
        if (item.finishSensitive) info += " " + getString(R.string.price_finish_note);
        d.priceCurrent.setText(info);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.price_dialog_title, item.description))
                .setView(d.getRoot())
                .setPositiveButton(R.string.common_save, null)       // set below, so a bad entry keeps the dialog open
                .setNegativeButton(R.string.common_cancel, null);
        if (s.hasOwnPrice(item)) builder.setNeutralButton(R.string.price_reset, (dlg, w) -> vm.setPrice(item, null));
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            RateInput.Result r = RateInput.parse(text(d.inputPriceMin), text(d.inputPriceAvg), text(d.inputPriceMax));
            d.layoutPriceMin.setError(r.errors.get(RateInput.MIN));
            d.layoutPriceAvg.setError(r.errors.get(RateInput.AVG));
            d.layoutPriceMax.setError(r.errors.get(RateInput.MAX));
            if (!r.ok()) return;
            vm.setPrice(item, r.rate);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

   
    // assumptions

    private void wireAssumptions() {
        // These three show the model's value (render() sets their text), so the framework must not restore it, and a tap
        // is read back by label, not by position: see Dropdowns.
        Dropdowns.textComesFromModel(b.inputFinish, b.inputRoof, b.inputSanitation);
        String[] finishLabels = labels(FinishLevel.values());
        String[] roofLabels = labels(RoofType.values());
        String[] sanitationLabels = labels(SanitationSystem.values());
        b.inputFinish.setAdapter(new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, finishLabels));
        b.inputFinish.setOnItemClickListener((p, v, position, id) -> {
            int i = Dropdowns.indexOfTapped(p, position, finishLabels);
            if (!filling && i >= 0) vm.changeSettings(e -> e.finishLevel = FinishLevel.values()[i]);
        });
        b.inputRoof.setAdapter(new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, roofLabels));
        b.inputRoof.setOnItemClickListener((p, v, position, id) -> {
            int i = Dropdowns.indexOfTapped(p, position, roofLabels);
            if (!filling && i >= 0) vm.changeSettings(e -> e.roofType = RoofType.values()[i]);
        });
        b.inputSanitation.setAdapter(new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, sanitationLabels));
        b.inputSanitation.setOnItemClickListener((p, v, position, id) -> {
            int i = Dropdowns.indexOfTapped(p, position, sanitationLabels);
            if (!filling && i >= 0) vm.changeSettings(e -> e.sanitation = SanitationSystem.values()[i]);
        });
        b.switchCeilings.setOnCheckedChangeListener((v, on) -> {
            if (!filling) vm.changeSettings(e -> e.ceilings = on);
        });
        b.switchGrills.setOnCheckedChangeListener((v, on) -> {
            if (!filling) vm.changeSettings(e -> e.windowGrills = on);
        });
        b.switchNca.setOnCheckedChangeListener((v, on) -> {
            if (!filling) vm.changeSettings(e -> e.ncaLevy = on);
        });
        wireNumber(b.layoutPitch, b.inputPitch, SettingsEditor.Field.ROOF_PITCH);
        wireNumber(b.layoutTank, b.inputTank, SettingsEditor.Field.WATER_TANK);
        wireNumber(b.layoutContingency, b.inputContingency, SettingsEditor.Field.CONTINGENCY);
        wireNumber(b.layoutPrelims, b.inputPrelims, SettingsEditor.Field.PRELIMINARIES);
        wireNumber(b.layoutOverhead, b.inputOverhead, SettingsEditor.Field.OVERHEAD_PROFIT);
        wireNumber(b.layoutFees, b.inputFees, SettingsEditor.Field.PROFESSIONAL_FEES);
    }

    private static String[] labels(Enum<?>[] values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            Enum<?> v = values[i];
            out[i] = v instanceof FinishLevel ? ((FinishLevel) v).label
                    : v instanceof RoofType ? ((RoofType) v).label
                    : ((SanitationSystem) v).label;
        }
        return out;
    }

    /** A typed number is checked and applied when the person finishes (keyboard "done", or leaving the field). */
    private void wireNumber(TextInputLayout layout, TextInputEditText input, SettingsEditor.Field field) {
        Runnable commit = () -> {
            EstimateViewModel.State s = vm.state().getValue();
            if (filling || s == null || !s.canEditAssumptions()) return;
            String typed = text(input);
            String error = SettingsEditor.validate(field, typed);
            layout.setError(error);
            if (error != null) return;
            EstimateSettings probe = s.settings.copy();
            SettingsEditor.apply(probe, field, typed);
            String normal = SettingsEditor.format(probe, field);
            if (!normal.equals(typed)) {
                filling = true;
                input.setText(normal);
                filling = false;
            }
            if (!normal.equals(SettingsEditor.format(s.settings, field))) {
                vm.changeSettings(e -> SettingsEditor.apply(e, field, typed));
            }
        };
        pendingCommits.add(() -> {
            if (input.hasFocus()) commit.run();
        });
        numberInputs.add(input);
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) commit.run();
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            commit.run();
            input.clearFocus();
            WindowCompat.getInsetsController(requireActivity().getWindow(), input).hide(WindowInsetsCompat.Type.ime());
            return true;
        });
    }

    private void renderAssumptions(EstimateViewModel.State s) {
        EstimateSettings e = s.settings;
        boolean can = s.canEditAssumptions();
        filling = true;
        if (can) b.assumptionsNote.setText(R.string.assume_note_edit);
        else if (s.version.workflowState != WorkflowState.DRAFT) {
            b.assumptionsNote.setText(getString(R.string.assume_note_frozen, ElementLabels.of(requireContext(), s.version.workflowState)));
        } else b.assumptionsNote.setText(R.string.assume_note_role);

        b.inputFinish.setText(e.finishLevel.label, false);
        b.inputRoof.setText(e.roofType.label, false);
        b.inputSanitation.setText(e.sanitation.label, false);
        b.switchCeilings.setChecked(e.ceilings);
        b.switchGrills.setChecked(e.windowGrills);
        b.switchNca.setChecked(e.ncaLevy);
        fill(b.inputPitch, SettingsEditor.format(e, SettingsEditor.Field.ROOF_PITCH));
        fill(b.inputTank, SettingsEditor.format(e, SettingsEditor.Field.WATER_TANK));
        fill(b.inputContingency, SettingsEditor.format(e, SettingsEditor.Field.CONTINGENCY));
        fill(b.inputPrelims, SettingsEditor.format(e, SettingsEditor.Field.PRELIMINARIES));
        fill(b.inputOverhead, SettingsEditor.format(e, SettingsEditor.Field.OVERHEAD_PROFIT));
        fill(b.inputFees, SettingsEditor.format(e, SettingsEditor.Field.PROFESSIONAL_FEES));
        b.layoutPitch.setVisibility(e.roofType.pitched() ? View.VISIBLE : View.GONE);

        b.layoutFinish.setEnabled(can);
        b.inputFinish.setEnabled(can);
        b.layoutRoof.setEnabled(can);
        b.inputRoof.setEnabled(can);
        b.layoutSanitation.setEnabled(can);
        b.inputSanitation.setEnabled(can);
        b.switchCeilings.setEnabled(can);
        b.switchGrills.setEnabled(can);
        b.switchNca.setEnabled(can);
        for (TextInputEditText in : numberInputs) in.setEnabled(can);
        b.layoutPitch.setEnabled(can);
        b.layoutTank.setEnabled(can);
        b.layoutContingency.setEnabled(can);
        b.layoutPrelims.setEnabled(can);
        b.layoutOverhead.setEnabled(can);
        b.layoutFees.setEnabled(can);
        filling = false;
    }

    /** Writes a value into a field unless the person is typing in it, and never replaces text that already matches. */
    private static void fill(TextInputEditText input, String value) {
        if (input.hasFocus() || value.equals(text(input))) return;
        input.setText(value);
    }

    
    // saving and sharing

    private void saveEstimate() {
        vm.saveEstimate(new RepoCallback<Boolean>() {
            @Override public void onSuccess(Boolean ok) {
                if (b != null) Snackbar.make(b.getRoot(), R.string.estimate_saved, Snackbar.LENGTH_SHORT).show();
            }

            @Override public void onError(Exception e) {
                if (b != null) Snackbar.make(b.getRoot(), getString(R.string.estimate_save_failed, message(e)), Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private static String message(Exception e) { return e.getMessage() == null ? e.toString() : e.getMessage(); }

    private void showExportIdle() {
        b.btnExportPdf.setEnabled(true);
        b.btnExportPng.setEnabled(true);
        b.exportStatus.setVisibility(View.GONE);
    }

    private void showExportBusy(String format) {
        b.btnExportPdf.setEnabled(false);
        b.btnExportPng.setEnabled(false);
        b.exportStatus.setText("pdf".equals(format) ? R.string.estimate_export_working_pdf : R.string.estimate_export_working_png);
        b.exportStatus.setVisibility(View.VISIBLE);
    }

    private void onWorkInfo(@Nullable WorkInfo info) {
        EstimateViewModel.ExportJob job = vm.exportJob().getValue();
        if (info == null || job == null || b == null) return;
        WorkInfo.State st = info.getState();
        if (st == WorkInfo.State.SUCCEEDED) {
            showExportIdle();
            if (job.delivered()) return;
            vm.markDelivered(job);
            share(job.format, info.getOutputData().getStringArray(ExportReportWorker.KEY_FILES));
        } else if (st == WorkInfo.State.FAILED || st == WorkInfo.State.CANCELLED) {
            showExportIdle();
            if (job.delivered()) return;
            vm.markDelivered(job);
            String why = info.getOutputData().getString(ExportReportWorker.KEY_ERROR);
            Snackbar.make(b.getRoot(), getString(R.string.estimate_export_failed, why == null ? getString(R.string.common_error) : why),
                    Snackbar.LENGTH_LONG).show();
        } else {
            showExportBusy(job.format);
        }
    }

    private void share(String format, @Nullable String[] paths) {
        EstimateViewModel.State s = vm.state().getValue();
        if (paths == null || paths.length == 0 || s == null) {
            Snackbar.make(b.getRoot(), getString(R.string.estimate_export_failed, getString(R.string.common_error)), Snackbar.LENGTH_LONG).show();
            return;
        }
        ArrayList<Uri> uris = new ArrayList<>();
        try {
            for (String path : paths) {
                uris.add(FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".files", new File(path)));
            }
        } catch (IllegalArgumentException ex) {
            Snackbar.make(b.getRoot(), getString(R.string.estimate_export_failed, message(ex)), Snackbar.LENGTH_LONG).show();
            return;
        }
        boolean pdf = "pdf".equals(format);
        Intent send = new Intent(uris.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
        send.setType(pdf ? "application/pdf" : "image/png");
        if (uris.size() == 1) send.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        else send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.estimate_share_subject, s.project.projectName));
        ClipData clip = ClipData.newRawUri("", uris.get(0));
        for (int i = 1; i < uris.size(); i++) clip.addItem(new ClipData.Item(uris.get(i)));
        send.setClipData(clip);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, getString(pdf ? R.string.estimate_share_pdf_chooser : R.string.estimate_share_png_chooser)));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (watchedWork != null) {
            watchedWork.removeObserver(workObserver);
            watchedWork = null;
        }
        b.tabItems.setAdapter(null);
        b = null;
    }
}
