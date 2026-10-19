package com.buildvisionai.reports;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import com.buildvisionai.reports.data.ReportModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lays a {@link ReportModel} out as an A4 PDF: summary and cost, floors and rooms, the plan drawings
 * (vector, at a standard scale), the quantity schedule, assumptions and warnings, sign-offs and the
 * disclaimer. All content comes from the model, which is unit-tested; this class only paginates.
 *
 * <p>Text pages are portrait A4 (595 x 842 pt); plan pages are landscape. Every page carries the
 * project, version and page number, and drafts carry a diagonal "not approved" mark.
 *
 */
public final class PdfReportWriter {
    private static final int PORTRAIT_W = 595, PORTRAIT_H = 842;
    private static final int LANDSCAPE_W = 842, LANDSCAPE_H = 595;
    private static final float MARGIN = 40f;
    private static final float FOOTER_H = 26f;
    private static final float CELL_PAD = 4f;

    private static final int INK = 0xFF1F1F23, MUTED = 0xFF4A4A52, RULE = 0xFFCCCCCC, HEAD_FILL = 0xFFEFEFEA;

    private final ReportModel m;
    private final PdfDocument pdf = new PdfDocument();
    private final TextPaint body = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint bold = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint h1 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint h2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint();
    private final Paint fill = new Paint();

    private PdfDocument.Page page;
    private Canvas canvas;
    private int pageNumber;
    private float y;

    private PdfReportWriter(ReportModel m) {
        this.m = m;
        body.setColor(INK);
        body.setTextSize(10f);
        bold.setColor(INK);
        bold.setTextSize(10f);
        bold.setTypeface(Typeface.DEFAULT_BOLD);
        small.setColor(MUTED);
        small.setTextSize(8.5f);
        h1.setColor(INK);
        h1.setTextSize(22f);
        h1.setTypeface(Typeface.DEFAULT_BOLD);
        h2.setColor(INK);
        h2.setTextSize(13f);
        h2.setTypeface(Typeface.DEFAULT_BOLD);
        line.setColor(RULE);
        line.setStrokeWidth(0.6f);
        fill.setColor(HEAD_FILL);
        fill.setStyle(Paint.Style.FILL);
    }

    /** Writes the report. {@code sheets} may be empty (nothing drawn yet). */
    public static void write(File out, ReportModel model, List<PlanImageExporter.Sheet> sheets) throws IOException {
        new PdfReportWriter(model).run(out, sheets);
    }

    private void run(File out, List<PlanImageExporter.Sheet> sheets) throws IOException {
        try {
            summary();
            floors();
            for (PlanImageExporter.Sheet s : sheets) planPage(s);
            quantities();
            notes();
            signOffs();
            disclaimer();
            finishPage();
            try (FileOutputStream os = new FileOutputStream(out)) {
                pdf.writeTo(os);
            }
        } finally {
            if (page != null) {
                try {
                    pdf.finishPage(page);                   // a failure part-way must not leave a page open
                } catch (RuntimeException ignored) {
                    // closing below releases it anyway
                }
            }
            pdf.close();
        }
    }

        // sections

    private void summary() {
        newPage(PORTRAIT_W, PORTRAIT_H);
        text(m.title, small, 2f);
        text(m.projectName, h1, 6f);
        keyValue("Owner", m.owner);
        keyValue("Location", join(m.location, m.county));
        keyValue("Building type", m.buildingType);
        keyValue("Plot", m.plotText);
        keyValue("Budget", m.budgetText);
        keyValue("Version", m.versionText);
        keyValue("Status", m.statusText);
        keyValue("Prepared", m.generatedAt);
        gap(10f);

        heading("Cost summary");
        if (m.total != null) {
            text("Typical total: " + m.total.avg, h2, 2f);
            text("Range: " + m.total.min + " to " + m.total.max, body, 2f);
        }
        if (!isEmpty(m.perM2Text)) text(m.perM2Text, body, 2f);
        if (!isEmpty(m.benchmarkText)) text(m.benchmarkText, body, 2f);
        gap(6f);

        List<String[]> rows = new ArrayList<>();
        for (ReportModel.CostRow r : m.costRows) rows.add(new String[]{r.label, r.min, r.avg, r.max, r.share});
        text("Construction cost by category", bold, 3f);
        table(new String[]{"Category", "Lowest", "Typical", "Highest", "Share"},
                new float[]{0.34f, 0.19f, 0.19f, 0.19f, 0.09f}, new boolean[]{false, true, true, true, true}, rows, false);
        gap(6f);

        if (!m.addOnRows.isEmpty()) {
            rows = new ArrayList<>();
            for (ReportModel.CostRow r : m.addOnRows) rows.add(new String[]{r.label, r.min, r.avg, r.max});
            if (m.total != null) rows.add(new String[]{"Total", m.total.min, m.total.avg, m.total.max});
            text("Added on top of the construction cost", bold, 3f);
            table(new String[]{"Item", "Lowest", "Typical", "Highest"},
                    new float[]{0.43f, 0.19f, 0.19f, 0.19f}, new boolean[]{false, true, true, true}, rows, true);
            gap(6f);
        }
        if (!isEmpty(m.vatText)) text(m.vatText, small, 2f);
        if (!isEmpty(m.ratePackText)) text("Prices: " + m.ratePackText, small, 2f);
    }

