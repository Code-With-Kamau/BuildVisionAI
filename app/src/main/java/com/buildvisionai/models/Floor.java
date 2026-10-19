package com.buildvisionai.models;

/** Level metadata. Each floor stores its own plan; elements reference it via {@code floorId}. */
public final class Floor {
    public String id;
    /** 0 = ground, 1 = first, 2 = second ... */
    public int level;
    public String name;
    public double floorToFloorMm = 3000;

    public Floor(String id, int level, String name) {
        this.id = id;
        this.level = level;
        this.name = name;
    }

    public static String defaultName(int level) {
        switch (level) {
            case 0: return "Ground Floor";
            case 1: return "First Floor";
            case 2: return "Second Floor";
            default: return "Floor " + level;
        }
    }

    public Floor copy() {
        Floor f = new Floor(id, level, name);
        f.floorToFloorMm = floorToFloorMm;
        return f;
    }
}
