package com.buildvisionai.ui.editor;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.buildvisionai.geometry.DetectedRoom;
import com.buildvisionai.models.Column;
import com.buildvisionai.models.Door;
import com.buildvisionai.models.DoorType;
import com.buildvisionai.models.Furniture;
import com.buildvisionai.models.PlacedElement;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.RectD;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.Stair;
import com.buildvisionai.models.Vec2;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.Window;
import com.buildvisionai.planner.EditorController;
import com.buildvisionai.planner.EditorState;
import com.buildvisionai.planner.FloorPlan;
import com.buildvisionai.planner.Handle;
import com.buildvisionai.planner.HandleGeometry;
import com.buildvisionai.planner.HitTester;
import com.buildvisionai.planner.Preview;
import com.buildvisionai.planner.ScaleModel;
import com.buildvisionai.planner.Selection;
import com.buildvisionai.planner.SnapResult;
import com.buildvisionai.planner.ViewTransform;
import com.buildvisionai.planner.render.GridLayout;
import com.buildvisionai.planner.render.Lod;
import com.buildvisionai.planner.render.PlanPalette;
import com.buildvisionai.planner.render.TextPlacement;
import com.buildvisionai.planner.render.WallJoins;
import com.buildvisionai.planner.render.WallOutline;
import com.buildvisionai.utils.LengthFormatter;
import com.buildvisionai.utils.UnitSystem;

import java.util.List;
import java.util.Locale;

/**
 * Draws one floor of the plan onto a Canvas. It reads the editor's immutable-per-revision
 * {@link FloorPlan} snapshot and never changes the model.
 *
 * <p>Performance rules, because this runs on every frame of a pan or pinch on low-end phones:
 * <ul>
 *   <li>No Paint, Path, RectF or grid buffer is created per frame or per element; the only per-frame
 *       allocations are a few small objects (the visible-element list from the spatial index, one
 *       {@link Lod}).</li>
 *   <li>All walls are one fill Path in plan space, rebuilt only when the snapshot changes; the canvas
 *       matrix does the zooming and the GPU does the rest.</li>
 *   <li>Furniture, openings and rooms come from the spatial index for the visible rectangle only.</li>
 *   <li>{@link Lod} drops text, furniture and door swings when they would be unreadable specks.</li>
 * </ul>
 * Shapes are drawn in plan space (mm) under the canvas matrix, so pixel sizes are divided by the
 * scale; text, handles and markers are drawn in screen space so they stay crisp and constant-sized.
 *
 */
public final class FloorPlanRenderer {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hair = new Paint();

    private final Path wallPath = new Path();
    private final Path tmp = new Path();
    private final RectF oval = new RectF();
    private final GridLayout grid = new GridLayout();

    private FloorPlan cachedPlan;
    private WallJoins joins;

    private int labelBudget;
    private DashPathEffect dash;
    private float dashDensity = -1f;