    private void floors() {
        if (m.floors.isEmpty()) return;
        gap(10f);
        heading("Floors and rooms");
        for (ReportModel.FloorSection f : m.floors) {
            ensure(60f);
            text(f.name, bold, 1f);
            text("Gross area " + f.grossAreaText + " · net floor area " + f.netAreaText + " · " + f.countsText, small, 3f);
            List<String[]> rows = new ArrayList<>();
            for (ReportModel.RoomRow r : f.rooms) rows.add(new String[]{r.name, r.type, r.size, r.area});
            if (!rows.isEmpty()) {
                table(new String[]{"Room", "Type", "Size", "Area"}, new float[]{0.34f, 0.26f, 0.24f, 0.16f},
                        new boolean[]{false, false, true, true}, rows, false);
            }
            gap(8f);
        }
    }

    private void planPage(PlanImageExporter.Sheet sheet) {
        finishPage();
        newPage(LANDSCAPE_W, LANDSCAPE_H);
        PlanImageExporter.draw(canvas, LANDSCAPE_W, LANDSCAPE_H - (int) FOOTER_H, PlanImageExporter.PDF_DPI, sheet);
        y = LANDSCAPE_H;                                    // the page is full; the next section starts a new one
    }

    private void quantities() {
        if (m.quantities.isEmpty()) return;
        finishPage();
        newPage(PORTRAIT_W, PORTRAIT_H);
        heading("Quantity schedule");
        text("Materials are supply-only and labour is installation-only, so nothing is counted twice. Allowances are marked.", small, 4f);
        List<String[]> rows = new ArrayList<>();
        String lastCategory = null;
        for (ReportModel.QuantityRow r : m.quantities) {
            String category = r.category.equals(lastCategory) ? "" : r.category;
            lastCategory = r.category;
            String note = r.allowance ? (isEmpty(r.note) ? "Allowance" : "Allowance. " + r.note) : r.note;
            rows.add(new String[]{category, r.description, r.unit, r.quantity, note == null ? "" : note});
        }
        table(new String[]{"Category", "Item", "Unit", "Quantity", "Basis"},
                new float[]{0.16f, 0.30f, 0.07f, 0.11f, 0.36f}, new boolean[]{false, false, false, true, false}, rows, false);
    }

    private void notes() {
        boolean any = !m.assumptions.isEmpty() || !m.warnings.isEmpty() || !m.issues.isEmpty();
        if (!any) return;
        ensure(90f);
        gap(10f);
        bullets("Assumptions", m.assumptions);
        bullets("Price warnings", m.warnings);
        bullets("Drawing issues to resolve", m.issues);
    }

