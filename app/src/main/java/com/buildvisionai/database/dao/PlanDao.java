package com.buildvisionai.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import com.buildvisionai.database.PlanRows;
import com.buildvisionai.database.entity.FloorEntity;
import com.buildvisionai.database.entity.WallEntity;
import com.buildvisionai.database.entity.RoomEntity;
import com.buildvisionai.database.entity.RoomWallEntity;
import com.buildvisionai.database.entity.DoorEntity;
import com.buildvisionai.database.entity.WindowEntity;
import com.buildvisionai.database.entity.ColumnEntity;
import com.buildvisionai.database.entity.StairEntity;
import com.buildvisionai.database.entity.FurnitureEntity;

import java.util.List;

/**
 * Plan geometry of one version. A version's plan is always written and read as a whole, inside one
 * transaction, so a reader never sees half a plan.
 *
 * Inserts use the default ABORT strategy on purpose: a clash means a bug, and REPLACE would
 * delete-then-insert a row and cascade to its children. Rows are keyed (versionId, id), so element
 * ids stay the same from one version to the next and versions can be diffed by id.
 *
 *Writes to a version that is not a draft are rejected by the triggers in
 * {@link com.buildvisionai.database.VersionGuard}.
 */
@Dao
public abstract class PlanDao {

    @Insert
    public abstract void insertFloors(List<FloorEntity> rows);

    @Insert
    public abstract void insertWalls(List<WallEntity> rows);

    @Insert
    public abstract void insertRooms(List<RoomEntity> rows);

    @Insert
    public abstract void insertRoomWalls(List<RoomWallEntity> rows);

    @Insert
    public abstract void insertDoors(List<DoorEntity> rows);

    @Insert
    public abstract void insertWindows(List<WindowEntity> rows);

    @Insert
    public abstract void insertColumns(List<ColumnEntity> rows);

    @Insert
    public abstract void insertStairs(List<StairEntity> rows);

    @Insert
    public abstract void insertFurniture(List<FurnitureEntity> rows);

    @Query("SELECT * FROM floors WHERE versionId = :versionId ORDER BY level")
    public abstract List<FloorEntity> floors(String versionId);

    @Query("SELECT * FROM walls WHERE versionId = :versionId ORDER BY id")
    public abstract List<WallEntity> walls(String versionId);

    @Query("SELECT * FROM rooms WHERE versionId = :versionId ORDER BY id")
    public abstract List<RoomEntity> rooms(String versionId);

    @Query("SELECT * FROM room_walls WHERE versionId = :versionId ORDER BY roomId, position")
    public abstract List<RoomWallEntity> roomWalls(String versionId);

    @Query("SELECT * FROM doors WHERE versionId = :versionId")
    public abstract List<DoorEntity> doors(String versionId);

    @Query("SELECT * FROM windows WHERE versionId = :versionId")
    public abstract List<WindowEntity> windows(String versionId);

    @Query("SELECT * FROM columns WHERE versionId = :versionId")
    public abstract List<ColumnEntity> columns(String versionId);

    @Query("SELECT * FROM stairs WHERE versionId = :versionId")
    public abstract List<StairEntity> stairs(String versionId);

    @Query("SELECT * FROM furniture WHERE versionId = :versionId")
    public abstract List<FurnitureEntity> furniture(String versionId);

    /** Deleting the floors cascades to everything else in the plan. */
    @Query("DELETE FROM floors WHERE versionId = :versionId")
    public abstract void deleteFloors(String versionId);

    @Query("SELECT COUNT(*) FROM walls WHERE versionId = :versionId")
    public abstract int wallCount(String versionId);

    @Query("SELECT COUNT(*) FROM floors WHERE versionId = :versionId")
    public abstract int floorCount(String versionId);


    @Query("INSERT INTO floors (versionId, id, level, name, floorToFloorMm) "
         + "SELECT :toVersion, id, level, name, floorToFloorMm FROM floors WHERE versionId = :fromVersion")
    public abstract void copyFloors(String fromVersion, String toVersion);

    @Query("INSERT INTO walls (versionId, id, floorId, startX, startY, endX, endY, thicknessMm, heightMm, kind, material) "
         + "SELECT :toVersion, id, floorId, startX, startY, endX, endY, thicknessMm, heightMm, kind, material FROM walls WHERE versionId = :fromVersion")
    public abstract void copyWalls(String fromVersion, String toVersion);

    @Query("INSERT INTO rooms (versionId, id, floorId, name, roomType, anchorX, anchorY, areaM2, grossAreaM2, perimeterM, widthM, lengthM) "
         + "SELECT :toVersion, id, floorId, name, roomType, anchorX, anchorY, areaM2, grossAreaM2, perimeterM, widthM, lengthM FROM rooms WHERE versionId = :fromVersion")
    public abstract void copyRooms(String fromVersion, String toVersion);

