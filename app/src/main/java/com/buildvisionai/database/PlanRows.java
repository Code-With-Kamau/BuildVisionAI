package com.buildvisionai.database;

import com.buildvisionai.database.entity.FloorEntity;
import com.buildvisionai.database.entity.WallEntity;
import com.buildvisionai.database.entity.RoomEntity;
import com.buildvisionai.database.entity.RoomWallEntity;
import com.buildvisionai.database.entity.DoorEntity;
import com.buildvisionai.database.entity.WindowEntity;
import com.buildvisionai.database.entity.ColumnEntity;
import com.buildvisionai.database.entity.StairEntity;
import com.buildvisionai.database.entity.FurnitureEntity;

import java.util.ArrayList;
import java.util.List;

/** All rows of one version's plan, grouped by table. A plain holder: no Room or Android types. */
public final class PlanRows {
    public final List<FloorEntity> floors = new ArrayList<>();
    public final List<WallEntity> walls = new ArrayList<>();
    public final List<RoomEntity> rooms = new ArrayList<>();
    public final List<RoomWallEntity> roomWalls = new ArrayList<>();
    public final List<DoorEntity> doors = new ArrayList<>();
    public final List<WindowEntity> windows = new ArrayList<>();
    public final List<ColumnEntity> columns = new ArrayList<>();
    public final List<StairEntity> stairs = new ArrayList<>();
    public final List<FurnitureEntity> furniture = new ArrayList<>();

    public int totalRows() {
        return floors.size() + walls.size() + rooms.size() + roomWalls.size() + doors.size() + windows.size() + columns.size() + stairs.size() + furniture.size();
    }

    /** Throws when any row belongs to another version: a plan is never written across versions by accident. */
    public void requireVersion(String versionId) {
        for (FloorEntity r : floors) check(versionId, r.versionId, "floors");
        for (WallEntity r : walls) check(versionId, r.versionId, "walls");
        for (RoomEntity r : rooms) check(versionId, r.versionId, "rooms");
        for (RoomWallEntity r : roomWalls) check(versionId, r.versionId, "room_walls");
        for (DoorEntity r : doors) check(versionId, r.versionId, "doors");
        for (WindowEntity r : windows) check(versionId, r.versionId, "windows");
        for (ColumnEntity r : columns) check(versionId, r.versionId, "columns");
        for (StairEntity r : stairs) check(versionId, r.versionId, "stairs");
        for (FurnitureEntity r : furniture) check(versionId, r.versionId, "furniture");
    }

    private static void check(String expected, String actual, String table) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("Row in " + table + " belongs to version " + actual + ", not " + expected);
        }
    }
}
