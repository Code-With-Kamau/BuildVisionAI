package com.buildvisionai.planner.props;

import com.buildvisionai.models.Column;
import com.buildvisionai.models.ColumnShape;
import com.buildvisionai.models.Door;
import com.buildvisionai.models.DoorType;
import com.buildvisionai.models.Furniture;
import com.buildvisionai.models.FurnitureType;
import com.buildvisionai.models.PlacedElement;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.RoomType;
import com.buildvisionai.models.Stair;
import com.buildvisionai.models.StairType;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.WallKind;
import com.buildvisionai.models.WallMaterial;
import com.buildvisionai.models.Window;
import com.buildvisionai.models.WindowType;
import com.buildvisionai.utils.LengthFormatter;
import com.buildvisionai.utils.UnitSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The editable properties of a selected element, as data. The properties sheet is generic: it asks
 * this class what to show, shows it, and asks it to validate and apply what the user typed. Keeping
 * the rules here (limits, defaults that follow a wall kind, hinge flips) means they are tested on
 * the JVM and the Android code is only widgets.
 *
 * <p>Typical use: {@code of(element, units)} to build the form; on a change,
 * {@code validate(element, key, text, units)} and, when it returns null,
 * {@code editor.updateElement(id, label, copy -> apply(copy, key, text, units))}.
 */
public final class ElementFields {
    private ElementFields() {}

    public enum Kind {
        /** A length typed in the user's unit ("200", "0.2 m", "8 in", "1'6\""). */
        LENGTH,
        /** A plain number (degrees). */
        NUMBER,
        TEXT,
        /** One of a fixed list; the value is the option's id. */
        CHOICE,
        /** A button with no value (flip hinge). */
        ACTION
    }

    public static final class Field {
        public final String key;
        public final String label;
        public final Kind kind;
        /** Current value as the user should see it (formatted length, text, option label). */
        public final String value;
        /** For CHOICE: ids and display labels in the same order. */
        public final List<String> choiceIds;
        public final List<String> choiceLabels;
        /** For CHOICE: index of the current value in {@code choiceIds}. */
        public final int selected;

        Field(String key, String label, Kind kind, String value, List<String> ids, List<String> labels, int selected) {
            this.key = key;
            this.label = label;
            this.kind = kind;
            this.value = value;
            this.choiceIds = ids;
            this.choiceLabels = labels;
            this.selected = selected;
        }
    }

    private static final List<String> NONE = Collections.emptyList();

    private static Field length(String key, String label, double mm, UnitSystem u) {
        return new Field(key, label, Kind.LENGTH, LengthFormatter.format(mm, u), NONE, NONE, -1);
    }

    private static Field number(String key, String label, double v) {
        return new Field(key, label, Kind.NUMBER, trim(v), NONE, NONE, -1);
    }

    private static Field text(String key, String label, String v) {
        return new Field(key, label, Kind.TEXT, v == null ? "" : v, NONE, NONE, -1);
    }

    private static Field action(String key, String label) {
        return new Field(key, label, Kind.ACTION, "", NONE, NONE, -1);
    }

    private static <E extends Enum<E>> Field choice(String key, String label, Class<E> type, E current, List<String> labels) {
        E[] all = type.getEnumConstants();
        List<String> ids = new ArrayList<>(all.length);
        for (E e : all) ids.add(e.name());
        return new Field(key, label, Kind.CHOICE, labels.get(current.ordinal()), ids, labels, current.ordinal());
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(java.util.Locale.US, "%.1f", v);
    }

    // ---- what to show ---------------------------------------------------------------------------------

