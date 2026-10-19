package com.buildvisionai.ui.editor;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.buildvisionai.BuildConfig;
import com.buildvisionai.R;
import com.buildvisionai.ai.AiAssistant;
import com.buildvisionai.ai.AssistantReply;
import com.buildvisionai.collaboration.ChangeProposal;
import com.buildvisionai.collaboration.Permission;
import com.buildvisionai.collaboration.ProposalDescriber;
import com.buildvisionai.databinding.ItemSuggestionBinding;
import com.buildvisionai.databinding.SheetAssistantBinding;
import com.buildvisionai.models.Suggestion;
import com.buildvisionai.repository.RepoCallback;
import com.buildvisionai.utils.Strings;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;

/**
 * Ask the assistant about the plan. It can answer and propose changes; it cannot change anything.
 * A proposal is shown in plain language and, if the person chooses, stored as a suggestion that the
 * owner or architect must accept on a draft before the plan changes.
 */
public final class AssistantSheet extends BottomSheetDialogFragment {
    public static final String TAG = "assistant";

    private SheetAssistantBinding b;
    private EditorViewModel vm;
    private boolean asking;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = SheetAssistantBinding.inflate(inflater, container, false);
        vm = EditorFragment.viewModelOf(requireParentFragment());
        if (!vm.isReady()) {                                  
            dismissAllowingStateLoss();
            return b.getRoot();
        }

        b.assistantPrivacy.setText(BuildConfig.AI_BASE_URL.isEmpty() ? R.string.assistant_privacy_offline : R.string.assistant_privacy_online);
        b.chipCheck.setOnClickListener(v -> ask(getString(R.string.assistant_quick_check)));
        b.chipSave.setOnClickListener(v -> ask(getString(R.string.assistant_quick_save)));
        b.layoutQuestion.setEndIconOnClickListener(v -> ask(String.valueOf(b.inputQuestion.getText())));
        // The question box is multi-line, so the keyboard shows Enter, not a Send key; the button at the end of the box sends.
        return b.getRoot();
    }

    private void ask(String question) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            b.layoutQuestion.setError(getString(R.string.assistant_question_required));
            return;
        }
        if (asking) return;
        asking = true;
        b.layoutQuestion.setError(null);
        b.assistantReply.setVisibility(View.GONE);
        b.proposals.removeAllViews();
        b.assistantProgress.setVisibility(View.VISIBLE);
        vm.ask(q, new AiAssistant.Callback() {
            @Override public void onReply(AssistantReply reply) {
                asking = false;
                if (b == null) return;                                         
                b.assistantProgress.setVisibility(View.GONE);
                b.assistantReply.setText(reply.text.isEmpty() ? getString(R.string.assistant_no_text) : reply.text);
                b.assistantReply.setVisibility(View.VISIBLE);
                for (ChangeProposal p : reply.proposals) addProposal(p);
            }

            @Override public void onError(String message) {
                asking = false;
                if (b == null) return;
                b.assistantProgress.setVisibility(View.GONE);
                b.assistantReply.setText(message);
                b.assistantReply.setVisibility(View.VISIBLE);
            }
        });
    }

    private void addProposal(ChangeProposal p) {
        ItemSuggestionBinding s = ItemSuggestionBinding.inflate(getLayoutInflater(), b.proposals, false);
        s.suggestionTitle.setText(p.title);
        s.suggestionSource.setText(R.string.suggestion_source_ai);
        s.suggestionBody.setText(Strings.join("\n", ProposalDescriber.lines(p, vm.controller().doc())));
        String effect = ProposalDescriber.effect(p);
        s.suggestionEffect.setText(effect == null ? "" : effect);
        s.suggestionEffect.setVisibility(effect == null ? View.GONE : View.VISIBLE);
        s.suggestionSecondary.setVisibility(View.GONE);
        if (vm.can(Permission.SUGGEST_CHANGES)) {
            s.suggestionPrimary.setText(R.string.assistant_add_suggestion);
            s.suggestionPrimary.setOnClickListener(v -> {
                s.suggestionPrimary.setEnabled(false);
                vm.propose(p, new RepoCallback<Suggestion>() {
                    @Override public void onSuccess(Suggestion value) {
                        if (b == null) return;
                        s.suggestionPrimary.setText(R.string.assistant_added);
                        Snackbar.make(b.getRoot(), R.string.assistant_added_note, Snackbar.LENGTH_LONG).show();
                    }

                    @Override public void onError(Exception e) {
                        if (b == null) return;
                        s.suggestionPrimary.setEnabled(true);
                        Snackbar.make(b.getRoot(), e.getMessage() == null ? getString(R.string.common_error) : e.getMessage(), Snackbar.LENGTH_LONG).show();
                    }
                });
            });
        } else {
            s.suggestionPrimary.setVisibility(View.GONE);
        }
        b.proposals.addView(s.getRoot());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
