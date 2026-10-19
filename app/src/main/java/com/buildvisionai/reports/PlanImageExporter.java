package com.buildvisionai.reports;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.buildvisionai.models.Floor;
import com.buildvisionai.models.RectD;
import com.buildvisionai.planner.EditorController;
import com.buildvisionai.planner.EditorState;
import com.buildvisionai.planner.FloorPlan;
import com.buildvisionai.planner.PlanDocument;
import com.buildvisionai.planner.ScaleModel;
import com.buildvisionai.planner.ViewTransform;
import com.buildvisionai.planner.render.PlanPalette;
import com.buildvisionai.reports.data.ReportModel;
import com.buildvisionai.ui.editor.FloorPlanRenderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws a floor plan as a printed sheet: a title band, the plan at a standard drawing scale (1:50,
 * 1:100 ...) when one fits, a scale bar and, on drafts, a diagonal "not approved" mark. The same
 * drawing code serves PNG files (a bitmap) and PDF pages (vector), because both are just a Canvas.
 *
 * <p>The scale is exact when the sheet is printed on A4 landscape at 100%: the bitmap is sized as
 * A4 at {@link #PNG_DPI}, and a PDF page is A4 at 72 points per inch. Text and line weights follow
 * the sheet's resolution, not the screen's.
 *
 */
public final class PlanImageExporter {
    private PlanImageExporter() {}

    private static final double A4_LANDSCAPE_WIDTH_MM = 297.0;
    /** A4 landscape at about 180 dpi: sharp enough to read dimensions, about 12 MB in memory. */
    public static final int PNG_WIDTH = 2100, PNG_HEIGHT = 1485;
    public static final float PNG_DPI = (float) (PNG_WIDTH / (A4_LANDSCAPE_WIDTH_MM / 25.4));
    /** A PDF page measures in points: 72 per inch. */
    public static final float PDF_DPI = 72f;

    /** One floor, ready to draw. */
    public static final class Sheet {
        public final PlanDocument doc;
        public final Floor floor;
        public final String title;
        public final String subtitle;
        /** Diagonal mark such as "DRAFT - NOT APPROVED"; null for none. */
        public final String watermark;
        final RectD bounds;

        Sheet(PlanDocument doc, Floor floor, RectD bounds, String title, String subtitle, String watermark) {
            this.doc = doc;
            this.floor = floor;
            this.bounds = bounds;
            this.title = title;
            this.subtitle = subtitle;
            this.watermark = watermark;
        }
    }

    /** One sheet per floor that has something drawn on it, lowest floor first. */
    public static List<Sheet> sheets(PlanDocument doc, ReportModel m) {
        List<Sheet> out = new ArrayList<>();
        for (Floor f : doc.floors()) {
            FloorPlan fp = doc.floorPlan(f.id);
            RectD bounds = fp.contentBounds();
            if (bounds == null) continue;
            String subtitle = m.versionText + " · " + m.statusText + " · " + m.generatedAt;
            out.add(new Sheet(doc, f, bounds, m.projectName + " - " + f.name, subtitle, m.watermark));
        }
        return out;
    }

    /**
     * Draws a sheet into the rectangle (0, 0, w, h) of the canvas. {@code dpi} is how many canvas
     * units make an inch of paper when printed at full size (72 for a PDF page).
     */
    public static void draw(Canvas canvas, int w, int h, float dpi, Sheet s) {
        float density = dpi / 90f;                          // text of "12 dp" comes out about 9.6 pt on paper
        float headerH = 46f * density;
        int planH = Math.max(1, Math.round(h - headerH));

        canvas.save();
        canvas.clipRect(0, 0, w, h);
        canvas.drawColor(0xFFFFFFFF);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF1F1F23);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(16f * density);
        canvas.drawText(s.title, 16f * density, 22f * density, p);
        p.setTypeface(Typeface.DEFAULT);
        p.setColor(0xFF4A4A52);
        p.setTextSize(9.5f * density);
        canvas.drawText(s.subtitle, 16f * density, 38f * density, p);
        p.setColor(0xFFCCCCCC);
        p.setStrokeWidth(Math.max(1f, 0.6f * density));
        canvas.drawLine(0, headerH, w, headerH, p);

        EditorController c = new EditorController(s.doc, s.floor.id);
        c.setDensity(density);
        c.setReadOnly(true);
        EditorState st = c.state();
        st.showGrid = false;
        st.showDimensions = true;
        st.showRoomLabels = true;
        ViewTransform vt = c.view();
        vt.setViewSize(w, planH);
        place(vt, s.bounds, dpi, w, planH, density);

        canvas.save();
        canvas.translate(0, headerH);
        canvas.clipRect(0, 0, w, planH);
        new FloorPlanRenderer().draw(canvas, c, PlanPalette.light(), density, dpi);
        canvas.restore();

        if (s.watermark != null) drawWatermark(canvas, w, h, density, s.watermark);
        canvas.restore();
    }

    /** The largest standard scale (1:5 ... 1:500) at which the drawing fits; otherwise whatever scale fits. */
    private static void place(ViewTransform vt, RectD b, float dpi, int w, int planH, float density) {
        double pad = 28 * density;
        double barRoom = 34 * density;                       // the scale bar sits at the bottom left
        double availW = Math.max(1, w - 2 * pad), availH = Math.max(1, planH - 2 * pad - barRoom);
        double bw = Math.max(b.width(), 1000), bh = Math.max(b.height(), 1000);
        double scale = Math.max(ViewTransform.MIN_SCALE, Math.min(availW / bw, availH / bh));
        for (int ratio : ScaleModel.STANDARD_RATIOS) {
            double s = ScaleModel.pxPerMm(dpi, ratio);
            if (bw * s <= availW && bh * s <= availH) {
                scale = s;
                break;
            }
        }
        vt.scale = Math.min(scale, ViewTransform.MAX_SCALE);
        vt.tx = w / 2.0 - b.centerX() * vt.scale;
        vt.ty = (planH - barRoom) / 2.0 - b.centerY() * vt.scale;
    }

    /** A pale diagonal mark across the sheet; it is light enough that what lies under it stays readable. */
    public static void drawWatermark(Canvas canvas, int w, int h, float density, String text) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x26B00020);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        float size = 40f * density;
        p.setTextSize(size);
        float maxWidth = (float) Math.hypot(w, h) * 0.8f;
        float needed = p.measureText(text);
        if (needed > maxWidth) p.setTextSize(size * maxWidth / needed);
        canvas.save();
        canvas.rotate(-30f, w / 2f, h / 2f);
        canvas.drawText(text, w / 2f, h / 2f, p);
        canvas.restore();
    }

    /** One PNG per sheet in {@code dir}. A plan too big for the device's memory fails with a readable message. */
    public static List<File> exportPng(File dir, String baseName, List<Sheet> sheets) throws IOException {
        List<File> files = new ArrayList<>();
        int index = 1;
        for (Sheet s : sheets) {
            File file = new File(dir, String.format(Locale.US, "%s-%d-%s.png", baseName, index++, safeName(s.floor.name)));
            Bitmap bmp;
            try {
                bmp = Bitmap.createBitmap(PNG_WIDTH, PNG_HEIGHT, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError e) {
                throw new IOException("There is not enough free memory on this device to draw the plan image.");
            }
            try {
                draw(new Canvas(bmp), PNG_WIDTH, PNG_HEIGHT, PNG_DPI, s);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("Could not write " + file.getName());
                }
            } finally {
                bmp.recycle();
            }
            files.add(file);
        }
        return files;
    }

    /** Letters, digits and dashes only, so the name is safe in a file path and in a share sheet. */
    static String safeName(String name) {
        String n = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (n.length() > 40) n = n.substring(0, 40);
        return n.isEmpty() ? "plan" : n;
    }
}