    public static List<Field> of(PlanElement e, UnitSystem u) {
        List<Field> f = new ArrayList<>();
        if (e instanceof Wall) {
            Wall w = (Wall) e;
            f.add(choice("kind", "Wall type", WallKind.class, w.kind, labels(WallKind.values())));
            f.add(choice("material", "Material", WallMaterial.class, w.material, labels(WallMaterial.values())));
            f.add(length("thickness", "Thickness", w.thicknessMm, u));
            f.add(length("height", "Height", w.heightMm, u));
        } else if (e instanceof Door) {
            Door d = (Door) e;
            f.add(choice("doorType", "Door type", DoorType.class, d.doorType, labels(DoorType.values())));
            f.add(length("width", "Width", d.widthMm, u));
            f.add(length("height", "Height", d.heightMm, u));
            f.add(action("flipHinge", "Flip hinge side"));
            f.add(action("flipSide", "Flip swing side"));
        } else if (e instanceof Window) {
            Window w = (Window) e;
            f.add(choice("windowType", "Window type", WindowType.class, w.windowType, labels(WindowType.values())));
            f.add(length("width", "Width", w.widthMm, u));
            f.add(length("height", "Height", w.heightMm, u));
            f.add(length("sill", "Sill height", w.sillMm, u));
        } else if (e instanceof Column) {
            Column c = (Column) e;
            f.add(choice("shape", "Shape", ColumnShape.class, c.shape, labels(ColumnShape.values())));
            f.add(length("width", "Width", c.widthMm, u));
            f.add(length("depth", "Depth", c.depthMm, u));
            f.add(length("height", "Height", c.heightMm, u));
            f.add(number("rotation", "Rotation (degrees)", c.rotationDeg));
        } else if (e instanceof Stair) {
            Stair s = (Stair) e;
            f.add(choice("stairType", "Stair type", StairType.class, s.stairType, labels(StairType.values())));
            f.add(length("width", "Width", s.widthMm, u));
            f.add(length("depth", "Run", s.depthMm, u));
            f.add(length("rise", "Floor to floor", s.riseMm, u));
            f.add(length("riser", "Riser height", s.riserMm, u));
            f.add(number("rotation", "Rotation (degrees)", s.rotationDeg));
        } else if (e instanceof Furniture) {
            Furniture fu = (Furniture) e;
            f.add(choice("furnitureType", "Item", FurnitureType.class, fu.furnitureType, labels(FurnitureType.values())));
            f.add(length("width", "Width", fu.widthMm, u));
            f.add(length("depth", "Depth", fu.depthMm, u));
            f.add(number("rotation", "Rotation (degrees)", fu.rotationDeg));
        } else if (e instanceof Room) {
            Room r = (Room) e;
            f.add(text("name", "Name", r.name));
            f.add(choice("roomType", "Room type", RoomType.class, r.roomType, labels(RoomType.values())));
        }
        return f;
    }

    private static List<String> labels(WallKind[] v) { List<String> l = new ArrayList<>(); for (WallKind x : v) l.add(x.label); return l; }
    private static List<String> labels(WallMaterial[] v) { List<String> l = new ArrayList<>(); for (WallMaterial x : v) l.add(x.label); return l; }
    private static List<String> labels(DoorType[] v) { List<String> l = new ArrayList<>(); for (DoorType x : v) l.add(x.label); return l; }
    private static List<String> labels(WindowType[] v) { List<String> l = new ArrayList<>(); for (WindowType x : v) l.add(x.label); return l; }
    private static List<String> labels(ColumnShape[] v) { List<String> l = new ArrayList<>(); for (ColumnShape x : v) l.add(x.label); return l; }
    private static List<String> labels(StairType[] v) { List<String> l = new ArrayList<>(); for (StairType x : v) l.add(x.label); return l; }
    private static List<String> labels(FurnitureType[] v) { List<String> l = new ArrayList<>(); for (FurnitureType x : v) l.add(x.label); return l; }
    private static List<String> labels(RoomType[] v) { List<String> l = new ArrayList<>(); for (RoomType x : v) l.add(x.label); return l; }

    // ---- limits (mm unless noted) -------------------------------------------------------------------------------

