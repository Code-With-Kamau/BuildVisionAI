package com.buildvisionai.ui.estimate;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.buildvisionai.R;
import com.buildvisionai.databinding.ItemCostHeaderBinding;
import com.buildvisionai.databinding.ItemCostLineBinding;
import com.buildvisionai.estimation.CostCategory;
import com.buildvisionai.estimation.CostEstimate;
import com.buildvisionai.estimation.CostLine;
import com.buildvisionai.reports.data.ReportBuilder;
import com.buildvisionai.utils.MoneyFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** The priced lines, grouped under a heading per category. Tapping a line asks to re-quote its price. */
final class CostLineAdapter extends ListAdapter<CostLineAdapter.Row, RecyclerView.ViewHolder> {
    private static final int HEADER = 0, LINE = 1;

    interface Listener {
        void onLineClicked(CostLine line);
    }

    /** A heading or a line. Immutable, so the list can be diffed. */
    static final class Row {
        final CostCategory category;      // headings
        final double categoryAvg;
        final CostLine line;              // lines; null for headings
        final boolean ownPrice;

        private Row(CostCategory category, double categoryAvg, CostLine line, boolean ownPrice) {
            this.category = category;
            this.categoryAvg = categoryAvg;
            this.line = line;
            this.ownPrice = ownPrice;
        }

        boolean isHeader() { return line == null; }
    }

    /** Headings and lines in category order; a category with no lines gets no heading. */
    static List<Row> rowsFor(CostEstimate cost, Predicate<CostLine> ownPrice) {
        List<Row> rows = new ArrayList<>();
        for (CostCategory c : CostCategory.values()) {
            List<CostLine> inCategory = new ArrayList<>();
            for (CostLine l : cost.lines) if (l.category == c) inCategory.add(l);
            if (inCategory.isEmpty()) continue;
            rows.add(new Row(c, cost.category(c).avg, null, false));
            for (CostLine l : inCategory) rows.add(new Row(c, 0, l, ownPrice.test(l)));
        }
        return rows;
    }

    private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<Row>() {
        @Override
        public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
            if (a.isHeader() != b.isHeader()) return false;
            if (a.isHeader()) return a.category == b.category;
            return a.line.item == b.line.item && a.line.category == b.line.category && a.line.basis.equals(b.line.basis);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
            if (a.isHeader()) return a.categoryAvg == b.categoryAvg;
            CostLine x = a.line, y = b.line;
            return a.ownPrice == b.ownPrice && x.qty == y.qty && x.allowance == y.allowance
                    && x.rate.min == y.rate.min && x.rate.avg == y.rate.avg && x.rate.max == y.rate.max;
        }
    };

    private final Listener listener;

    CostLineAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) { return getItem(position).isHeader() ? HEADER : LINE; }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater in = LayoutInflater.from(parent.getContext());
        if (viewType == HEADER) return new HeaderHolder(ItemCostHeaderBinding.inflate(in, parent, false));
        return new LineHolder(ItemCostLineBinding.inflate(in, parent, false), listener);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        if (holder instanceof HeaderHolder) ((HeaderHolder) holder).bind(row);
        else ((LineHolder) holder).bind(row);
    }

    private static final class HeaderHolder extends RecyclerView.ViewHolder {
        private final ItemCostHeaderBinding b;

        HeaderHolder(ItemCostHeaderBinding b) {
            super(b.getRoot());
            this.b = b;
            ViewCompat.setAccessibilityHeading(b.headerTitle, true);
        }

        void bind(Row row) {
            b.headerTitle.setText(row.category.label);
            b.headerTotal.setText(MoneyFormatter.kes(row.categoryAvg));
        }
    }

    private static final class LineHolder extends RecyclerView.ViewHolder {
        private final ItemCostLineBinding b;
        private CostLine line;

        LineHolder(ItemCostLineBinding b, Listener listener) {
            super(b.getRoot());
            this.b = b;
            b.getRoot().setOnClickListener(v -> {
                if (line != null) listener.onLineClicked(line);
            });
        }

        void bind(Row row) {
            CostLine l = row.line;
            line = l;
            android.content.Context c = b.getRoot().getContext();
            b.lineTitle.setText(l.item.description);
            b.lineDetail.setText(c.getString(R.string.estimate_line_detail, ReportBuilder.formatQty(l.qty), l.item.unit,
                    MoneyFormatter.kes(l.rate.avg), MoneyFormatter.kes(l.totalAvg)));
            String note = row.ownPrice ? c.getString(R.string.estimate_line_own_price) : "";
            if (l.allowance) note = join(note, c.getString(R.string.estimate_line_allowance));
            if (l.basis != null && !l.basis.isEmpty()) note = join(note, l.basis);
            b.lineNote.setText(note);
            b.lineNote.setVisibility(note.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        }

        private static String join(String a, String b) { return a.isEmpty() ? b : a + " · " + b; }
    }
}