    @Query("INSERT INTO room_walls (versionId, roomId, wallId, position) "
         + "SELECT :toVersion, roomId, wallId, position FROM room_walls WHERE versionId = :fromVersion")
    public abstract void copyRoomWalls(String fromVersion, String toVersion);

    @Query("INSERT INTO doors (versionId, id, floorId, wallId, offsetMm, widthMm, heightMm, doorType, swing) "
         + "SELECT :toVersion, id, floorId, wallId, offsetMm, widthMm, heightMm, doorType, swing FROM doors WHERE versionId = :fromVersion")
    public abstract void copyDoors(String fromVersion, String toVersion);

    @Query("INSERT INTO windows (versionId, id, floorId, wallId, offsetMm, widthMm, heightMm, sillMm, windowType) "
         + "SELECT :toVersion, id, floorId, wallId, offsetMm, widthMm, heightMm, sillMm, windowType FROM windows WHERE versionId = :fromVersion")
    public abstract void copyWindows(String fromVersion, String toVersion);

    @Query("INSERT INTO columns (versionId, id, floorId, x, y, widthMm, depthMm, rotationDeg, shape, heightMm) "
         + "SELECT :toVersion, id, floorId, x, y, widthMm, depthMm, rotationDeg, shape, heightMm FROM columns WHERE versionId = :fromVersion")
    public abstract void copyColumns(String fromVersion, String toVersion);

    @Query("INSERT INTO stairs (versionId, id, floorId, x, y, widthMm, depthMm, rotationDeg, stairType, riseMm, riserMm) "
         + "SELECT :toVersion, id, floorId, x, y, widthMm, depthMm, rotationDeg, stairType, riseMm, riserMm FROM stairs WHERE versionId = :fromVersion")
    public abstract void copyStairs(String fromVersion, String toVersion);

    @Query("INSERT INTO furniture (versionId, id, floorId, x, y, widthMm, depthMm, rotationDeg, furnitureType) "
         + "SELECT :toVersion, id, floorId, x, y, widthMm, depthMm, rotationDeg, furnitureType FROM furniture WHERE versionId = :fromVersion")
    public abstract void copyFurniture(String fromVersion, String toVersion);

    /** Replaces the whole plan of a draft version. Rows must all belong to {@code versionId}. */
    @Transaction
    public void replacePlan(String versionId, PlanRows rows) {
        rows.requireVersion(versionId);
        deleteFloors(versionId);

        if (!rows.floors.isEmpty()) insertFloors(rows.floors);
        if (!rows.walls.isEmpty()) insertWalls(rows.walls);
        if (!rows.rooms.isEmpty()) insertRooms(rows.rooms);
        if (!rows.roomWalls.isEmpty()) insertRoomWalls(rows.roomWalls);
        if (!rows.doors.isEmpty()) insertDoors(rows.doors);
        if (!rows.windows.isEmpty()) insertWindows(rows.windows);
        if (!rows.columns.isEmpty()) insertColumns(rows.columns);
        if (!rows.stairs.isEmpty()) insertStairs(rows.stairs);
        if (!rows.furniture.isEmpty()) insertFurniture(rows.furniture);
    }

    /** A consistent snapshot of one version's plan. */
    @Transaction
    public PlanRows loadPlan(String versionId) {
        PlanRows r = new PlanRows();
        r.floors.addAll(floors(versionId));
        r.walls.addAll(walls(versionId));
        r.rooms.addAll(rooms(versionId));
        r.roomWalls.addAll(roomWalls(versionId));
        r.doors.addAll(doors(versionId));
        r.windows.addAll(windows(versionId));
        r.columns.addAll(columns(versionId));
        r.stairs.addAll(stairs(versionId));
        r.furniture.addAll(furniture(versionId));
        return r;
    }

    /**
     * Copies the whole plan of one version into another (which must already exist as a draft). Row
     * order follows the foreign keys: floors, walls, rooms, then everything that points at them.
     */
    @Transaction
    public void clonePlan(String fromVersion, String toVersion) {
        copyFloors(fromVersion, toVersion);
        copyWalls(fromVersion, toVersion);
        copyRooms(fromVersion, toVersion);
        copyRoomWalls(fromVersion, toVersion);
        copyDoors(fromVersion, toVersion);
        copyWindows(fromVersion, toVersion);
        copyColumns(fromVersion, toVersion);
        copyStairs(fromVersion, toVersion);
        copyFurniture(fromVersion, toVersion);
    }
}
