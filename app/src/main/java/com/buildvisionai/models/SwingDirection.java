package com.buildvisionai.models;

/**
 * Where a door is hinged and which face of the wall its leaf swings towards, in the wall's own axes
 * (looking from its start point towards its end, with the plan seen from above):
 * <ul>
 *   <li>LEFT hinges at the wall's start end of the opening, RIGHT at its far end;</li>
 *   <li>IN swings to the right-hand face of the wall, OUT to the left-hand face.</li>
 * </ul>
 * "In" and "out" follow the direction the wall was drawn, not the building interior (a wall has no
 * notion of inside); the editor offers "flip hinge" and "flip swing" so the user fixes it in one tap.
 */
public enum SwingDirection {
    LEFT_IN, RIGHT_IN, LEFT_OUT, RIGHT_OUT;

    public boolean hingeLeft() { return this == LEFT_IN || this == LEFT_OUT; }
    public boolean inward() { return this == LEFT_IN || this == RIGHT_IN; }

    public SwingDirection flipHinge() {
        switch (this) {
            case LEFT_IN: return RIGHT_IN;
            case RIGHT_IN: return LEFT_IN;
            case LEFT_OUT: return RIGHT_OUT;
            default: return LEFT_OUT;
        }
    }

    public SwingDirection flipSide() {
        switch (this) {
            case LEFT_IN: return LEFT_OUT;
            case LEFT_OUT: return LEFT_IN;
            case RIGHT_IN: return RIGHT_OUT;
            default: return RIGHT_IN;
        }
    }
}
