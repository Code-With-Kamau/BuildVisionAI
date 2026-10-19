package com.buildvisionai.data.mapper;

import com.buildvisionai.database.PlanRows;
import com.buildvisionai.database.entity.ColumnEntity;
import com.buildvisionai.database.entity.DoorEntity;
import com.buildvisionai.database.entity.FloorEntity;
import com.buildvisionai.database.entity.FurnitureEntity;
import com.buildvisionai.database.entity.RoomEntity;
import com.buildvisionai.database.entity.RoomWallEntity;
import com.buildvisionai.database.entity.StairEntity;
import com.buildvisionai.database.entity.WallEntity;
import com.buildvisionai.database.entity.WindowEntity;
import com.buildvisionai.models.Column;
import com.buildvisionai.models.ColumnShape;
import com.buildvisionai.models.Door;
import com.buildvisionai.models.DoorType;
import com.buildvisionai.models.Floor;
import com.buildvisionai.models.Furniture;
import com.buildvisionai.models.FurnitureType;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.RoomType;
import com.buildvisionai.models.Stair;
import com.buildvisionai.models.StairType;
import com.buildvisionai.models.SwingDirection;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.WallKind;
import com.buildvisionai.models.WallMaterial;
import com.buildvisionai.models.Window;
import com.buildvisionai.models.WindowType;
import com.buildvisionai.planner.PlanDocument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link PlanDocument} &lt;-&gt; table rows. The in-memory plan is a graph of objects; on disk it is
 * one row per element with real foreign keys (a door points at its wall, a room at its walls through
 * {@code room_walls}). Nothing is stored as a JSON blob.
 *
 * <p>Saving is strict about references because the database enforces them: an element on a floor
 * that does not exist, a door without its wall, or a room listing a missing wall is left out of
 * the rows and reported in {@code warnings} instead of failing the whole save with a constraint
 * error. Derived values (wall length, rotation, door world position, room polygon) are never
 * stored; they are recomputed from the geometry when the plan is opened.
 */