    private static double[] limits(PlanElement e, String key) {
        switch (key) {
            case "thickness": return new double[]{50, 600};
            case "height":
                return e instanceof Wall ? new double[]{1000, 6000}
                        : e instanceof Door ? new double[]{1800, 3000}
                        : e instanceof Window ? new double[]{300, 2500}
                        : new double[]{500, 6000};                       // column
            case "width":
                return e instanceof Door ? new double[]{600, 3000}
                        : e instanceof Window ? new double[]{400, 4000}
                        : e instanceof Column ? new double[]{100, 1000}
                        : e instanceof Stair ? new double[]{600, 3000}
                        : new double[]{100, 5000};                       // furniture
            case "depth":
                return e instanceof Column ? new double[]{100, 1000}
                        : e instanceof Stair ? new double[]{1000, 8000}
                        : new double[]{100, 5000};
            case "sill": return new double[]{0, 2000};
            case "rise": return new double[]{2000, 5000};
            case "riser": return new double[]{120, 220};
            case "rotation": return new double[]{-360, 360};
            default: return null;
        }
    }

    private static String unitHint(double mm, UnitSystem u) {
        return LengthFormatter.format(mm, u);
    }

    // ---- validate and apply -----------------------------------------------------------------------------------------

    /** @return null when {@code text} is acceptable for this field, otherwise a message to show next to it. */
    public static String validate(PlanElement e, String key, String text, UnitSystem u) {
        switch (key) {
            case "name": {
                String t = text == null ? "" : text.trim();
                if (t.isEmpty()) return "Enter a name.";
                if (t.length() > 40) return "Use 40 characters or fewer.";
                return null;
            }
            case "rotation": {
                double v = parseNumber(text);
                if (Double.isNaN(v)) return "Enter a number of degrees.";
                double[] lim = limits(e, key);
                return v < lim[0] || v > lim[1] ? "Enter an angle between -360 and 360." : null;
            }
            default: {
                double[] lim = limits(e, key);
                if (lim == null) return null;                           // choices and actions cannot be invalid
                double mm = parseLength(text, u);
                if (Double.isNaN(mm)) return "Enter a length such as 200, 0.2 m or 8 in.";
                if (mm < lim[0] || mm > lim[1]) return "Enter between " + unitHint(lim[0], u) + " and " + unitHint(lim[1], u) + ".";
                return null;
            }
        }
    }

    private static final java.util.regex.Pattern BARE_NUMBER = java.util.regex.Pattern.compile("^\\s*-?\\d+(?:\\.\\d+)?\\s*$");

    /**
     * Parses a typed length. Component sizes are quoted in millimetres in Kenya (a 230 mm wall, a
     * 900 mm door), so in metric mode a bare number means millimetres here; "0.9 m", "90 cm" and
     * "900mm" all work too. In feet-and-inches mode parsing is the shared {@code LengthFormatter} rule.
     * (Plot and room dimensions elsewhere read a bare number as metres.)
     */
    static double parseLength(String text, UnitSystem u) {
        if (u == UnitSystem.METRIC && text != null && BARE_NUMBER.matcher(text).matches()) return Double.parseDouble(text.trim());
        return LengthFormatter.parseMm(text, u);
    }

