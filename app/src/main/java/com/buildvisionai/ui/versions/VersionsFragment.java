package com.buildvisionai.ui.versions;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.buildvisionai.BuildVisionApp;
import com.buildvisionai.R;
import com.buildvisionai.collaboration.ChangeProposal;
import com.buildvisionai.collaboration.Permission;
import com.buildvisionai.collaboration.PermissionPolicy;
import com.buildvisionai.collaboration.ProposalCodec;
import com.buildvisionai.collaboration.ProposalDescriber;
import com.buildvisionai.collaboration.ReviewGuide;
import com.buildvisionai.databinding.DialogDecisionBinding;
import com.buildvisionai.databinding.FragmentVersionsBinding;
import com.buildvisionai.databinding.ItemApprovalBinding;
import com.buildvisionai.databinding.ItemCommentBinding;
import com.buildvisionai.databinding.ItemMemberBinding;
import com.buildvisionai.databinding.ItemSuggestionBinding;
import com.buildvisionai.databinding.ItemVersionBinding;
import com.buildvisionai.models.Approval;
import com.buildvisionai.models.ApprovalDecision;
import com.buildvisionai.models.Comment;
import com.buildvisionai.models.Member;
import com.buildvisionai.models.Project;
import com.buildvisionai.models.ProjectVersion;
import com.buildvisionai.models.Suggestion;
import com.buildvisionai.models.SuggestionSource;
import com.buildvisionai.models.SuggestionStatus;
import com.buildvisionai.models.UserRole;
import com.buildvisionai.models.WorkflowState;
import com.buildvisionai.reports.data.ReportBuilder;
import com.buildvisionai.repository.RepoCallback;
import com.buildvisionai.ui.common.Dropdowns;
import com.buildvisionai.ui.common.ElementLabels;
import com.buildvisionai.ui.common.Event;
import com.buildvisionai.ui.common.Nav;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The history of a project and the people around it, in four tabs: the versions (open one, start a new
 * draft from it, discard a draft), the review of the selected version (submit, approve, ask for
 * changes, lock, and the record of who decided what), discussion (comments and the suggestions made
 * about it) and the team.
 *
 * <p>The device acts as one team member at a time ("Acting as"), so an architect, an engineer and the
 * owner can take turns on a single phone. That is a convenience, not security. Buttons are offered
 * only when the permission rules allow them for that member, and the repositories check again.
 */
public final class VersionsFragment extends Fragment {
    private static final String ARG_PROJECT_ID = "projectId";
    private static final String ARG_VERSION_ID = "versionId";
    private static final String STATE_TAB = "tab";
    private static final int TAB_VERSIONS = 0, TAB_REVIEW = 1, TAB_DISCUSSION = 2, TAB_TEAM = 3;
    private static final UserRole[] ASSIGNABLE_ROLES = {UserRole.ARCHITECT, UserRole.ENGINEER, UserRole.QUANTITY_SURVEYOR, UserRole.CONTRACTOR};

    private FragmentVersionsBinding b;
    private VersionsViewModel vm;
    private int tab;
    private boolean filling;

    // the latest of each stream; any change redraws the tabs from these
    @Nullable private Project project;
    private List<ProjectVersion> versions;
    private List<Member> members;
    private List<Approval> approvals = Collections.emptyList();
    private List<Comment> comments = Collections.emptyList();
    private List<Suggestion> suggestions = Collections.emptyList();
    @Nullable private String selectedId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentVersionsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        Bundle args = requireArguments();
        vm = new ViewModelProvider(this, new VersionsViewModel.Factory(BuildVisionApp.of(requireContext()),
                args.getString(ARG_PROJECT_ID), args.getString(ARG_VERSION_ID))).get(VersionsViewModel.class);
        tab = saved == null ? 0 : saved.getInt(STATE_TAB, 0);
        versions = null;
        members = null;

