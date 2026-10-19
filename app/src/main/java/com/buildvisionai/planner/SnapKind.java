package com.buildvisionai.planner;

/** What a point snapped to, in priority order (earlier wins). Drives the snap indicator shown to the user. */
public enum SnapKind {
    NONE, ENDPOINT, MIDPOINT, ON_WALL, ANGLE, ALIGN_X, ALIGN_Y, ALIGN_XY, GRID
}
