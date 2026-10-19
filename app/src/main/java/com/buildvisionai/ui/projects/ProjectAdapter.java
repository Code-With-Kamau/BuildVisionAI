package com.buildvisionai.ui.projects;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.buildvisionai.R;
import com.buildvisionai.databinding.ItemProjectBinding;
import com.buildvisionai.models.Project;
import com.buildvisionai.models.ProjectStatus;
import com.buildvisionai.utils.MoneyFormatter;

import java.util.Locale;

/** One card per project: name, place, plot and budget, status. Tapping opens it; the menu has the rest. */
public final class ProjectAdapter extends ListAdapter<Project, ProjectAdapter.Holder> {
    public interface Listener {
        void onOpen(Project project);

        void onMore(Project project, android.view.View anchor);
    }

    private static final DiffUtil.ItemCallback<Project> DIFF = new DiffUtil.ItemCallback<Project>() {
        @Override public boolean areItemsTheSame(@NonNull Project a, @NonNull Project b) { return a.projectId.equals(b.projectId); }

        @Override public boolean areContentsTheSame(@NonNull Project a, @NonNull Project b) {
            return a.updatedAt == b.updatedAt && a.status == b.status && a.projectName.equals(b.projectName)
                    && a.headVersionId != null && a.headVersionId.equals(b.headVersionId);
        }
    };

    private final Listener listener;

    public ProjectAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemProjectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Project p = getItem(position);
        Context c = h.b.getRoot().getContext();
        h.b.name.setText(p.projectName);
        String place = p.location == null || p.location.isEmpty() ? p.county : p.location + ", " + p.county;
        h.b.where.setText(place);
        StringBuilder numbers = new StringBuilder(String.format(Locale.US, "%.1f x %.1f m (%.0f m²)", p.plotWidthM, p.plotLengthM, p.plotAreaM2()));
        if (p.budgetKes > 0) numbers.append("  ·  ").append(c.getString(R.string.projects_budget, MoneyFormatter.compact(p.budgetKes)));
        numbers.append("  ·  ").append(DateUtils.getRelativeTimeSpanString(p.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        h.b.numbers.setText(numbers);
        h.b.status.setText(statusLabel(c, p.status));
        h.b.card.setOnClickListener(v -> listener.onOpen(p));
        h.b.more.setContentDescription(c.getString(R.string.projects_more_for, p.projectName));   // each row says which project it acts on
        h.b.more.setOnClickListener(v -> listener.onMore(p, v));
    }

    static String statusLabel(Context c, ProjectStatus s) {
        switch (s) {
            case REVIEW: return c.getString(R.string.status_review);
            case APPROVED: return c.getString(R.string.status_approved);
            case LOCKED: return c.getString(R.string.status_locked);
            default: return c.getString(R.string.status_draft);
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemProjectBinding b;

        Holder(ItemProjectBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