        b.toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        TabLayout.Tab first = b.tabs.getTabAt(tab);
        if (first != null) first.select();
        b.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) {
                tab = t.getPosition();
                showTab();
            }

            @Override public void onTabUnselected(TabLayout.Tab t) {}

            @Override public void onTabReselected(TabLayout.Tab t) {}
        });

        b.inputActing.setOnItemClickListener((p, v, position, id) -> {
            List<Member> sorted = sortedMembers();
            List<String> lines = new ArrayList<>();
            for (Member m : sorted) lines.add(memberLine(m));
            int i = Dropdowns.indexOfTapped(p, position, lines);                 // by label, not by position (filtered popup)
            if (!filling && i >= 0) vm.setActing(sorted.get(i).userId);
        });
        Dropdowns.textComesFromModel(b.inputActing);
        wireDiscussionInput();
        wireTeamForm();

        vm.project().observe(getViewLifecycleOwner(), p -> {
            project = p;
            if (p != null) b.toolbar.setSubtitle(p.projectName);
        });
        vm.versions().observe(getViewLifecycleOwner(), list -> {
            versions = list;
            keepSelectionValid();
            render();
        });
        vm.members().observe(getViewLifecycleOwner(), list -> {
            members = list;
            render();
        });
        vm.selected().observe(getViewLifecycleOwner(), id -> {
            selectedId = id;
            render();
        });
        vm.actingUserId().observe(getViewLifecycleOwner(), id -> render());
        vm.approvals().observe(getViewLifecycleOwner(), list -> {
            approvals = list == null ? Collections.emptyList() : list;
            render();
        });
        vm.comments().observe(getViewLifecycleOwner(), list -> {
            comments = list == null ? Collections.emptyList() : list;
            render();
        });
        vm.suggestions().observe(getViewLifecycleOwner(), list -> {
            suggestions = list == null ? Collections.emptyList() : list;
            render();
        });
        vm.messages().observe(getViewLifecycleOwner(), (Event<VersionsViewModel.Notice> e) -> {
            VersionsViewModel.Notice n = e.getIfNotHandled();
            if (n == null) return;
            String text = n.resId != 0 ? getString(n.resId) : n.text == null ? getString(R.string.common_error) : n.text;
            Snackbar.make(b.getRoot(), text, Snackbar.LENGTH_LONG).show();
        });
        vm.openVersion().observe(getViewLifecycleOwner(), e -> {
            String id = e.getIfNotHandled();
            if (id != null) openInEditor(id);
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_TAB, tab);
    }

    private void showTab() {
        if (b == null) return;
        boolean ready = versions != null && members != null;
        b.tabVersions.setVisibility(ready && tab == TAB_VERSIONS ? View.VISIBLE : View.GONE);
        b.tabReview.setVisibility(ready && tab == TAB_REVIEW ? View.VISIBLE : View.GONE);
        b.tabDiscussion.setVisibility(ready && tab == TAB_DISCUSSION ? View.VISIBLE : View.GONE);
        b.tabTeam.setVisibility(ready && tab == TAB_TEAM ? View.VISIBLE : View.GONE);
    }

    /**
     * After a draft is discarded the selected version may be gone; fall back to the head. The head is
     * always the newest version: every new draft and every restore takes the next number and becomes the head.
     */
    private void keepSelectionValid() {
        if (versions == null || versions.isEmpty()) return;
        // Ask the ViewModel, not the field: in a freshly created view the versions observer runs before the
        // selected observer has set selectedId, and a field check would throw the person's selection away.
        String current = vm.selected().getValue();
        for (ProjectVersion v : versions) if (v.id.equals(current)) return;
        vm.select(newestFirst().get(0).id);
    }

    private void openInEditor(String versionId) {
        Bundle args = new Bundle();
        args.putString("versionId", versionId);
        Nav.go(this, R.id.action_versions_to_editor, args);
    }

   
    // drawing the tabs

    private void render() {
        if (b == null || versions == null || members == null) return;
        b.loading.setVisibility(View.GONE);
        showTab();
        Member who = vm.acting(members);
        renderActing(who);
        renderVersions(who);
        ProjectVersion sel = selected();
        renderReview(who, sel);
        renderDiscussion(who, sel);
        renderTeam(who);
    }

    @Nullable
    private ProjectVersion selected() {
        if (versions == null) return null;
        for (ProjectVersion v : versions) if (v.id.equals(selectedId)) return v;
        return null;
    }

    private List<ProjectVersion> newestFirst() {
        List<ProjectVersion> sorted = new ArrayList<>(versions);
        sorted.sort((x, y) -> Integer.compare(y.number, x.number));
        return sorted;
    }

    private List<Member> sortedMembers() {
        List<Member> sorted = new ArrayList<>(members == null ? Collections.<Member>emptyList() : members);
        sorted.sort((x, y) -> x.role != y.role ? Integer.compare(x.role.ordinal(), y.role.ordinal())
                : name(x).compareToIgnoreCase(name(y)));
        return sorted;
    }

    private String name(Member m) {
        return m.displayName == null || m.displayName.trim().isEmpty() ? getString(R.string.versions_unnamed) : m.displayName;
    }

    private String nameOfUser(@Nullable String userId) {
        if (userId != null && members != null) for (Member m : members) if (m.userId.equals(userId)) return name(m);
        return getString(R.string.versions_someone);
    }

    private String memberLine(Member m) {
        return getString(R.string.versions_member_line, name(m), m.role.label);
    }

    private String versionName(ProjectVersion v) {
        boolean plain = v.label == null || v.label.trim().isEmpty() || v.label.equals("Version " + v.number);
        return plain ? getString(R.string.versions_item_title, v.number) : getString(R.string.versions_item_title_label, v.number, v.label);
    }

    private void renderActing(@Nullable Member who) {
        List<Member> sorted = sortedMembers();
        String[] labels = new String[sorted.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = memberLine(sorted.get(i));
        filling = true;
        b.inputActing.setAdapter(new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, labels));
        b.inputActing.setText(who == null ? "" : memberLine(who), false);
        filling = false;
    }

    // versions 

    private void renderVersions(@Nullable Member who) {
        b.versionsList.removeAllViews();
        String head = newestFirst().get(0).id;
        int dp2 = Math.round(2 * getResources().getDisplayMetrics().density);
        for (ProjectVersion v : newestFirst()) {
            ItemVersionBinding row = ItemVersionBinding.inflate(getLayoutInflater(), b.versionsList, false);
            boolean isSelected = v.id.equals(selectedId);
            boolean isHead = v.id.equals(head);
            row.versionTitle.setText(versionName(v));
            row.versionState.setText(ElementLabels.of(requireContext(), v.workflowState));
            String detail = getString(R.string.versions_item_detail, ReportBuilder.formatDate(v.createdAt), nameOfUser(v.createdBy));
            if (isHead) detail += " · " + getString(R.string.versions_latest);
            if (isSelected) detail += " · " + getString(R.string.versions_selected);
            row.versionDetail.setText(detail);
            MaterialCardView card = row.versionCard;
            card.setStrokeWidth(isSelected ? dp2 : 0);
            card.setStrokeColor(MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary));
            card.setOnClickListener(x -> vm.select(v.id));

            row.btnOpen.setText(v.isFrozen() ? R.string.versions_view : R.string.versions_open);
            row.btnOpen.setOnClickListener(x -> openInEditor(v.id));
            row.btnSelect.setOnClickListener(x -> {
                vm.select(v.id);
                selectTab(TAB_REVIEW);
            });

            boolean canDiscard = !v.isFrozen() && versions.size() > 1 && VersionsViewModel.can(who, Permission.EDIT_PLAN, v);
            boolean canCopy = v.isFrozen() && VersionsViewModel.can(who, Permission.CREATE_VERSION, v);
            row.btnMore.setVisibility(canDiscard || canCopy ? View.VISIBLE : View.GONE);
            if (canDiscard) {
                row.btnMore.setText(R.string.versions_discard);
                row.btnMore.setOnClickListener(x -> confirmDiscard(v));
            } else if (canCopy) {
                boolean restore = !isHead;
                row.btnMore.setText(restore ? R.string.versions_restore : R.string.versions_new_draft);
                row.btnMore.setOnClickListener(x -> vm.copyToDraft(v, restore));
            }
            b.versionsList.addView(row.getRoot());
        }
    }

    private void selectTab(int position) {
        TabLayout.Tab t = b.tabs.getTabAt(position);
        if (t != null) t.select();
    }

    private void confirmDiscard(ProjectVersion v) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.versions_discard_title, v.number))
                .setMessage(R.string.versions_discard_message)
                .setPositiveButton(R.string.versions_discard, (d, w) -> vm.discard(v))
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    // review

    private void renderReview(@Nullable Member who, @Nullable ProjectVersion v) {
        b.reviewActions.removeAllViews();
        b.approvalsList.removeAllViews();
        if (v == null) {
            b.reviewTitle.setText("");
            b.reviewState.setText("");
            b.reviewHint.setText("");
            b.reviewNoActions.setText("");
            b.approvalsEmpty.setVisibility(View.GONE);
            return;
        }
        WorkflowState st = v.workflowState;
        b.reviewTitle.setText(versionName(v));
        b.reviewState.setText(ElementLabels.of(requireContext(), st));
        b.reviewHint.setText(hint(st));

        List<ReviewGuide.Action> actions = who == null ? Collections.<ReviewGuide.Action>emptyList() : ReviewGuide.actions(who.role, st);
        for (ReviewGuide.Action a : actions) b.reviewActions.addView(actionButton(a, v));
        if (actions.isEmpty()) {
            b.reviewNoActions.setText(who == null ? getString(R.string.versions_no_acting)
                    : getString(R.string.versions_cannot_act, who.role.label, getString(next(st))));
        } else {
            b.reviewNoActions.setText("");
        }
        b.reviewNoActions.setVisibility(actions.isEmpty() ? View.VISIBLE : View.GONE);

        b.approvalsEmpty.setVisibility(approvals.isEmpty() ? View.VISIBLE : View.GONE);
        for (Approval a : approvals) {
            ItemApprovalBinding row = ItemApprovalBinding.inflate(getLayoutInflater(), b.approvalsList, false);
            String decision = a.decision == ApprovalDecision.APPROVED ? getString(R.string.versions_decision_approved)
                    : getString(R.string.versions_decision_changes);
            String person = a.userName == null || a.userName.trim().isEmpty() ? getString(R.string.versions_unnamed) : a.userName;
            row.approvalTitle.setText(getString(R.string.versions_approval_title, person, a.role == null ? "" : a.role.label, decision));
            row.approvalDetail.setText(getString(R.string.versions_approval_detail,
                    a.stage == null ? "" : ElementLabels.of(requireContext(), a.stage), ReportBuilder.formatDate(a.createdAt)));
            boolean hasComment = a.comment != null && !a.comment.trim().isEmpty();
            row.approvalComment.setText(hasComment ? a.comment : "");
            row.approvalComment.setVisibility(hasComment ? View.VISIBLE : View.GONE);
            b.approvalsList.addView(row.getRoot());
        }
    }

    private MaterialButton actionButton(ReviewGuide.Action a, ProjectVersion v) {
        boolean outlined = a == ReviewGuide.Action.PROFESSIONAL_REQUEST_CHANGES || a == ReviewGuide.Action.OWNER_REQUEST_CHANGES;
        MaterialButton btn = outlined
                ? new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
                : new MaterialButton(requireContext());
        btn.setText(label(a));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(8 * getResources().getDisplayMetrics().density);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(x -> onAction(a, v));
        return btn;
    }

    private void onAction(ReviewGuide.Action a, ProjectVersion v) {
        if (a.takesComment()) {
            askDecision(a, v);
        } else if (a == ReviewGuide.Action.SUBMIT) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.versions_confirm_submit_title)
                    .setMessage(R.string.versions_confirm_submit_message)
                    .setPositiveButton(R.string.versions_action_submit, (d, w) -> vm.review(a, v, null))
                    .setNegativeButton(R.string.common_cancel, null)
                    .show();
        } else {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.versions_confirm_lock_title)
                    .setMessage(R.string.versions_confirm_lock_message)
                    .setPositiveButton(R.string.versions_action_lock, (d, w) -> vm.review(a, v, null))
                    .setNegativeButton(R.string.common_cancel, null)
                    .show();
        }
    }

    /** Approving takes an optional note; asking for changes needs one, so the author knows what to fix. */
    private void askDecision(ReviewGuide.Action a, ProjectVersion v) {
        DialogDecisionBinding d = DialogDecisionBinding.inflate(getLayoutInflater());
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(label(a))
                .setView(d.getRoot())
                .setPositiveButton(R.string.common_send, null)               // set below, so a missing note keeps the dialog open
                .setNegativeButton(R.string.common_cancel, null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v2 -> {
            CharSequence t = d.inputDecisionComment.getText();
            String note = t == null ? "" : t.toString().trim();
            if (a.commentRequired() && note.isEmpty()) {
                d.layoutDecisionComment.setError(getString(R.string.versions_decision_note_required));
                return;
            }
            vm.review(a, v, note);
            dialog.dismiss();
        }));
        dialog.show();
    }

    @StringRes
    private static int label(ReviewGuide.Action a) {
        switch (a) {
            case SUBMIT: return R.string.versions_action_submit;
            case PROFESSIONAL_APPROVE: return R.string.versions_action_professional_approve;
            case PROFESSIONAL_REQUEST_CHANGES:
            case OWNER_REQUEST_CHANGES: return R.string.versions_action_request_changes;
            case OWNER_APPROVE: return R.string.versions_action_owner_approve;
            default: return R.string.versions_action_lock;
        }
    }

    @StringRes
    private static int hint(WorkflowState s) {
        switch (s) {
            case PROFESSIONAL_REVIEW: return R.string.workflow_hint_professional_review;
            case OWNER_REVIEW: return R.string.workflow_hint_owner_review;
            case APPROVED: return R.string.workflow_hint_approved;
            case LOCKED: return R.string.workflow_hint_locked;
            default: return R.string.workflow_hint_draft;
        }
    }

    @StringRes
    private static int next(WorkflowState s) {
        switch (s) {
            case PROFESSIONAL_REVIEW: return R.string.workflow_next_professional_review;
            case OWNER_REVIEW: return R.string.workflow_next_owner_review;
            case APPROVED: return R.string.workflow_next_approved;
            case LOCKED: return R.string.workflow_next_locked;
            default: return R.string.workflow_next_draft;
        }
    }

    // discussion

    private void wireDiscussionInput() {
        b.btnAddComment.setOnClickListener(x -> {
            ProjectVersion v = selected();
            CharSequence t = b.inputComment.getText();
            String text = t == null ? "" : t.toString().trim();
            if (v == null) return;
            if (text.isEmpty()) {
                b.layoutComment.setError(getString(R.string.versions_comment_required));
                return;
            }
            b.layoutComment.setError(null);
            b.btnAddComment.setEnabled(false);
            vm.addComment(v, text, new RepoCallback<Boolean>() {
                @Override public void onSuccess(Boolean ok) {
                    if (b == null) return;
                    b.btnAddComment.setEnabled(true);
                    b.inputComment.setText("");
                }

                @Override public void onError(Exception e) {
                    if (b != null) b.btnAddComment.setEnabled(true);
                }
            });
        });
    }

    private void renderDiscussion(@Nullable Member who, @Nullable ProjectVersion v) {
        b.commentsList.removeAllViews();
        b.suggestionsList.removeAllViews();
        if (v == null) {
            b.discussionVersion.setText("");
            return;
        }
        boolean canComment = VersionsViewModel.can(who, Permission.COMMENT, v);
        b.discussionVersion.setText(getString(R.string.versions_discussion_of, versionName(v)));
        b.layoutComment.setEnabled(canComment);
        b.inputComment.setEnabled(canComment);
        b.btnAddComment.setEnabled(canComment);

        List<Comment> sorted = new ArrayList<>(comments);
        sorted.sort((x, y) -> Long.compare(x.createdAt, y.createdAt));
        b.commentsEmpty.setVisibility(sorted.isEmpty() ? View.VISIBLE : View.GONE);
        for (Comment c : sorted) {
            ItemCommentBinding row = ItemCommentBinding.inflate(getLayoutInflater(), b.commentsList, false);
            String author = c.authorName == null || c.authorName.trim().isEmpty() ? getString(R.string.versions_unnamed) : c.authorName;
            row.commentAuthor.setText(getString(R.string.versions_member_line, author, c.authorRole == null ? "" : c.authorRole.label));
            String meta = ReportBuilder.formatDate(c.createdAt);
            if (c.resolved) meta += " · " + getString(R.string.versions_resolved);
            row.commentMeta.setText(meta);
            row.commentText.setText(c.text);
            row.commentText.setAlpha(c.resolved ? 0.7f : 1f);
            row.btnResolve.setVisibility(canComment ? View.VISIBLE : View.GONE);
            row.btnResolve.setText(c.resolved ? R.string.versions_reopen : R.string.versions_resolve);
            row.btnResolve.setOnClickListener(x -> vm.setResolved(v, c, !c.resolved));
            b.commentsList.addView(row.getRoot());
        }

        List<Suggestion> sug = new ArrayList<>(suggestions);
        sug.sort((x, y) -> Long.compare(y.createdAt, x.createdAt));
        b.suggestionsEmpty.setVisibility(sug.isEmpty() ? View.VISIBLE : View.GONE);
        for (Suggestion s : sug) b.suggestionsList.addView(suggestionCard(s));
    }

    /** Read-only: suggestions are decided in the editor, where accepting applies them to the draft. */
    private View suggestionCard(Suggestion s) {
        ItemSuggestionBinding c = ItemSuggestionBinding.inflate(getLayoutInflater(), b.suggestionsList, false);
        c.suggestionTitle.setText(s.title);
        String origin = s.source == SuggestionSource.AI ? getString(R.string.suggestion_source_ai)
                : getString(R.string.suggestion_source_person, s.authorName == null ? "" : s.authorName);
        c.suggestionSource.setText(getString(R.string.suggestion_source_line, origin, statusText(s.status)));
        boolean hasWhy = s.rationale != null && !s.rationale.isEmpty();
        c.suggestionBody.setText(hasWhy ? s.rationale : "");
        c.suggestionBody.setVisibility(hasWhy ? View.VISIBLE : View.GONE);
        String effect = null;
        try {
            ChangeProposal p = ProposalCodec.fromJson(s.payloadJson);
            if (p != null && p.ops != null) effect = ProposalDescriber.effect(p);
        } catch (RuntimeException ignored) {
            // an unreadable payload simply has no effect line
        }
        c.suggestionEffect.setText(effect == null ? "" : effect);
        c.suggestionEffect.setVisibility(effect == null ? View.GONE : View.VISIBLE);
        c.suggestionPrimary.setVisibility(View.GONE);
        c.suggestionSecondary.setVisibility(View.GONE);
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

    // team 
    private void wireTeamForm() {
        String[] roles = new String[ASSIGNABLE_ROLES.length];
        for (int i = 0; i < roles.length; i++) roles[i] = ASSIGNABLE_ROLES[i].label;
        b.inputMemberRole.setAdapter(new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, roles));
        b.inputMemberRole.setText(roles[0], false);
        b.btnAddMember.setOnClickListener(x -> addMember());
    }

    private UserRole chosenRole() {
        CharSequence t = b.inputMemberRole.getText();
        String text = t == null ? "" : t.toString();
        for (UserRole r : ASSIGNABLE_ROLES) if (r.label.equals(text)) return r;
        return ASSIGNABLE_ROLES[0];
    }

    private void addMember() {
        CharSequence n = b.inputMemberName.getText();
        String name = n == null ? "" : n.toString().trim();
        if (name.length() < 2) {
            b.layoutMemberName.setError(getString(R.string.versions_member_name_required));
            return;
        }
        b.layoutMemberName.setError(null);
        CharSequence r = b.inputMemberReg.getText();
        b.btnAddMember.setEnabled(false);
        vm.addMember(name, chosenRole(), r == null ? null : r.toString(), new RepoCallback<Boolean>() {
            @Override public void onSuccess(Boolean ok) {
                if (b == null) return;
                b.btnAddMember.setEnabled(true);
                b.inputMemberName.setText("");
                b.inputMemberReg.setText("");
            }

            @Override public void onError(Exception e) {
                if (b != null) b.btnAddMember.setEnabled(true);
            }
        });
    }

    private void renderTeam(@Nullable Member who) {
        boolean canManage = who != null && PermissionPolicy.can(who.role, Permission.MANAGE_MEMBERS, WorkflowState.DRAFT);
        b.teamNote.setText(canManage ? R.string.versions_team_note_owner : R.string.versions_team_note);
        b.addMemberForm.setVisibility(canManage ? View.VISIBLE : View.GONE);
        b.membersList.removeAllViews();
        for (Member m : sortedMembers()) {
            ItemMemberBinding row = ItemMemberBinding.inflate(getLayoutInflater(), b.membersList, false);
            row.memberName.setText(name(m));
            String line = m.role.label;
            if (m.registrationNo != null && !m.registrationNo.trim().isEmpty()) {
                line = m.role.regulator == null ? getString(R.string.versions_member_reg_line, line, m.registrationNo)
                        : getString(R.string.versions_member_reg_line_regulator, line, m.registrationNo, m.role.regulator);
            }
            row.memberRole.setText(line);
            boolean removable = canManage && m.role != UserRole.OWNER;
            row.btnRemove.setVisibility(removable ? View.VISIBLE : View.GONE);
            row.btnRemove.setContentDescription(getString(R.string.versions_remove_member_for, name(m)));
            row.btnRemove.setOnClickListener(x -> new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.versions_remove_title, name(m)))
                    .setMessage(R.string.versions_remove_message)
                    .setPositiveButton(R.string.versions_remove_member, (d, w) -> vm.removeMember(m))
                    .setNegativeButton(R.string.common_cancel, null)
                    .show());
            b.membersList.addView(row.getRoot());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
