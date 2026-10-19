package com.buildvisionai.planner;

/** Tools on the editor toolbar. */
public enum ToolType {
    SELECT("Select"), WALL("Wall"), ROOM("Room"), DOOR("Door"), WINDOW("Window"),
    COLUMN("Column"), STAIR("Stair"), FURNITURE("Furniture");

    public final String label;

    ToolType(String label) { this.label = label; }
}
