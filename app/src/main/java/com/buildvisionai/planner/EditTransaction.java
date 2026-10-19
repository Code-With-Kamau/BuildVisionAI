package com.buildvisionai.planner;

import com.buildvisionai.models.Floor;
import com.buildvisionai.models.PlanElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One undoable user action: an ordered list of element and floor changes. Changes hold private
 * copies, so a transaction never aliases live objects and can be re-applied or reverted any
 * number of times. Derived changes (rooms re-detected after a wall edit) are added to the same
 * transaction, so a single undo restores everything the action touched.
 */
public final class EditTransaction {
    public enum Kind { ADD, REMOVE, MODIFY }

    public static final class Change {
        public final Kind kind;
        public final PlanElement before;
        public final PlanElement after;

        Change(Kind kind, PlanElement before, PlanElement after) {
            this.kind = kind;
            this.before = before;
            this.after = after;
        }

        public String elementId() { return kind == Kind.ADD ? after.id : before.id; }
    }

    public static final class FloorChange {
        public final Kind kind;
        public final Floor before;
        public final Floor after;

        FloorChange(Kind kind, Floor before, Floor after) {
            this.kind = kind;
            this.before = before;
            this.after = after;
        }
    }

    public final String label;
    private final List<Change> changes = new ArrayList<>();
    private final List<FloorChange> floorChanges = new ArrayList<>();

    public EditTransaction(String label) { this.label = label; }

    public EditTransaction add(PlanElement e) {
        changes.add(new Change(Kind.ADD, null, e.copy()));
        return this;
    }

    public EditTransaction remove(PlanElement e) {
        changes.add(new Change(Kind.REMOVE, e.copy(), null));
        return this;
    }

    /** {@code before} must be a copy taken before mutation; {@code after} is copied here. */
    public EditTransaction modify(PlanElement before, PlanElement after) {
        changes.add(new Change(Kind.MODIFY, before.copy(), after.copy()));
        return this;
    }

    public EditTransaction addFloor(Floor f) { floorChanges.add(new FloorChange(Kind.ADD, null, f.copy())); return this; }
    public EditTransaction removeFloor(Floor f) { floorChanges.add(new FloorChange(Kind.REMOVE, f.copy(), null)); return this; }
    public EditTransaction modifyFloor(Floor before, Floor after) {
        floorChanges.add(new FloorChange(Kind.MODIFY, before.copy(), after.copy()));
        return this;
    }

    /** Appends another transaction's changes without applying them (used for derived room changes). */
    public EditTransaction append(EditTransaction other) {
        changes.addAll(other.changes);
        floorChanges.addAll(other.floorChanges);
        return this;
    }

    public List<Change> changes() { return Collections.unmodifiableList(changes); }
    public List<FloorChange> floorChanges() { return Collections.unmodifiableList(floorChanges); }
    public boolean isEmpty() { return changes.isEmpty() && floorChanges.isEmpty(); }

    /** Applies all changes to the document (floors first, then elements in order). */
    public void apply(PlanDocument doc) {
        for (FloorChange c : floorChanges) {
            if (c.kind == Kind.REMOVE) doc.removeFloor(c.before.id); else doc.putFloor(c.after.copy());
        }
        for (Change c : changes) applyOne(doc, c);
        doc.touch();
    }

    /** Exactly undoes {@link #apply}: elements in reverse order, then floors in reverse order. */
    public void revert(PlanDocument doc) {
        for (int i = changes.size() - 1; i >= 0; i--) revertOne(doc, changes.get(i));
        for (int i = floorChanges.size() - 1; i >= 0; i--) {
            FloorChange c = floorChanges.get(i);
            if (c.kind == Kind.ADD) doc.removeFloor(c.after.id); else doc.putFloor(c.before.copy());
        }
        doc.touch();
    }

    private static void applyOne(PlanDocument doc, Change c) {
        switch (c.kind) {
            case ADD:
            case MODIFY: doc.put(c.after.copy()); break;
            case REMOVE: doc.remove(c.before.id); break;
        }
    }

    private static void revertOne(PlanDocument doc, Change c) {
        switch (c.kind) {
            case ADD: doc.remove(c.after.id); break;
            case REMOVE:
            case MODIFY: doc.put(c.before.copy()); break;
        }
    }
}
