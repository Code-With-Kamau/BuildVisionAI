package com.buildvisionai.planner;

import java.util.ArrayDeque;
import java.util.Deque;

/** Undo/redo history of {@link EditTransaction}s with a bounded depth. */
public final class CommandStack {
    private final Deque<EditTransaction> undo = new ArrayDeque<>();
    private final Deque<EditTransaction> redo = new ArrayDeque<>();
    private final int limit;

    public CommandStack() { this(200); }

    public CommandStack(int limit) { this.limit = limit; }

    /** Applies the transaction and records it. */
    public void execute(EditTransaction tx, PlanDocument doc) {
        if (tx.isEmpty()) return;
        tx.apply(doc);
        record(tx);
    }

    /** Records a transaction whose effects are already in the document (live-edited gestures). */
    public void record(EditTransaction tx) {
        if (tx.isEmpty()) return;
        undo.push(tx);
        redo.clear();
        while (undo.size() > limit) undo.removeLast();
    }

    public EditTransaction undo(PlanDocument doc) {
        if (undo.isEmpty()) return null;
        EditTransaction tx = undo.pop();
        tx.revert(doc);
        redo.push(tx);
        return tx;
    }

    public EditTransaction redo(PlanDocument doc) {
        if (redo.isEmpty()) return null;
        EditTransaction tx = redo.pop();
        tx.apply(doc);
        undo.push(tx);
        return tx;
    }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public String undoLabel() { return undo.isEmpty() ? null : undo.peek().label; }
    public String redoLabel() { return redo.isEmpty() ? null : redo.peek().label; }
    public int undoDepth() { return undo.size(); }

    public void clear() { undo.clear(); redo.clear(); }
}