public final class PlanMapper {
    private PlanMapper() {}

  
    public static PlanRows toRows(String versionId, PlanDocument doc, List<String> warnings) {
        PlanRows rows = new PlanRows();
        Set<String> floorIds = new HashSet<>();
        for (Floor f : doc.floors()) {
            floorIds.add(f.id);
            FloorEntity e = new FloorEntity();
            e.versionId = versionId;
            e.id = f.id;
            e.level = f.level;
            e.name = Codes.orEmpty(f.name);
            e.floorToFloorMm = f.floorToFloorMm;
            rows.floors.add(e);
        }

        // walls first: doors, windows and rooms refer to them
        Map<String, Wall> walls = new HashMap<>();
        for (PlanElement el : doc.all()) {
            if (!(el instanceof Wall)) continue;
            Wall w = (Wall) el;
            if (!floorIds.contains(w.floorId)) { skip(warnings, "Wall", w.id, "is on a floor that does not exist"); continue; }
            walls.put(w.id, w);
            WallEntity e = new WallEntity();
            e.versionId = versionId;
            e.id = w.id;
            e.floorId = w.floorId;
            e.startX = w.startX; e.startY = w.startY; e.endX = w.endX; e.endY = w.endY;
            e.thicknessMm = w.thicknessMm;
            e.heightMm = w.heightMm;
            e.kind = Codes.of(w.kind);
            e.material = Codes.of(w.material);
            rows.walls.add(e);
        }

        for (PlanElement el : doc.all()) {
            if (el instanceof Wall) continue;
            if (!floorIds.contains(el.floorId)) { skip(warnings, el.type().name(), el.id, "is on a floor that does not exist"); continue; }
            if (el instanceof Room) {
                addRoom(versionId, (Room) el, walls, rows, warnings);
            } else if (el instanceof Door) {
                Door d = (Door) el;
                Wall w = walls.get(d.wallId);
                if (w == null || !w.floorId.equals(d.floorId)) { skip(warnings, "Door", d.id, "is not attached to a wall on its floor"); continue; }
                DoorEntity e = new DoorEntity();
                e.versionId = versionId;
                e.id = d.id;
                e.floorId = d.floorId;
                e.wallId = d.wallId;
                e.offsetMm = d.offsetMm; e.widthMm = d.widthMm; e.heightMm = d.heightMm;
                e.doorType = Codes.of(d.doorType);
                e.swing = Codes.of(d.swing);
                rows.doors.add(e);
            } else if (el instanceof Window) {
                Window x = (Window) el;
                Wall w = walls.get(x.wallId);
                if (w == null || !w.floorId.equals(x.floorId)) { skip(warnings, "Window", x.id, "is not attached to a wall on its floor"); continue; }
                WindowEntity e = new WindowEntity();
                e.versionId = versionId;
                e.id = x.id;
                e.floorId = x.floorId;
                e.wallId = x.wallId;
                e.offsetMm = x.offsetMm; e.widthMm = x.widthMm; e.heightMm = x.heightMm; e.sillMm = x.sillMm;
                e.windowType = Codes.of(x.windowType);
                rows.windows.add(e);
            } else if (el instanceof Column) {
                Column c = (Column) el;
                ColumnEntity e = new ColumnEntity();
                e.versionId = versionId;
                e.id = c.id;
                e.floorId = c.floorId;
                e.x = c.x; e.y = c.y; e.widthMm = c.widthMm; e.depthMm = c.depthMm; e.rotationDeg = c.rotationDeg;
                e.shape = Codes.of(c.shape);
                e.heightMm = c.heightMm;
                rows.columns.add(e);
            } else if (el instanceof Stair) {
                Stair s = (Stair) el;
                StairEntity e = new StairEntity();
                e.versionId = versionId;
                e.id = s.id;
                e.floorId = s.floorId;
                e.x = s.x; e.y = s.y; e.widthMm = s.widthMm; e.depthMm = s.depthMm; e.rotationDeg = s.rotationDeg;
                e.stairType = Codes.of(s.stairType);
                e.riseMm = s.riseMm;
                e.riserMm = s.riserMm;
                rows.stairs.add(e);
            } else if (el instanceof Furniture) {
                Furniture f = (Furniture) el;
                FurnitureEntity e = new FurnitureEntity();
                e.versionId = versionId;
                e.id = f.id;
                e.floorId = f.floorId;
                e.x = f.x; e.y = f.y; e.widthMm = f.widthMm; e.depthMm = f.depthMm; e.rotationDeg = f.rotationDeg;
                e.furnitureType = Codes.of(f.furnitureType);
                rows.furniture.add(e);
            } else {
                skip(warnings, el.type().name(), el.id, "has a type the database does not know");
            }
        }
        return rows;
    }

