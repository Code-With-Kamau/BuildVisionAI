package com.buildvisionai.models;

/**
 * Base class of everything drawn on a floor plan.
 *
 * <p>Domain models are plain mutable data holders with public fields. That is deliberate: they are
 * touched thousands of times per frame while dragging, and Android's performance guidance is to
 * avoid getter/setter indirection on hot paths. Encapsulation is provided one level up
 * ({@code FloorPlan} owns the collections; {@code EditTransaction} is the only way edits reach
 * history). Models never reference Android classes, so they run in plain JVM unit tests.
 *
 * <p>All lengths are millimetres. Ids are stable UUID strings that survive versioning, which is
 * what makes version diffing possible.
 */
public abstract class PlanElement {
    public String id;
    public String floorId;

    protected PlanElement(String id, String floorId) {
        this.id = id;
        this.floorId = floorId;
    }

    public abstract ElementType type();

    /** World-space bounding box (mm). */
    public abstract RectD bounds();

    /** Deep copy preserving the id. */
    public abstract PlanElement copy();
}
