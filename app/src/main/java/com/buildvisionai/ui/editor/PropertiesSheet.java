package com.buildvisionai.ui.editor;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.content.DialogInterface;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.buildvisionai.R;
import com.buildvisionai.databinding.ItemFieldChoiceBinding;
import com.buildvisionai.databinding.ItemFieldTextBinding;
import com.buildvisionai.databinding.SheetPropertiesBinding;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.planner.EditorController;
import com.buildvisionai.planner.props.ElementFields;
import com.buildvisionai.ui.common.Dropdowns;
import com.buildvisionai.ui.common.ElementLabels;
import com.buildvisionai.utils.UnitSystem;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Edits the selected element's properties. The form is generated from {@link ElementFields}, which
 * also owns the limits and the validation, so this class is only widgets. Each change is one undo
 * step through the editor controller; read-only versions show the values without editing.
 */
public final class PropertiesSheet extends BottomSheetDialogFragment {
    public static final String TAG = "properties";
    private static final String ARG_ID = "element_id";

    public static PropertiesSheet newInstance(String elementId) {
        PropertiesSheet s = new PropertiesSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ID, elementId);
        s.setArguments(args);
        return s;
    }

    private SheetPropertiesBinding b;
    private EditorViewModel vm;
    private String elementId;
    /** One per text row: applies what is typed in the box, if it differs from what the element has. */
    private final List<Runnable> commits = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = SheetPropertiesBinding.inflate(inflater, container, false);
        vm = EditorFragment.viewModelOf(requireParentFragment());
        elementId = requireArguments().getString(ARG_ID);
        // The rows are made from one layout, so they share view ids, and the framework keys saved view state by id:
        // after a rotation every row would get the last row's text. The rows are rebuilt from the element anyway.
        b.fields.setSaveFromParentEnabled(false);
        build();
        return b.getRoot();
    }

    private void build() {
        b.fields.removeAllViews();
        commits.clear();
        EditorController c = vm.controller();
        PlanElement e = c == null ? null : c.doc().get(elementId);
        if (e == null) {
            dismissAllowingStateLoss();
            return;
        }
        b.sheetTitle.setText(ElementLabels.of(requireContext(), e.type()));
        boolean editable = c.canEdit();
        b.sheetHint.setText(editable ? R.string.props_hint : R.string.props_read_only);
        UnitSystem units = c.state().units;
        for (ElementFields.Field f : ElementFields.of(e, units)) {
            switch (f.kind) {
                case CHOICE: addChoice(f, editable); break;
                case ACTION: addAction(f, editable); break;
                default: addText(f, units, editable); break;
            }
        }
    }

    private void addText(ElementFields.Field f, UnitSystem units, boolean editable) {
        ItemFieldTextBinding t = ItemFieldTextBinding.inflate(getLayoutInflater(), b.fields, false);
        t.fieldLayout.setHint(f.label);
        t.fieldInput.setText(f.value);
        t.fieldInput.setEnabled(editable);
        switch (f.kind) {
            case NUMBER:
                t.fieldInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
                break;
            case TEXT:
                t.fieldInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                break;
            default:
                t.fieldInput.setInputType(InputType.TYPE_CLASS_TEXT);        // lengths like "0.9 m" or "3'-6\""
                break;
        }
        String[] shown = {f.value};                                           // what the box shows after the last accepted edit
        Runnable commit = () -> {
            PlanElement current = vm.controller().doc().get(elementId);
            if (current == null) return;
            String text = String.valueOf(t.fieldInput.getText());
            if (text.equals(shown[0])) {
                t.fieldLayout.setError(null);
                return;
            }
            String error = ElementFields.validate(current, f.key, text, units);
            t.fieldLayout.setError(error);
            if (error != null) return;
            apply(f, text, units);
            String normalised = currentValue(f.key, units, text);
            shown[0] = normalised;
            t.fieldInput.setText(normalised);
        };
        commits.add(commit);
        t.fieldInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commit.run();
                v.clearFocus();
            }
            return false;
        });
        t.fieldInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) commit.run();
        });
        b.fields.addView(t.getRoot());
    }

    private void addChoice(ElementFields.Field f, boolean editable) {
        ItemFieldChoiceBinding ch = ItemFieldChoiceBinding.inflate(getLayoutInflater(), b.fields, false);
        ch.choiceLayout.setHint(f.label);
        ch.choiceInput.setAdapter(new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, f.choiceLabels));
        ch.choiceInput.setText(f.value, false);
        ch.choiceLayout.setEnabled(editable);
        ch.choiceInput.setOnItemClickListener((parent, view, position, id) -> {
            int i = Dropdowns.indexOfTapped(parent, position, f.choiceLabels);     // by label: the popup may be filtered
            if (i < 0) return;
            apply(f, f.choiceIds.get(i), vm.controller().state().units);
            build();                                                           // other fields may follow the choice (thickness, width)
        });
        b.fields.addView(ch.getRoot());
    }

    private void addAction(ElementFields.Field f, boolean editable) {
        MaterialButton button = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(f.label);
        button.setEnabled(editable);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        button.setLayoutParams(lp);
        button.setOnClickListener(v -> {
            apply(f, "", vm.controller().state().units);
            build();
        });
        b.fields.addView(button);
    }

    private void apply(ElementFields.Field f, String text, UnitSystem units) {
        vm.controller().updateElement(elementId, f.label, copy -> ElementFields.apply(copy, f.key, text, units));
    }

    /** The field's value as the form would now show it (a length typed as "230" reads "230 mm"). */
    private String currentValue(String key, UnitSystem units, String fallback) {
        PlanElement e = vm.controller().doc().get(elementId);
        if (e == null) return fallback;
        for (ElementFields.Field f : ElementFields.of(e, units)) if (f.key.equals(key)) return f.value;
        return fallback;
    }

    /**
     * A typed value is applied when the box loses focus or the keyboard's Done is pressed. Swiping the sheet away, tapping
     * outside it or pressing back does neither, so apply whatever is still pending when the sheet goes (or the app does).
     */
    private void commitPending() {
        if (b == null || vm == null || vm.controller() == null) return;
        for (Runnable r : new ArrayList<>(commits)) r.run();
    }

    @Override
    public void onStop() {
        commitPending();
        super.onStop();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        commitPending();
        super.onDismiss(dialog);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
