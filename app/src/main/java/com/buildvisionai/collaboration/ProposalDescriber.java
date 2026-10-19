package com.buildvisionai.collaboration;

import com.buildvisionai.estimation.FinishLevel;
import com.buildvisionai.estimation.RoofType;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.RoomType;
import com.buildvisionai.models.WallKind;
import com.buildvisionai.models.WallMaterial;
import com.buildvisionai.planner.PlanDocument;
import com.buildvisionai.utils.LengthFormatter;
import com.buildvisionai.utils.MoneyFormatter;
import com.buildvisionai.utils.UnitSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Plain-language lines for what a proposal would change, shown before anyone accepts it: a person
 * should never approve something they cannot read. Resolves element ids against the current plan
 * when one is given (so "Living Room" rather than an id) and says so when the element is gone.
 */
public final class ProposalDescriber {
    private ProposalDescriber() {}

    /** One line per op, in order. Notes come out as their own text. */
    public static List<String> lines(ChangeProposal p, PlanDocument docOrNull) {
        List<String> out = new ArrayList<>();
        if (p == null || p.ops == null) return out;
        for (ProposalOp op : p.ops) {
            if (op == null || op.type == null) continue;
            out.add(line(op, docOrNull));
        }
        return out;
    }

    /** "Estimated cost change: -KES 120,000" or null when the proposal has no estimate. */
    public static String effect(ChangeProposal p) {
        if (p == null || Double.isNaN(p.estimatedDeltaKes) || Double.isInfinite(p.estimatedDeltaKes)) return null;
        double d = p.estimatedDeltaKes;
        return "Estimated cost change: " + (d < 0 ? "-" : "+") + MoneyFormatter.kes(Math.abs(d));
    }

    private static String line(ProposalOp op, PlanDocument doc) {
        String who = subject(op, doc);
        switch (op.type) {
            case SET_WALL_THICKNESS: return who + ": thickness " + mm(op.number);
            case SET_WALL_HEIGHT: return who + ": height " + mm(op.number);
            case SET_WALL_MATERIAL: return who + ": material " + label(WallMaterial.class, op.text, x -> x.label);
            case SET_WALL_KIND: return who + ": type " + label(WallKind.class, op.text, x -> x.label);
            case SET_ROOM_TYPE: return who + ": room type " + label(RoomType.class, op.text, x -> x.label);
            case RENAME_ROOM: return who + ": rename to \"" + (op.text == null ? "" : op.text) + "\"";
            case SET_DOOR_WIDTH: return who + ": width " + mm(op.number);
            case SET_WINDOW_SIZE: return who + ": size " + mm(op.number) + " x " + mm(op.number2);
            case SET_FINISH_LEVEL: return "Finish level: " + label(FinishLevel.class, op.text, x -> x.label);
            case SET_ROOF_TYPE: return "Roof: " + label(RoofType.class, op.text, x -> x.label);
            case SET_ROOF_PITCH: return String.format(Locale.US, "Roof pitch: %.0f degrees", op.number);
            case SET_CEILINGS: return "Ceilings: " + (op.number != 0 ? "included" : "left out");
            case SET_WINDOW_GRILLS: return "Window grills: " + (op.number != 0 ? "included" : "left out");
            case SET_CONTINGENCY_PCT: return String.format(Locale.US, "Contingency: %.1f%%", op.number);
            case NOTE: return "Note: " + (op.text == null ? "" : op.text);
            default: return op.type.name();
        }
    }

    private static String subject(ProposalOp op, PlanDocument doc) {
        String kind;
        switch (op.type) {
            case SET_WALL_THICKNESS: case SET_WALL_HEIGHT: case SET_WALL_MATERIAL: case SET_WALL_KIND: kind = "Wall"; break;
            case SET_ROOM_TYPE: case RENAME_ROOM: kind = "Room"; break;
            case SET_DOOR_WIDTH: kind = "Door"; break;
            case SET_WINDOW_SIZE: kind = "Window"; break;
            default: return "";
        }
        if (doc == null || op.targetId == null) return kind;
        PlanElement e = doc.get(op.targetId);
        if (e == null) return kind + " (no longer in the plan)";
        if (e instanceof Room && ((Room) e).name != null && !((Room) e).name.isEmpty()) return "Room \"" + ((Room) e).name + "\"";
        return kind;
    }

    private static String mm(double v) { return LengthFormatter.format(v, UnitSystem.METRIC); }

    /** The enum's label when the name is valid, otherwise the raw text (never throws on bad input). */
    private static <E extends Enum<E>> String label(Class<E> type, String name, Function<E, String> labeller) {
        if (name == null) return "";
        try {
            return labeller.apply(Enum.valueOf(type, name));
        } catch (IllegalArgumentException ex) {
            return name;
        }
    }
}
