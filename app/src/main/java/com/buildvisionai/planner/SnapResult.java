package com.buildvisionai.planner;

import com.buildvisionai.models.Vec2;

/** Outcome of a snap: the final point plus optional guide-line ends for drawing feedback. */
public final class SnapResult {
    public final Vec2 point;
    public final SnapKind kind;
    public final Vec2 guideA;
    public final Vec2 guideB;

    public SnapResult(Vec2 point, SnapKind kind, Vec2 guideA, Vec2 guideB) {
        this.point = point;
        this.kind = kind;
        this.guideA = guideA;
        this.guideB = guideB;
    }

    public static SnapResult none(Vec2 p) { return new SnapResult(p, SnapKind.NONE, null, null); }

    public boolean snapped() { return kind != SnapKind.NONE; }
}