    private void signOffs() {
        gap(10f);
        ensure(70f);
        heading("Review and approval record");
        if (m.signOffs.isEmpty()) {
            text("No reviews or approvals have been recorded for this version.", body, 2f);
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (ReportModel.SignOff s : m.signOffs) {
            rows.add(new String[]{s.date, s.who + (isEmpty(s.role) ? "" : " (" + s.role + ")"), s.stage, s.decision, s.comment});
        }
        table(new String[]{"Date", "Person", "Stage", "Decision", "Comment"},
                new float[]{0.16f, 0.24f, 0.17f, 0.15f, 0.28f}, new boolean[]{false, false, false, false, false}, rows, false);
    }

    private void disclaimer() {
        gap(12f);
        ensure(80f);
        heading("Important");
        text(m.disclaimer, body, 2f);
    }

    
    // layout helpers

    private float contentWidth() { return page.getInfo().getPageWidth() - 2 * MARGIN; }

    private float bottom() { return page.getInfo().getPageHeight() - MARGIN - FOOTER_H; }

    private void newPage(int w, int h) {
        finishPage();
        pageNumber++;
        page = pdf.startPage(new PdfDocument.PageInfo.Builder(w, h, pageNumber).create());
        canvas = page.getCanvas();
        y = MARGIN;
        if (m.watermark != null && w == PORTRAIT_W) PlanImageExporter.drawWatermark(canvas, w, h, 0.8f, m.watermark);
    }

    private void finishPage() {
        if (page == null) return;
        int w = page.getInfo().getPageWidth(), h = page.getInfo().getPageHeight();
        small.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(m.projectName + " · " + m.versionText + " · " + m.statusText, MARGIN, h - 16f, small);
        small.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Page " + pageNumber, w - MARGIN, h - 16f, small);
        small.setTextAlign(Paint.Align.LEFT);
        pdf.finishPage(page);
        page = null;
        canvas = null;
    }

    // Starts a new page when fewer than {@code needed} points remain. 
    private void ensure(float needed) {
        if (page == null || y + needed > bottom()) newPage(PORTRAIT_W, PORTRAIT_H);
    }

    private void gap(float points) { y += points; }

    private void heading(String title) {
        ensure(40f);
        text(title, h2, 4f);
        canvas.drawLine(MARGIN, y - 2f, MARGIN + contentWidth(), y - 2f, line);
        y += 4f;
    }

    private void keyValue(String key, String value) {
        if (isEmpty(value)) return;
        ensure(16f);
        float keyWidth = 90f;
        canvas.drawText(key, MARGIN, y + 10f, small);
        StaticLayout layout = layout(value, body, (int) (contentWidth() - keyWidth), Layout.Alignment.ALIGN_NORMAL, 3);
        canvas.save();
        canvas.translate(MARGIN + keyWidth, y);
        layout.draw(canvas);
        canvas.restore();
        y += Math.max(layout.getHeight(), 13f) + 1f;
    }

    /** A paragraph, split across pages line by line when it is long. */
    private void text(String value, TextPaint paint, float after) {
        if (isEmpty(value)) return;
        StaticLayout layout = layout(value, paint, (int) contentWidth(), Layout.Alignment.ALIGN_NORMAL, Integer.MAX_VALUE);
        for (int i = 0; i < layout.getLineCount(); i++) {
            float lineHeight = layout.getLineBottom(i) - layout.getLineTop(i);
            if (page == null || y + lineHeight > bottom()) newPage(PORTRAIT_W, PORTRAIT_H);
            int start = layout.getLineStart(i), end = layout.getLineEnd(i);
            String piece = value.substring(start, end).replaceAll("\\s+$", "");
            canvas.drawText(piece, MARGIN, y + (layout.getLineBaseline(i) - layout.getLineTop(i)), paint);
            y += lineHeight;
        }
        y += after;
    }

    private void bullets(String title, List<String> items) {
        if (items.isEmpty()) return;
        ensure(40f);
        text(title, bold, 2f);
        for (String item : items) text("• " + item, body, 1.5f);
        gap(6f);
    }

    /**
     * A table with a shaded header row that repeats on each page. Column widths are fractions of the
     * content width; a cell wraps (up to six lines) and a row never splits across pages.
     */
    private void table(String[] heads, float[] fractions, boolean[] right, List<String[]> rows, boolean boldLast) {
        float width = contentWidth();
        float[] x = new float[fractions.length + 1];
        x[0] = MARGIN;
        for (int i = 0; i < fractions.length; i++) x[i + 1] = x[i] + fractions[i] * width;
        ensure(60f);                                        // the header and at least one row stay together
        drawRow(cellLayouts(heads, x, right, bold), x, true);
        for (int r = 0; r < rows.size(); r++) {
            TextPaint paint = boldLast && r == rows.size() - 1 ? bold : body;
            StaticLayout[] cells = cellLayouts(rows.get(r), x, right, paint);
            if (y + height(cells) > bottom()) {
                newPage(PORTRAIT_W, PORTRAIT_H);
                drawRow(cellLayouts(heads, x, right, bold), x, true);
            }
            drawRow(cells, x, false);
        }
    }

    private static StaticLayout[] cellLayouts(String[] cells, float[] x, boolean[] right, TextPaint paint) {
        StaticLayout[] layouts = new StaticLayout[cells.length];
        for (int i = 0; i < cells.length; i++) {
            int w = Math.max(10, (int) (x[i + 1] - x[i] - 2 * CELL_PAD));
            Layout.Alignment align = right[i] ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL;
            layouts[i] = layout(cells[i] == null ? "" : cells[i], paint, w, align, 6);
        }
        return layouts;
    }

    private static float height(StaticLayout[] cells) {
        float h = 0;
        for (StaticLayout c : cells) h = Math.max(h, c.getHeight());
        return h + 2 * CELL_PAD;
    }

    private void drawRow(StaticLayout[] cells, float[] x, boolean header) {
        float height = height(cells);
        if (header) canvas.drawRect(MARGIN, y, x[x.length - 1], y + height, fill);
        for (int i = 0; i < cells.length; i++) {
            canvas.save();
            canvas.translate(x[i] + CELL_PAD, y + CELL_PAD);
            cells[i].draw(canvas);
            canvas.restore();
        }
        y += height;
        canvas.drawLine(MARGIN, y, x[x.length - 1], y, line);
    }

    private static StaticLayout layout(String value, TextPaint paint, int width, Layout.Alignment align, int maxLines) {
        StaticLayout.Builder b = StaticLayout.Builder.obtain(value, 0, value.length(), paint, Math.max(10, width))
                .setAlignment(align)
                .setIncludePad(false);
        if (maxLines != Integer.MAX_VALUE) b.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END);
        return b.build();
    }

    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    private static String join(String a, String b) {
        if (isEmpty(a)) return b == null ? "" : b;
        if (isEmpty(b)) return a;
        return a + ", " + b;
    }
}