    private static void addRoom(String versionId, Room r, Map<String, Wall> walls, PlanRows rows, List<String> warnings) {
        RoomEntity e = new RoomEntity();
        e.versionId = versionId;
        e.id = r.id;
        e.floorId = r.floorId;
        e.name = Codes.orEmpty(r.name);
        e.roomType = Codes.of(r.roomType);
        e.anchorX = r.anchorX;
        e.anchorY = r.anchorY;
        e.areaM2 = r.areaM2;
        e.grossAreaM2 = r.grossAreaM2;
        e.perimeterM = r.perimeterM;
        e.widthM = r.widthM;
        e.lengthM = r.lengthM;
        rows.rooms.add(e);
        Set<String> seen = new HashSet<>();
        int position = 0;
        for (String wallId : r.wallIds) {
            Wall w = walls.get(wallId);
            if (w == null || !w.floorId.equals(r.floorId)) { skip(warnings, "Room", r.id, "lists wall " + wallId + " which is not on its floor"); continue; }
            if (!seen.add(wallId)) continue;
            RoomWallEntity rw = new RoomWallEntity();
            rw.versionId = versionId;
            rw.roomId = r.id;
            rw.wallId = wallId;
            rw.position = position++;
            rows.roomWalls.add(rw);
        }
    }

    
    public static PlanDocument toDocument(PlanRows rows, List<String> warnings) {
        PlanDocument doc = new PlanDocument();
        for (FloorEntity e : rows.floors) {
            Floor f = new Floor(e.id, e.level, e.name);
            f.floorToFloorMm = e.floorToFloorMm;
            doc.putFloor(f);
        }
        for (WallEntity e : rows.walls) {
            String what = "Wall " + e.id;
            Wall w = new Wall(e.id, e.floorId, e.startX, e.startY, e.endX, e.endY,
                    Codes.parse(WallKind.class, e.kind, WallKind.INTERNAL, warnings, what));
            w.material = Codes.parse(WallMaterial.class, e.material, WallMaterial.BLOCK_150, warnings, what);
            w.thicknessMm = e.thicknessMm;
            w.heightMm = e.heightMm;
            doc.put(w);
        }
        Map<String, List<RoomWallEntity>> wallsOfRoom = new HashMap<>();
        for (RoomWallEntity rw : rows.roomWalls) {
            List<RoomWallEntity> l = wallsOfRoom.get(rw.roomId);
            if (l == null) { l = new ArrayList<>(); wallsOfRoom.put(rw.roomId, l); }
            l.add(rw);
        }
        for (RoomEntity e : rows.rooms) {
            Room r = new Room(e.id, e.floorId, e.name,
                    Codes.parse(RoomType.class, e.roomType, RoomType.CUSTOM, warnings, "Room " + e.id), e.anchorX, e.anchorY);
            r.areaM2 = e.areaM2;
            r.grossAreaM2 = e.grossAreaM2;
            r.perimeterM = e.perimeterM;
            r.widthM = e.widthM;
            r.lengthM = e.lengthM;
            List<RoomWallEntity> l = wallsOfRoom.get(e.id);
            if (l != null) {
                Collections.sort(l, Comparator.comparingInt(x -> x.position));
                for (RoomWallEntity rw : l) r.wallIds.add(rw.wallId);
            }
            doc.put(r);
        }
        for (DoorEntity e : rows.doors) {
            String what = "Door " + e.id;
            Door d = new Door(e.id, e.floorId, e.wallId, e.offsetMm,
                    Codes.parse(DoorType.class, e.doorType, DoorType.SINGLE, warnings, what));
            d.widthMm = e.widthMm;
            d.heightMm = e.heightMm;
            d.swing = Codes.parse(SwingDirection.class, e.swing, SwingDirection.LEFT_IN, warnings, what);
            doc.put(d);
        }
        for (WindowEntity e : rows.windows) {
            Window w = new Window(e.id, e.floorId, e.wallId, e.offsetMm,
                    Codes.parse(WindowType.class, e.windowType, WindowType.CASEMENT, warnings, "Window " + e.id));
            w.widthMm = e.widthMm;
            w.heightMm = e.heightMm;
            w.sillMm = e.sillMm;
            doc.put(w);
        }
        for (ColumnEntity e : rows.columns) {
            Column c = new Column(e.id, e.floorId, e.x, e.y,
                    Codes.parse(ColumnShape.class, e.shape, ColumnShape.SQUARE, warnings, "Column " + e.id));
            c.widthMm = e.widthMm;
            c.depthMm = e.depthMm;
            c.rotationDeg = e.rotationDeg;
            c.heightMm = e.heightMm;
            doc.put(c);
        }
        for (StairEntity e : rows.stairs) {
            Stair s = new Stair(e.id, e.floorId, e.x, e.y,
                    Codes.parse(StairType.class, e.stairType, StairType.STRAIGHT, warnings, "Stair " + e.id));
            s.widthMm = e.widthMm;
            s.depthMm = e.depthMm;
            s.rotationDeg = e.rotationDeg;
            s.riseMm = e.riseMm;
            s.riserMm = e.riserMm;
            doc.put(s);
        }
        for (FurnitureEntity e : rows.furniture) {
            Furniture f = new Furniture(e.id, e.floorId, e.x, e.y,
                    Codes.parse(FurnitureType.class, e.furnitureType, FurnitureType.DESK, warnings, "Furniture " + e.id));
            f.widthMm = e.widthMm;
            f.depthMm = e.depthMm;
            f.rotationDeg = e.rotationDeg;
            doc.put(f);
        }
        return doc;
    }

    private static void skip(List<String> warnings, String kind, String id, String why) {
        if (warnings != null) warnings.add(kind + " " + id + " " + why + "; it was not saved.");
    }
}
