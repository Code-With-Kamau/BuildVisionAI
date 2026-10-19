package com.buildvisionai.collaboration;

import com.buildvisionai.estimation.EstimateSettings;
import com.buildvisionai.estimation.FinishLevel;
import com.buildvisionai.estimation.RoofType;
import com.buildvisionai.models.Door;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.RoomType;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.WallKind;
import com.buildvisionai.models.WallMaterial;
import com.buildvisionai.models.Window;
import com.buildvisionai.planner.EditorController;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Applies an accepted proposal. Every op is validated against the current plan and same limits;
 * anything invalid is skipped and reported, never half-applied. All plan changes land as ONE undo
 * step on the draft.
 */
public final class ProposalApplier {
    private ProposalApplier() {}

    public static final class Result {
        public int applied;
        public final List<String> skipped = new ArrayList<>();
    }

    public static Result apply(ChangeProposal p, EditorController editor, EstimateSettings settings) {
        Result res = new Result();
        List<EditorController.ElementEdit> edits = new ArrayList<>();
        for (ProposalOp op : p.ops) {
            if (op.type == ProposalOp.Type.NOTE) continue;
            // NaN and infinity pass every "outside the range" comparison, so they are refused up front
            String problem = !Double.isFinite(op.number) || !Double.isFinite(op.number2) ? "value is not a finite number"
                    : op.changesPlan() ? planEdit(op, editor, edits) : settingEdit(op, settings);
            if (problem != null) res.skipped.add(op.type + ": " + problem);
            else if (!op.changesPlan()) res.applied++;
        }
        if (!edits.isEmpty()) {
            int changed = editor.updateElements("Apply suggestion: " + p.title, edits);
            if (changed > 0) res.applied += edits.size();
            else res.skipped.add("plan changes were not applied (this version cannot be edited)");
        }
        return res;
    }

    private static String planEdit(ProposalOp op, EditorController ed, List<EditorController.ElementEdit> out) {
        PlanElement e = op.targetId == null ? null : ed.doc().get(op.targetId);
        if (e == null) return "the element no longer exists";
        Consumer<PlanElement> m;
        switch (op.type) {
            case SET_WALL_THICKNESS:
                if (!(e instanceof Wall)) return "target is not a wall";
                if (op.number < 50 || op.number > 450) return "thickness " + op.number + " mm is outside 50-450 mm";
                m = x -> ((Wall) x).thicknessMm = op.number;
                break;
            case SET_WALL_HEIGHT:
                if (!(e instanceof Wall)) return "target is not a wall";
                if (op.number < 2000 || op.number > 4500) return "height " + op.number + " mm is outside 2000-4500 mm";
                m = x -> ((Wall) x).heightMm = op.number;
                break;
            case SET_WALL_MATERIAL: {
                if (!(e instanceof Wall)) return "target is not a wall";
                WallMaterial mat = parse(WallMaterial.class, op.text);
                if (mat == null) return "unknown material " + op.text;
                m = x -> { ((Wall) x).material = mat; ((Wall) x).thicknessMm = mat.thicknessMm; };
                break;
            }
            case SET_WALL_KIND: {
                if (!(e instanceof Wall)) return "target is not a wall";
                WallKind k = parse(WallKind.class, op.text);
                if (k == null) return "unknown wall kind " + op.text;
                m = x -> ((Wall) x).kind = k;
                break;
            }
            case SET_ROOM_TYPE: {
                if (!(e instanceof Room)) return "target is not a room";
                RoomType t = parse(RoomType.class, op.text);
                if (t == null) return "unknown room type " + op.text;
                m = x -> ((Room) x).roomType = t;
                break;
            }
            case RENAME_ROOM:
                if (!(e instanceof Room)) return "target is not a room";
                if (op.text == null || op.text.trim().isEmpty() || op.text.length() > 60) return "invalid name";
                m = x -> ((Room) x).name = op.text.trim();
                break;
            case SET_DOOR_WIDTH:
                if (!(e instanceof Door)) return "target is not a door";
                if (op.number < 600 || op.number > 3000) return "width " + op.number + " mm is outside 600-3000 mm";
                m = x -> ((Door) x).widthMm = op.number;
                break;
            case SET_WINDOW_SIZE:
                if (!(e instanceof Window)) return "target is not a window";
                if (op.number < 400 || op.number > 3000 || op.number2 < 400 || op.number2 > 2400) return "window size out of range";
                m = x -> { ((Window) x).widthMm = op.number; ((Window) x).heightMm = op.number2; };
                break;
            default:
                return "not a plan change";
        }
        out.add(new EditorController.ElementEdit(e.id, m));
        return null;
    }

    private static String settingEdit(ProposalOp op, EstimateSettings s) {
        switch (op.type) {
            case SET_FINISH_LEVEL: {
                FinishLevel l = parse(FinishLevel.class, op.text);
                if (l == null) return "unknown finish level " + op.text;
                s.finishLevel = l;
                return null;
            }
            case SET_ROOF_TYPE: {
                RoofType r = parse(RoofType.class, op.text);
                if (r == null) return "unknown roof type " + op.text;
                s.roofType = r;
                return null;
            }
            case SET_ROOF_PITCH:
                if (op.number < 5 || op.number > 45) return "pitch " + op.number + " deg is outside 5-45";
                s.roofPitchDeg = op.number;
                return null;
            case SET_CEILINGS:
                s.ceilings = op.number != 0;
                return null;
            case SET_WINDOW_GRILLS:
                s.windowGrills = op.number != 0;
                return null;
            case SET_CONTINGENCY_PCT:
                if (op.number < 0 || op.number > 30) return "contingency must be 0-30%";
                s.contingencyPct = op.number;
                return null;
            default:
                return "not a settings change";
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> c, String name) {
        if (name == null) return null;
        try {
            return Enum.valueOf(c, name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
