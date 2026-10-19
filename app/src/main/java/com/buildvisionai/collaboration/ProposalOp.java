package com.buildvisionai.collaboration;


public final class ProposalOp {
    public enum Type {
        SET_WALL_THICKNESS, SET_WALL_MATERIAL, SET_WALL_KIND, SET_WALL_HEIGHT,
        SET_ROOM_TYPE, RENAME_ROOM,
        SET_DOOR_WIDTH, SET_WINDOW_SIZE,
        SET_FINISH_LEVEL, SET_ROOF_TYPE, SET_ROOF_PITCH, SET_CEILINGS, SET_WINDOW_GRILLS, SET_CONTINGENCY_PCT,
        /** Text only: a recommendation that has no automatic effect. */
        NOTE
    }

    public Type type;
    /** Element id for plan ops; null for settings ops. */
    public String targetId;
    /** Enum name or text value. */
    public String text;
    public double number;
    public double number2;

    public ProposalOp() { }

    public ProposalOp(Type type, String targetId, String text, double number, double number2) {
        this.type = type;
        this.targetId = targetId;
        this.text = text;
        this.number = number;
        this.number2 = number2;
    }

    public static ProposalOp setting(Type t, String text, double number) { return new ProposalOp(t, null, text, number, 0); }

    public static ProposalOp plan(Type t, String targetId, String text, double number) { return new ProposalOp(t, targetId, text, number, 0); }

    public static ProposalOp note(String text) { return new ProposalOp(Type.NOTE, null, text, 0, 0); }

    public boolean changesPlan() {
        switch (type) {
            case SET_WALL_THICKNESS: case SET_WALL_MATERIAL: case SET_WALL_KIND: case SET_WALL_HEIGHT:
            case SET_ROOM_TYPE: case RENAME_ROOM: case SET_DOOR_WIDTH: case SET_WINDOW_SIZE:
                return true;
            default: return false;
        }
    }
}
