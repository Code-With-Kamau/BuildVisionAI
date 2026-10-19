package com.buildvisionai.domain;

import com.buildvisionai.models.Column;
import com.buildvisionai.models.Door;
import com.buildvisionai.models.Floor;
import com.buildvisionai.models.Furniture;
import com.buildvisionai.models.PlacedElement;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.Stair;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.Window;
import com.buildvisionai.planner.PlanDocument;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compares two versions of a plan element by element. Elements keep their ids across versions, so a
 * moved wall shows as "modified", not as a delete plus an add. Derived values (room areas) are
 * ignored: only what the user actually edited is compared.
 */
public final class VersionDiff {
    private VersionDiff() {}

    public enum Change { ADDED, REMOVED, MODIFIED }

    public static final class Entry {
        public final Change change;
        public final String elementId;
        public final String kind;
        public final String label;
    
        public final List<String> details;

        Entry(Change change, String elementId, String kind, String label, List<String> details) {
            this.change = change;
            this.elementId = elementId;
            this.kind = kind;
            this.label = label;
            this.details = details;
        }
    }

    public static final class Result {
        public final List<Entry> entries = new ArrayList<>();
        public final List<String> floorChanges = new ArrayList<>();

        public int count(Change c) {
            int n = 0;
            for (Entry e : entries) if (e.change == c) n++;
            return n;
        }

        public boolean isEmpty() { return entries.isEmpty() && floorChanges.isEmpty(); }

        
        public String summary() {
            if (isEmpty()) return "No changes";
            return count(Change.ADDED) + " added, " + count(Change.REMOVED) + " removed, " + count(Change.MODIFIED) + " modified"
                    + (floorChanges.isEmpty() ? "" : ", " + floorChanges.size() + " floor change(s)");
        }
    }

    public static Result compare(PlanDocument older, PlanDocument newer) {
        Result r = new Result();
        for (PlanElement b : newer.all()) {
            PlanElement a = older.get(b.id);
            if (a == null) {
                r.entries.add(new Entry(Change.ADDED, b.id, kind(b), label(b), new ArrayList<>()));
            } else {
                List<String> d = fieldChanges(fields(a), fields(b));
                if (!d.isEmpty()) r.entries.add(new Entry(Change.MODIFIED, b.id, kind(b), label(b), d));
            }
        }
        for (PlanElement a : older.all()) {
            if (newer.get(a.id) == null) r.entries.add(new Entry(Change.REMOVED, a.id, kind(a), label(a), new ArrayList<>()));
        }
        for (Floor f : newer.floors()) {
            Floor o = older.floor(f.id);
            if (o == null) r.floorChanges.add("Added " + f.name);
            else if (!o.name.equals(f.name) || o.floorToFloorMm != f.floorToFloorMm) r.floorChanges.add("Changed " + f.name);
        }
        for (Floor o : older.floors()) if (newer.floor(o.id) == null) r.floorChanges.add("Removed " + o.name);
        return r;
    }

    static String kind(PlanElement e) { return e.type().name().charAt(0) + e.type().name().substring(1).toLowerCase(Locale.US); }

    static String label(PlanElement e) {
        if (e instanceof Room) return ((Room) e).name;
        if (e instanceof Wall) return String.format(Locale.US, "Wall %.2f m", ((Wall) e).lengthMm() / 1000.0);
        if (e instanceof Door) return ((Door) e).doorType.label;
        if (e instanceof Window) return ((Window) e).windowType.label + " window";
        if (e instanceof Column) return ((Column) e).shape.label + " column";
        if (e instanceof Stair) return ((Stair) e).stairType.label + " stair";
        if (e instanceof Furniture) return ((Furniture) e).furnitureType.label;
        return e.id;
    }

    /** User-editable fields only, as text. */
    static Map<String, String> fields(PlanElement e) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("floor", e.floorId);
        if (e instanceof Wall) {
            Wall w = (Wall) e;
            m.put("start x (mm)", n(w.startX)); m.put("start y (mm)", n(w.startY));
            m.put("end x (mm)", n(w.endX)); m.put("end y (mm)", n(w.endY));
            m.put("thickness (mm)", n(w.thicknessMm)); m.put("height (mm)", n(w.heightMm));
            m.put("kind", w.kind.label); m.put("material", w.material.label);
        } else if (e instanceof Room) {
            Room r = (Room) e;
            m.put("name", r.name); m.put("type", r.roomType.label);
        } else if (e instanceof Door) {
            Door d = (Door) e;
            m.put("wall", d.wallId); m.put("offset (mm)", n(d.offsetMm)); m.put("width (mm)", n(d.widthMm));
            m.put("type", d.doorType.label); m.put("swing", d.swing.name());
        } else if (e instanceof Window) {
            Window w = (Window) e;
            m.put("wall", w.wallId); m.put("offset (mm)", n(w.offsetMm)); m.put("width (mm)", n(w.widthMm));
            m.put("height (mm)", n(w.heightMm)); m.put("type", w.windowType.label);
        } else if (e instanceof PlacedElement) {
            PlacedElement p = (PlacedElement) e;
            m.put("x (mm)", n(p.x)); m.put("y (mm)", n(p.y));
            m.put("width (mm)", n(p.widthMm)); m.put("depth (mm)", n(p.depthMm)); m.put("rotation (deg)", n(p.rotationDeg));
        }
        return m;
    }

    private static String n(double v) { return String.format(Locale.US, "%.0f", v); }

    private static List<String> fieldChanges(Map<String, String> a, Map<String, String> b) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : b.entrySet()) {
            String old = a.get(e.getKey());
            if (old == null || !old.equals(e.getValue())) out.add(e.getKey() + ": " + old + " -> " + e.getValue());
        }
        return out;
    }
}
