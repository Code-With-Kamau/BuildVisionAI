package com.buildvisionai.ui.editor;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.buildvisionai.R;
import com.buildvisionai.collaboration.ChangeProposal;
import com.buildvisionai.collaboration.Permission;
import com.buildvisionai.collaboration.ProposalApplier;
import com.buildvisionai.collaboration.ProposalCodec;
import com.buildvisionai.collaboration.ProposalDescriber;
import com.buildvisionai.databinding.ItemSuggestionBinding;
import com.buildvisionai.databinding.SheetSuggestionsBinding;
import com.buildvisionai.models.Suggestion;
import com.buildvisionai.models.SuggestionSource;
import com.buildvisionai.models.SuggestionStatus;
import com.buildvisionai.repository.RepoCallback;
import com.buildvisionai.utils.Strings;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * Suggestions for this version, newest first, waiting ones before decided ones. On a draft the owner
 * or architect can accept (the change is applied as one undo step, then recorded) or reject.
 * Everyone else sees the same list read-only.
 */
public final class SuggestionsSheet extends BottomSheetDialogFragment {
    public static final String TAG = "suggestions";

    private SheetSuggestionsBinding b;
    private EditorViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = SheetSuggestionsBinding.inflate(inflater, container, false);
        vm = EditorFragment.viewModelOf(requireParentFragment());
        if (!vm.isReady()) {                                   // restored after the process was killed: nothing to show yet
            dismissAllowingStateLoss();
            return b.getRoot();
        }
        b.suggestionsNote.setText(vm.can(Permission.DECIDE_SUGGESTION) ? R.string.suggestions_note_can_decide
                : vm.canEdit() ? R.string.suggestions_note_owner_only : R.string.suggestions_note_read_only);
        vm.suggestions().observe(getViewLifecycleOwner(), this::show);
        return b.getRoot();
    }

    private void show(List<Suggestion> all) {
        b.suggestionsList.removeAllViews();
        List<Suggestion> sorted = new ArrayList<>(all);
        sorted.sort((x, y) -> {
            boolean xp = x.status == SuggestionStatus.PROPOSED, yp = y.status == SuggestionStatus.PROPOSED;
            if (xp != yp) return xp ? -1 : 1;
            return Long.compare(y.createdAt, x.createdAt);
        });
        b.suggestionsEmpty.setVisibility(sorted.isEmpty() ? View.VISIBLE : View.GONE);
        for (Suggestion s : sorted) b.suggestionsList.addView(card(s));
    }

    private View card(Suggestion s) {
        ItemSuggestionBinding c = ItemSuggestionBinding.inflate(getLayoutInflater(), b.suggestionsList, false);
        ChangeProposal p = decode(s);
        c.suggestionTitle.setText(s.title);
        String origin = s.source == SuggestionSource.AI ? getString(R.string.suggestion_source_ai)
                : getString(R.string.suggestion_source_person, s.authorName == null ? "" : s.authorName);
        c.suggestionSource.setText(getString(R.string.suggestion_source_line, origin, statusText(s.status)));
        StringBuilder body = new StringBuilder();
        if (s.rationale != null && !s.rationale.isEmpty()) body.append(s.rationale).append("\n\n");
        body.append(p == null ? getString(R.string.suggestion_unreadable)
                : Strings.join("\n", ProposalDescriber.lines(p, vm.controller().doc())));
        c.suggestionBody.setText(body);
        String effect = p == null ? null : ProposalDescriber.effect(p);
        c.suggestionEffect.setText(effect == null ? "" : effect);
        c.suggestionEffect.setVisibility(effect == null ? View.GONE : View.VISIBLE);

        boolean actionable = s.status == SuggestionStatus.PROPOSED && vm.can(Permission.DECIDE_SUGGESTION);
        c.suggestionPrimary.setVisibility(actionable && p != null ? View.VISIBLE : View.GONE);
        c.suggestionSecondary.setVisibility(actionable ? View.VISIBLE : View.GONE);
        c.suggestionPrimary.setText(R.string.suggestion_accept);
        c.suggestionSecondary.setText(R.string.suggestion_reject);
        c.suggestionPrimary.setOnClickListener(v -> {
            c.suggestionPrimary.setEnabled(false);
            c.suggestionSecondary.setEnabled(false);
            vm.acceptSuggestion(s, new RepoCallback<ProposalApplier.Result>() {
                @Override public void onSuccess(ProposalApplier.Result r) {
                    if (b == null) return;
                    String msg = r.skipped.isEmpty() ? getString(R.string.suggestion_applied, r.applied)
                            : getString(R.string.suggestion_applied_partly, r.applied, r.skipped.size());
                    Snackbar.make(b.getRoot(), msg, Snackbar.LENGTH_LONG).show();
                }

                @Override public void onError(Exception e) {
                    c.suggestionPrimary.setEnabled(true);
                    c.suggestionSecondary.setEnabled(true);
                    if (b == null) return;
                    Snackbar.make(b.getRoot(), e.getMessage() == null ? getString(R.string.common_error) : e.getMessage(), Snackbar.LENGTH_LONG).show();
                }
            });
        });
        c.suggestionSecondary.setOnClickListener(v -> {
            c.suggestionPrimary.setEnabled(false);
            c.suggestionSecondary.setEnabled(false);
            vm.rejectSuggestion(s, new RepoCallback<Boolean>() {
                @Override public void onSuccess(Boolean value) { /* the list is observed and refreshes itself */ }

                @Override public void onError(Exception e) {
                    c.suggestionPrimary.setEnabled(true);
                    c.suggestionSecondary.setEnabled(true);
                    if (b == null) return;
                    Snackbar.make(b.getRoot(), e.getMessage() == null ? getString(R.string.common_error) : e.getMessage(), Snackbar.LENGTH_LONG).show();
                }
            });
        });
        return c.getRoot();
    }

    private String statusText(SuggestionStatus st) {
        switch (st) {
            case ACCEPTED: return getString(R.string.suggestion_status_accepted);
            case REJECTED: return getString(R.string.suggestion_status_rejected);
            case WITHDRAWN: return getString(R.string.suggestion_status_withdrawn);
            default: return getString(R.string.suggestion_status_waiting);
        }
    }

    @Nullable
    private static ChangeProposal decode(Suggestion s) {
        try {
            ChangeProposal p = ProposalCodec.fromJson(s.payloadJson);
            return p == null || p.ops == null ? null : p;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