    private static double parseNumber(String text) {
        if (text == null) return Double.NaN;
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    /**
     * Applies an already validated value to {@code target} (the private copy the editor hands to its
     * mutator). Unknown keys and unparsable values change nothing.
     */
    public static void apply(PlanElement target, String key, String text, UnitSystem u) {
        double mm = limits(target, key) != null && !"rotation".equals(key) ? parseLength(text, u) : Double.NaN;
        switch (key) {
            case "name":
                if (target instanceof Room) ((Room) target).name = text.trim();
                break;
            case "roomType":
                if (target instanceof Room) ((Room) target).roomType = RoomType.valueOf(text);
                break;
            case "rotation":
                if (target instanceof PlacedElement) {
                    double v = parseNumber(text);
                    if (!Double.isNaN(v)) ((PlacedElement) target).rotationDeg = ((v % 360) + 360) % 360;
                }
                break;
            case "kind":
                if (target instanceof Wall) setWallKind((Wall) target, WallKind.valueOf(text));
                break;
            case "material":
                if (target instanceof Wall) {
                    Wall w = (Wall) target;
                    WallMaterial old = w.material;
                    w.material = WallMaterial.valueOf(text);
                    if (w.thicknessMm == old.thicknessMm) w.thicknessMm = w.material.thicknessMm;   // follow, unless the user set their own
                }
                break;
            case "thickness":
                if (target instanceof Wall && !Double.isNaN(mm)) ((Wall) target).thicknessMm = mm;
                break;
            case "height":
                if (Double.isNaN(mm)) break;
                if (target instanceof Wall) ((Wall) target).heightMm = mm;
                else if (target instanceof Door) ((Door) target).heightMm = mm;
                else if (target instanceof Window) ((Window) target).heightMm = mm;
                else if (target instanceof Column) ((Column) target).heightMm = mm;
                break;
            case "width":
                if (Double.isNaN(mm)) break;
                if (target instanceof Door) ((Door) target).widthMm = mm;
                else if (target instanceof Window) ((Window) target).widthMm = mm;
                else if (target instanceof PlacedElement) ((PlacedElement) target).widthMm = mm;
                break;
            case "depth":
                if (!Double.isNaN(mm) && target instanceof PlacedElement) ((PlacedElement) target).depthMm = mm;
                break;
            case "sill":
                if (!Double.isNaN(mm) && target instanceof Window) ((Window) target).sillMm = mm;
                break;
            case "rise":
                if (!Double.isNaN(mm) && target instanceof Stair) ((Stair) target).riseMm = mm;
                break;
            case "riser":
                if (!Double.isNaN(mm) && target instanceof Stair) ((Stair) target).riserMm = mm;
                break;
            case "doorType":
                if (target instanceof Door) {
                    Door d = (Door) target;
                    DoorType old = d.doorType;
                    d.doorType = DoorType.valueOf(text);
                    if (d.widthMm == old.defaultWidthMm) d.widthMm = d.doorType.defaultWidthMm;      // follow the type's width unless customised
                }
                break;
            case "windowType":
                if (target instanceof Window) {
                    Window w = (Window) target;
                    WindowType old = w.windowType;
                    w.windowType = WindowType.valueOf(text);
                    if (w.widthMm == old.defaultWidthMm) w.widthMm = w.windowType.defaultWidthMm;
                    if (w.heightMm == old.defaultHeightMm) w.heightMm = w.windowType.defaultHeightMm;
                }
                break;
            case "flipHinge":
                if (target instanceof Door) ((Door) target).swing = ((Door) target).swing.flipHinge();
                break;
            case "flipSide":
                if (target instanceof Door) ((Door) target).swing = ((Door) target).swing.flipSide();
                break;
            case "shape":
                if (target instanceof Column) ((Column) target).shape = ColumnShape.valueOf(text);
                break;
            case "stairType":
                if (target instanceof Stair) ((Stair) target).stairType = StairType.valueOf(text);
                break;
            case "furnitureType":
                if (target instanceof Furniture) {
                    Furniture f = (Furniture) target;
                    FurnitureType old = f.furnitureType;
                    f.furnitureType = FurnitureType.valueOf(text);
                    if (f.widthMm == old.defaultWidthMm && f.depthMm == old.defaultDepthMm) {
                        f.widthMm = f.furnitureType.defaultWidthMm;
                        f.depthMm = f.furnitureType.defaultDepthMm;
                    }
                }
                break;
            default:
                break;
        }
    }

    /** Changing the wall type moves thickness and material to the new type's defaults only if they were still the old defaults. */
    private static void setWallKind(Wall w, WallKind next) {
        boolean defaultThickness = w.thicknessMm == w.kind.defaultThicknessMm();
        boolean defaultMaterial = w.material == w.kind.defaultMaterial;
        w.kind = next;
        if (defaultMaterial) w.material = next.defaultMaterial;
        if (defaultThickness) w.thicknessMm = w.material.thicknessMm;
    }
}