    public FloorPlanRenderer() {
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.MITER);
        text.setTextAlign(Paint.Align.CENTER);
        hair.setStyle(Paint.Style.STROKE);
        hair.setStrokeWidth(1f);
    }

    /**
     * @param density screen density (dp to px)
     * @param dpi     physical pixels per inch, for the drawing-scale label
     */
    public void draw(Canvas canvas, EditorController c, PlanPalette pal, float density, float dpi) {
        ViewTransform vt = c.view();
        EditorState st = c.state();
        FloorPlan plan = c.plan();
        float s = (float) vt.scale;
        Lod lod = new Lod(vt.scale, density);

        canvas.drawColor(pal.background);

        if (st.showGrid) drawGrid(canvas, vt, c.gridStepMm(), pal);

        if (plan != cachedPlan) rebuild(plan);

        RectD view = vt.visibleWorldRect();
        RectD margin = view.inflate(vt.pxToMm(40 * density));
        List<PlanElement> visible = plan.inRect(margin);

        canvas.save();
        canvas.translate((float) vt.tx, (float) vt.ty);
        canvas.scale(s, s);

        drawRooms(canvas, plan, pal, margin);
        if (lod.showFurniture()) {
            for (PlanElement e : visible) if (e instanceof Furniture) drawPlaced(canvas, (PlacedElement) e, pal, s, density);
        }
        for (PlanElement e : visible) {
            if (e instanceof Column) drawPlaced(canvas, (PlacedElement) e, pal, s, density);
            else if (e instanceof Stair) drawStair(canvas, (Stair) e, pal, s, lod);
        }

        fill.setColor(pal.wall);
        canvas.drawPath(wallPath, fill);

        for (PlanElement e : visible) {
            if (e instanceof Door) drawDoor(canvas, (Door) e, pal, s, lod);
            else if (e instanceof Window) drawWindow(canvas, (Window) e, pal, s, density);
        }

        drawSelection(canvas, c, plan, pal, s, density);
        drawPreviewShapes(canvas, c, pal, s, density);
        canvas.restore();

        // ---- screen-space overlays ----
        labelBudget = lod.maxDimensionLabels();
        text.setTextSize(lod.labelTextPx());
        if (st.showRoomLabels) drawRoomLabels(canvas, plan, st.units, pal, vt, lod, margin);
        if (st.showDimensions) drawDimensions(canvas, plan, st.units, pal, vt, lod, margin);
        drawHandles(canvas, c, pal, vt, density);
        drawSnapMarker(canvas, c.preview(), pal, vt, density);
        drawPreviewLabel(canvas, c.preview(), st.units, pal, vt, lod);
        drawScaleBar(canvas, vt, st.units, pal, density, dpi);
    }

    //caches 

    private void rebuild(FloorPlan plan) {
        cachedPlan = plan;
        joins = WallJoins.compute(plan.walls);
        wallPath.rewind();
        wallPath.setFillType(Path.FillType.WINDING);
        for (int i = 0; i < plan.walls.size(); i++) {
            double[] q = WallOutline.corners(plan.walls.get(i), joins.capStart[i], joins.capEnd[i]);
            wallPath.moveTo((float) q[0], (float) q[1]);
            wallPath.lineTo((float) q[2], (float) q[3]);
            wallPath.lineTo((float) q[4], (float) q[5]);
            wallPath.lineTo((float) q[6], (float) q[7]);
            wallPath.close();
        }
    }

    //  grid 

    private void drawGrid(Canvas canvas, ViewTransform vt, double stepMm, PlanPalette pal) {
        grid.compute(vt, stepMm, ScaleModel.majorStepMm(stepMm));
        hair.setColor(pal.gridMinor);
        hair.setStrokeWidth(1f);
        canvas.drawLines(grid.minor, 0, grid.minorCount, hair);
        hair.setColor(pal.gridMajor);
        hair.setStrokeWidth(1.5f);
        canvas.drawLines(grid.major, 0, grid.majorCount, hair);
    }

    //  rooms 
    private void drawRooms(Canvas canvas, FloorPlan plan, PlanPalette pal, RectD margin) {
        for (Room r : plan.rooms) {
            DetectedRoom d = plan.shapeOf(r);
            if (d == null || !d.bounds.intersects(margin)) continue;
            polygon(tmp, d.netPolygon);
            fill.setColor((r.roomType.fillColor & 0x00FFFFFF) | (pal.roomFillAlpha << 24));
            canvas.drawPath(tmp, fill);
        }
    }

    private static void polygon(Path p, List<Vec2> pts) {
        p.rewind();
        for (int i = 0; i < pts.size(); i++) {
            Vec2 v = pts.get(i);
            if (i == 0) p.moveTo((float) v.x, (float) v.y); else p.lineTo((float) v.x, (float) v.y);
        }
        p.close();
    }

    //  placed elements 

    private void drawPlaced(Canvas canvas, PlacedElement e, PlanPalette pal, float s, float density) {
        boolean round = e.isElliptical() || (e instanceof Furniture && ((Furniture) e).furnitureType.plumbingFixture);
        canvas.save();
        canvas.translate((float) e.x, (float) e.y);
        canvas.rotate((float) e.rotationDeg);
        float hw = (float) e.widthMm / 2, hd = (float) e.depthMm / 2;
        fill.setColor(e instanceof Column ? pal.columnFill : pal.furnitureFill);
        stroke.setColor(pal.furnitureStroke);
        stroke.setStrokeWidth(1.25f * density / s);
        if (round) {
            oval.set(-hw, -hd, hw, hd);
            canvas.drawOval(oval, fill);
            canvas.drawOval(oval, stroke);
        } else {
            canvas.drawRect(-hw, -hd, hw, hd, fill);
            canvas.drawRect(-hw, -hd, hw, hd, stroke);
        }
        canvas.restore();
    }

    private void drawStair(Canvas canvas, Stair st, PlanPalette pal, float s, Lod lod) {
        canvas.save();
        canvas.translate((float) st.x, (float) st.y);
        canvas.rotate((float) st.rotationDeg);
        float hw = (float) st.widthMm / 2, hd = (float) st.depthMm / 2;
        stroke.setColor(pal.stairStroke);
        stroke.setStrokeWidth(Math.max(1.25f, lod.wallMinPx() * 0.8f) / s);
        canvas.drawRect(-hw, -hd, hw, hd, stroke);
        if (lod.showStairSteps()) {
            int n = st.stepCount();
            for (int i = 1; i < n; i++) {
                float y = -hd + (float) st.depthMm * i / n;
                canvas.drawLine(-hw, y, hw, y, stroke);
            }
            canvas.drawLine(0, hd - 150, 0, -hd + 150, stroke);            // direction of climb: towards -y (up the plan)
            canvas.drawLine(0, -hd + 150, -hw * 0.25f, -hd + 150 + hw * 0.25f, stroke);
            canvas.drawLine(0, -hd + 150, hw * 0.25f, -hd + 150 + hw * 0.25f, stroke);
        }
        canvas.restore();
    }

//  openings 
    private void drawDoor(Canvas canvas, Door d, PlanPalette pal, float s, Lod lod) {
        if (!d.attached) return;
        canvas.save();
        canvas.translate((float) d.worldX, (float) d.worldY);
        canvas.rotate((float) d.angleDeg);
        float w = (float) d.widthMm, t = (float) Math.max(d.wallThicknessMm, 100);
        // clear the wall behind the opening
        fill.setColor(pal.background);
        canvas.drawRect(-w / 2, -t / 2 - 4, w / 2, t / 2 + 4, fill);
        if (lod.showOpeningDetail()) {
            stroke.setColor(pal.doorLeaf);
            stroke.setStrokeWidth(Math.max(1.25f, lod.wallMinPx() * 0.8f) / s);
            if (d.doorType == DoorType.SLIDING) {
                canvas.drawRect(-w / 2, -t / 4, 0.05f * w, t / 4, stroke);
                canvas.drawRect(-0.05f * w, 0, w / 2, t / 2, stroke);
            } else if (d.doorType == DoorType.DOUBLE) {
                leaf(canvas, -w / 2, w / 2, true, d.swing.inward(), stroke);
                leaf(canvas, w / 2, w / 2, false, d.swing.inward(), stroke);
            } else {
                leaf(canvas, d.swing.hingeLeft() ? -w / 2 : w / 2, w, d.swing.hingeLeft(), d.swing.inward(), stroke);
            }
        }
        canvas.restore();
    }

    /** One door leaf and its swing arc, in the opening's local axes (x along the wall, +y to the "inside"). */
    private void leaf(Canvas canvas, float hingeX, float length, boolean hingeAtLeft, boolean inward, Paint p) {
        float side = inward ? 1f : -1f;
        canvas.drawLine(hingeX, 0, hingeX, side * length, p);                      // the open leaf, square to the wall
        float closedAngle = hingeAtLeft ? 0f : 180f;                                // the closed leaf lies along the wall
        float openAngle = side > 0 ? 90f : -90f;
        float sweep = openAngle - closedAngle;
        if (sweep > 180f) sweep -= 360f;
        if (sweep < -180f) sweep += 360f;
        oval.set(hingeX - length, -length, hingeX + length, length);
        canvas.drawArc(oval, closedAngle, sweep, false, p);
    }

    private void drawWindow(Canvas canvas, Window win, PlanPalette pal, float s, float density) {
        if (!win.attached) return;
        canvas.save();
        canvas.translate((float) win.worldX, (float) win.worldY);
        canvas.rotate((float) win.angleDeg);
        float w = (float) win.widthMm, t = (float) Math.max(win.wallThicknessMm, 100);
        fill.setColor(pal.windowGlass);
        canvas.drawRect(-w / 2, -t / 2, w / 2, t / 2, fill);
        stroke.setColor(pal.windowFrame);
        stroke.setStrokeWidth(1.5f * density / s);
        canvas.drawRect(-w / 2, -t / 2, w / 2, t / 2, stroke);
        canvas.drawLine(-w / 2, 0, w / 2, 0, stroke);
        canvas.restore();
    }

    //  selection and handles 

    private void drawSelection(Canvas canvas, EditorController c, FloorPlan plan, PlanPalette pal, float s, float density) {
        Selection sel = c.selection();
        if (sel.isEmpty()) return;
        stroke.setColor(pal.selection);
        stroke.setStrokeWidth(2.5f * density / s);
        for (String id : sel.ids()) {
            PlanElement e = c.doc().get(id);
            if (e == null || !plan.floorId.equals(e.floorId)) continue;
            if (e instanceof Wall) {
                Wall w = (Wall) e;
                int i = plan.walls.indexOf(w);
                double[] q = WallOutline.corners(w, i >= 0 && joins.capStart[i], i >= 0 && joins.capEnd[i]);
                tmp.rewind();
                tmp.moveTo((float) q[0], (float) q[1]);
                tmp.lineTo((float) q[2], (float) q[3]);
                tmp.lineTo((float) q[4], (float) q[5]);
                tmp.lineTo((float) q[6], (float) q[7]);
                tmp.close();
                canvas.drawPath(tmp, stroke);
            } else if (e instanceof PlacedElement) {
                PlacedElement p = (PlacedElement) e;
                Vec2[] k = p.corners();
                tmp.rewind();
                tmp.moveTo((float) k[0].x, (float) k[0].y);
                for (int i = 1; i < 4; i++) tmp.lineTo((float) k[i].x, (float) k[i].y);
                tmp.close();
                canvas.drawPath(tmp, stroke);
            } else if (e instanceof Room) {
                DetectedRoom d = plan.shapeOf((Room) e);
                if (d != null) { polygon(tmp, d.netPolygon); canvas.drawPath(tmp, stroke); }
            } else if (e instanceof Door) {
                Door d = (Door) e;
                if (d.attached) openingBox(canvas, d.worldX, d.worldY, d.angleDeg, d.widthMm, Math.max(d.wallThicknessMm, 100));
            } else if (e instanceof Window) {
                Window w = (Window) e;
                if (w.attached) openingBox(canvas, w.worldX, w.worldY, w.angleDeg, w.widthMm, Math.max(w.wallThicknessMm, 100));
            }
        }
    }

    private void openingBox(Canvas canvas, double x, double y, double angleDeg, double w, double t) {
        canvas.save();
        canvas.translate((float) x, (float) y);
        canvas.rotate((float) angleDeg);
        canvas.drawRect((float) (-w / 2 - 20), (float) (-t / 2 - 20), (float) (w / 2 + 20), (float) (t / 2 + 20), stroke);
        canvas.restore();
    }

    private void drawHandles(Canvas canvas, EditorController c, PlanPalette pal, ViewTransform vt, float density) {
        if (!c.canEdit() || !c.selection().isSingle()) return;
        PlanElement e = c.doc().get(c.selection().primaryId());
        if (e == null) return;
        double rotateMm = vt.pxToMm(HitTester.ROTATE_OFFSET_PX);
        float r = 7f * density;
        for (Handle h : HandleGeometry.handlesOf(e)) {
            Vec2 p = HandleGeometry.position(e, h, rotateMm);
            if (p == null) continue;
            float sx = (float) vt.toScreenX(p.x), sy = (float) vt.toScreenY(p.y);
            if (h == Handle.ROTATE) {
                Vec2 top = HandleGeometry.position(e, Handle.CORNER_NW, rotateMm);
                Vec2 top2 = HandleGeometry.position(e, Handle.CORNER_NE, rotateMm);
                if (top != null && top2 != null) {
                    hair.setColor(pal.selection);
                    hair.setStrokeWidth(1.5f * density);
                    canvas.drawLine((float) vt.toScreenX((top.x + top2.x) / 2), (float) vt.toScreenY((top.y + top2.y) / 2), sx, sy, hair);
                }
            }
            fill.setColor(pal.handleFill);
            canvas.drawCircle(sx, sy, r, fill);
            stroke.setColor(pal.selection);
            stroke.setStrokeWidth(2f * density);
            canvas.drawCircle(sx, sy, r, stroke);
        }
    }

    // preview while drawing 
    private void drawPreviewShapes(Canvas canvas, EditorController c, PlanPalette pal, float s, float density) {
        Preview pv = c.preview();
        if (pv.wallStart != null && pv.wallEnd != null) {
            Vec2 a = pv.wallStart, b = pv.wallEnd;
            double len = a.distanceTo(b);
            if (len > 1) {
                Vec2 d = b.minus(a).normalized(), n = d.perp().times(pv.wallThicknessMm / 2);
                tmp.rewind();
                tmp.moveTo((float) (a.x + n.x), (float) (a.y + n.y));
                tmp.lineTo((float) (b.x + n.x), (float) (b.y + n.y));
                tmp.lineTo((float) (b.x - n.x), (float) (b.y - n.y));
                tmp.lineTo((float) (a.x - n.x), (float) (a.y - n.y));
                tmp.close();
                fill.setColor(pal.previewFill);
                canvas.drawPath(tmp, fill);
            }
        }
        PlanElement g = pv.ghost;
        if (g != null) {
            stroke.setColor(pal.selection);
            stroke.setStrokeWidth(2.5f * density / s);
            if (g instanceof PlacedElement) {
                Vec2[] k = ((PlacedElement) g).corners();
                tmp.rewind();
                tmp.moveTo((float) k[0].x, (float) k[0].y);
                for (int i = 1; i < 4; i++) tmp.lineTo((float) k[i].x, (float) k[i].y);
                tmp.close();
                canvas.drawPath(tmp, stroke);
            } else if (g instanceof Door && ((Door) g).attached) {
                Door d = (Door) g;
                openingBox(canvas, d.worldX, d.worldY, d.angleDeg, d.widthMm, Math.max(d.wallThicknessMm, 100));
            } else if (g instanceof Window && ((Window) g).attached) {
                Window w = (Window) g;
                openingBox(canvas, w.worldX, w.worldY, w.angleDeg, w.widthMm, Math.max(w.wallThicknessMm, 100));
            }
        }
    }

    private void drawSnapMarker(Canvas canvas, Preview pv, PlanPalette pal, ViewTransform vt, float density) {
        SnapResult sn = pv.snap;
        if (sn == null || !sn.snapped()) return;
        float x = (float) vt.toScreenX(sn.point.x), y = (float) vt.toScreenY(sn.point.y);
        if (sn.guideA != null && sn.guideB != null) {
            hair.setColor(pal.snapGuide);
            hair.setStrokeWidth(1f * density);
            if (dash == null || dashDensity != density) {
                dash = new DashPathEffect(new float[]{8f * density, 6f * density}, 0f);
                dashDensity = density;
            }
            hair.setPathEffect(dash);
            canvas.drawLine((float) vt.toScreenX(sn.guideA.x), (float) vt.toScreenY(sn.guideA.y),
                    (float) vt.toScreenX(sn.guideB.x), (float) vt.toScreenY(sn.guideB.y), hair);
            hair.setPathEffect(null);
        }
        float r = 6f * density;
        stroke.setColor(pal.snapMarker);
        stroke.setStrokeWidth(2f * density);
        switch (sn.kind) {
            case ENDPOINT:
                canvas.drawRect(x - r, y - r, x + r, y + r, stroke);
                break;
            case MIDPOINT:
                tmp.rewind();
                tmp.moveTo(x, y - r);
                tmp.lineTo(x + r, y + r);
                tmp.lineTo(x - r, y + r);
                tmp.close();
                canvas.drawPath(tmp, stroke);
                break;
            case ON_WALL:
                canvas.drawLine(x - r, y - r, x + r, y + r, stroke);
                canvas.drawLine(x - r, y + r, x + r, y - r, stroke);
                break;
            default:
                canvas.drawCircle(x, y, r * 0.7f, stroke);
                break;
        }
    }

    private void drawPreviewLabel(Canvas canvas, Preview pv, UnitSystem units, PlanPalette pal, ViewTransform vt, Lod lod) {
        if (pv.wallStart == null || pv.wallEnd == null) return;
        double len = pv.wallStart.distanceTo(pv.wallEnd);
        if (len < 1) return;
        double mx = (pv.wallStart.x + pv.wallEnd.x) / 2, my = (pv.wallStart.y + pv.wallEnd.y) / 2;
        text.setColor(pal.selection);
        label(canvas, LengthFormatter.format(len, units), (float) vt.toScreenX(mx), (float) vt.toScreenY(my) - 14f * (lod.labelTextPx() / 12f), 0f);
    }

    //text 

    private void drawRoomLabels(Canvas canvas, FloorPlan plan, UnitSystem units, PlanPalette pal, ViewTransform vt, Lod lod, RectD margin) {
        text.setColor(pal.labelText);
        for (Room r : plan.rooms) {
            DetectedRoom d = plan.shapeOf(r);
            if (d == null || !d.bounds.intersects(margin)) continue;
            double shortSideMm = Math.min(d.widthM, d.lengthM) * 1000.0;
            if (!lod.showRoomLabel(shortSideMm)) continue;
            float sx = (float) vt.toScreenX(d.interior.x), sy = (float) vt.toScreenY(d.interior.y);
            if (lod.showRoomArea(shortSideMm)) {
                String area = units == UnitSystem.METRIC ? LengthFormatter.areaM2(d.netAreaM2) : LengthFormatter.areaSqFt(d.netAreaM2);
                label(canvas, r.name, sx, sy - lod.labelTextPx() * 0.15f, 0f);
                label(canvas, area, sx, sy + lod.labelTextPx() * 1.15f, 0f);
            } else {
                label(canvas, r.name, sx, sy, 0f);
            }
        }
    }

    private void drawDimensions(Canvas canvas, FloorPlan plan, UnitSystem units, PlanPalette pal, ViewTransform vt, Lod lod, RectD margin) {
        text.setColor(pal.dimensionText);
        for (Wall w : plan.walls) {
            if (labelBudget <= 0) return;
            double len = w.lengthMm();
            if (!lod.showDimension(len) || !w.bounds().intersects(margin)) continue;
            Vec2 d = w.direction();
            Vec2 n = d.perp();
            if (n.y > 1e-6 || (Math.abs(n.y) <= 1e-6 && n.x < 0)) n = n.times(-1);       // put the label above (or to the right of) the wall
            double off = w.thicknessMm / 2 + vt.pxToMm(lod.labelTextPx() * 0.8);
            double lx = w.midX() + n.x * off, ly = w.midY() + n.y * off;
            double angle = Math.toDegrees(Math.atan2(d.y, d.x));
            label(canvas, LengthFormatter.format(len, units), (float) vt.toScreenX(lx), (float) vt.toScreenY(ly), (float) TextPlacement.uprightDeg(angle));
            labelBudget--;
        }
    }

    /** Draws centred text, rotated about its own centre. */
    private void label(Canvas canvas, String s, float sx, float sy, float rotationDeg) {
        canvas.save();
        canvas.translate(sx, sy);
        if (rotationDeg != 0f) canvas.rotate(rotationDeg);
        canvas.drawText(s, 0f, -(text.ascent() + text.descent()) / 2f, text);
        canvas.restore();
    }

    //  scale 
    private void drawScaleBar(Canvas canvas, ViewTransform vt, UnitSystem units, PlanPalette pal, float density, float dpi) {
        float pxPerMm = (float) vt.scale;
        double barMm = ScaleModel.scaleBarMm(pxPerMm, 90 * density);
        float barPx = (float) (barMm * pxPerMm);
        float x0 = 16 * density, y = vt.viewH - 20 * density;
        hair.setColor(pal.scaleBar);
        hair.setStrokeWidth(2f * density);
        canvas.drawLine(x0, y, x0 + barPx, y, hair);
        canvas.drawLine(x0, y - 5 * density, x0, y + 5 * density, hair);
        canvas.drawLine(x0 + barPx, y - 5 * density, x0 + barPx, y + 5 * density, hair);
        text.setColor(pal.scaleBar);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(11 * density);
        canvas.drawText(LengthFormatter.format(barMm, units), x0, y - 9 * density, text);
        double ratio = ScaleModel.effectiveRatio(pxPerMm, dpi);
        int near = ScaleModel.nearestRatio(pxPerMm, dpi);
        boolean exact = Math.abs(ratio / near - 1) < 0.04;
        String scaleText = exact ? String.format(Locale.US, "Scale 1:%d", near) : String.format(Locale.US, "Scale ≈ 1:%d", Math.round(ratio));
        canvas.drawText(scaleText, x0, y - 9 * density - 14 * density, text);
        text.setTextAlign(Paint.Align.CENTER);
    }
}
