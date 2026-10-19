package com.buildvisionai.planner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.models.Door;
import com.buildvisionai.models.Floor;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.RoomType;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.WallKind;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class EditorControllerTest {
    EditorController c;
    PlanDocument doc;
    final List<String> messages = new ArrayList<>();
    String floorId;

    @Before public void setUp() {
        doc = new PlanDocument();
        Floor f = new Floor("floor0", 0, "Ground Floor");
        doc.putFloor(f);
        floorId = f.id;
        c = new EditorController(doc, f.id);
        c.view().scale = 0.1;           // 1 px = 10 mm
        c.view().tx = 100;
        c.view().ty = 100;
        c.view().setViewSize(1000, 1000);
        c.state().snap.angle = true;
        c.setListener(new EditorListener() {
            public void onInvalidate() { }
            public void onDocumentChanged() { }
            public void onSelectionChanged() { }
            public void onToolChanged() { }
            public void onMessage(String t) { messages.add(t); }
            public void onRoomEditRequested(String id) { messages.add("room:" + id); }
        });
    }

    float sx(double wx) { return (float) c.view().toScreenX(wx); }
    float sy(double wy) { return (float) c.view().toScreenY(wy); }

    void tap(double wx, double wy) { c.onTap(sx(wx), sy(wy)); }

    void drag(double x1, double y1, double x2, double y2) {
        assertTrue("drag should be consumed", c.onDragStart(sx(x1), sy(y1)));
        int steps = 6;
        for (int i = 1; i <= steps; i++) c.onDrag(sx(x1 + (x2 - x1) * i / steps), sy(y1 + (y2 - y1) * i / steps));
        c.onDragEnd();
    }

    /** Draws a 5 m x 4 m room by tap-tap and closes it on the start point. */
    void drawRoom() {
        c.setTool(ToolType.WALL);
        tap(0, 0);
        tap(5000, 0);
        tap(5000, 4000);
        tap(0, 4000);
        tap(0, 0);
    }

    @Test public void tapTapChainClosesIntoARoom() {
        drawRoom();
        FloorPlan fp = c.plan();
        assertEquals(4, fp.walls.size());
        assertEquals(1, fp.rooms.size());
        Room r = fp.rooms.get(0);
        assertEquals("Room 1", r.name);
        assertEquals(RoomType.CUSTOM, r.roomType);
        assertEquals(4.8 * 3.8, r.areaM2, 1e-6);   // external walls are 200 mm
        assertEquals(20.0, r.grossAreaM2, 1e-6);
        assertEquals(4, r.wallIds.size());
        assertNotNull(fp.shapeOf(r));
    }

    @Test public void undoWalksBackOneWallAtATimeAndRemovesTheRoomWithTheLastWall() {
        drawRoom();
        assertEquals(1, c.plan().rooms.size());
        assertTrue(c.undo());                       // removes the closing wall AND the room
        assertEquals(3, c.plan().walls.size());
        assertEquals(0, c.plan().rooms.size());
        assertTrue(c.undo());
        assertEquals(2, c.plan().walls.size());
        assertTrue(c.redo());
        assertTrue(c.redo());
        assertEquals(4, c.plan().walls.size());
        assertEquals(1, c.plan().rooms.size());     // room restored by redo
        assertEquals("Room 1", c.plan().rooms.get(0).name);
    }

    @Test public void renamedRoomKeepsItsNameWhenAWallSplitsIt() {
        drawRoom();
        c.updateElement(c.plan().rooms.get(0).id, "Edit room", e -> {
            ((Room) e).name = "Master Bedroom";
            ((Room) e).roomType = RoomType.MASTER_BEDROOM;
        });
        assertEquals("Master Bedroom", c.plan().rooms.get(0).name);

        c.setTool(ToolType.WALL);
        c.state().wallKind = WallKind.INTERNAL;
        drag(3000, 0, 3000, 4000);                  // partition from wall to wall
        FloorPlan fp = c.plan();
        assertEquals(2, fp.rooms.size());
        Room left = null, right = null;
        for (Room r : fp.rooms) { if (r.anchorX < 3000) left = r; else right = r; }
        assertEquals("Master Bedroom", left.name);   // anchor was in the left half
        assertEquals(RoomType.CUSTOM, right.roomType);
        assertEquals("Room 1", right.name);
        assertEquals(2.825 * 3.8, left.areaM2, 1e-6);

        c.undo();                                    // one undo removes partition + the new room
        assertEquals(1, c.plan().rooms.size());
        assertEquals("Master Bedroom", c.plan().rooms.get(0).name);
        assertEquals(4.8 * 3.8, c.plan().rooms.get(0).areaM2, 1e-6);
    }

    @Test public void doorPlacementSnapsToAFreeGapAndRefusesOverlap() {
        drawRoom();
        c.setTool(ToolType.DOOR);
        tap(2500, 5);                                // bottom? top wall y=0, middle
        assertEquals(1, c.plan().doors.size());
        Door d = c.plan().doors.get(0);
        assertEquals(2500, d.offsetMm, 1e-6);
        tap(2600, 5);                                // overlaps the first door: moved to nearest free gap
        assertEquals(2, c.plan().doors.size());
        Door d2 = c.plan().doors.get(1);
        double gap = Math.abs(d2.offsetMm - d.offsetMm);
        assertTrue("doors must not overlap, gap=" + gap, gap >= 900 + OpeningPlacer.GAP_MM - 1e-6);
        assertTrue(c.plan().doors.get(0).attached);
    }

    @Test public void deletingAWallDeletesItsOpeningsAndBreaksTheRoom() {
        drawRoom();
        c.setTool(ToolType.DOOR);
        tap(2500, 5);
        c.setTool(ToolType.SELECT);
        tap(5000, 2000);                             // right wall
        assertEquals(1, c.selection().size());
        assertTrue(c.plan().wallsById.containsKey(c.selection().primaryId()));
        c.deleteSelection();
        assertEquals(3, c.plan().walls.size());
        assertEquals(0, c.plan().rooms.size());
        assertEquals(1, c.plan().doors.size());      // door was on the top wall, untouched
        c.undo();
        assertEquals(4, c.plan().walls.size());
        assertEquals(1, c.plan().rooms.size());
    }

    @Test public void deletingTheWallThatCarriesADoorRemovesTheDoorAndUndoRestoresBoth() {
        drawRoom();
        c.setTool(ToolType.DOOR);
        tap(2500, 5);
        String wallId = c.plan().doors.get(0).wallId;
        c.setTool(ToolType.SELECT);
        c.selection().set(wallId);
        c.deleteSelection();
        assertEquals(0, c.plan().doors.size());
        c.undo();
        assertEquals(1, c.plan().doors.size());
        assertTrue(c.plan().doors.get(0).attached);
    }

    @Test public void draggingAWallMovesConnectedWallsAndIsOneUndoStep() {
        drawRoom();
        int depth = c.undoLabel() == null ? 0 : 1;
        c.setTool(ToolType.SELECT);
        // pick the right wall (x=5000) and drag it 1000 mm to the right
        tap(5000, 2000);
        drag(5000, 2000, 6000, 2000);
        FloorPlan fp = c.plan();
        double minX = 1e9, maxX = -1e9;
        for (Wall w : fp.walls) { minX = Math.min(minX, Math.min(w.startX, w.endX)); maxX = Math.max(maxX, Math.max(w.startX, w.endX)); }
        assertEquals(0, minX, 1e-6);
        assertEquals(6000, maxX, 1e-6);
        assertEquals(1, fp.rooms.size());
        assertEquals(5.8 * 3.8, fp.rooms.get(0).areaM2, 1e-6);   // top and bottom stretched, still closed
        assertEquals("Move", c.undoLabel());
        c.undo();
        fp = c.plan();
        maxX = -1e9;
        for (Wall w : fp.walls) maxX = Math.max(maxX, Math.max(w.startX, w.endX));
        assertEquals(5000, maxX, 1e-6);
        assertEquals(4.8 * 3.8, fp.rooms.get(0).areaM2, 1e-6);
        assertTrue(depth >= 0);
    }

    @Test public void endpointHandleDragReshapesAndKeepsJoins() {
        drawRoom();
        c.setTool(ToolType.SELECT);
        // select the top wall then drag its right end (5000,0) to (5000,-1000)
        tap(2500, 0);
        assertEquals(1, c.selection().size());
        drag(5000, 0, 5000, -1000);
        FloorPlan fp = c.plan();
        int joined = 0;
        for (Wall w : fp.walls) {
            if (Math.hypot(w.startX - 5000, w.startY + 1000) < 1) joined++;
            if (Math.hypot(w.endX - 5000, w.endY + 1000) < 1) joined++;
        }
        assertEquals("both walls meeting at the corner moved together", 2, joined);
        assertEquals(1, fp.rooms.size());
    }

    @Test public void emptyAreaDragIsNotConsumedSoTheViewPans() {
        drawRoom();
        c.setTool(ToolType.SELECT);
        assertFalse(c.onDragStart(sx(20000), sy(20000)));
    }

    @Test public void unselectedRoomFillDragPansInsteadOfMovingTheRoom() {
        drawRoom();
        c.setTool(ToolType.SELECT);
        assertFalse(c.onDragStart(sx(2500), sy(2000)));
    }

    @Test public void lockedVersionRefusesEdits() {
        drawRoom();
        c.setReadOnly(true);
        assertTrue(c.isReadOnly());
        c.setTool(ToolType.WALL);
        assertEquals(ToolType.SELECT, c.state().tool);
        int walls = c.plan().walls.size();
        c.selection().set(c.plan().walls.get(0).id);
        c.deleteSelection();
        assertEquals(walls, c.plan().walls.size());
        assertFalse(c.undo());
        assertFalse(c.canUndo());
        assertFalse(c.onDragStart(sx(5000), sy(2000)));
        assertTrue(messages.get(messages.size() - 1).contains("locked"));
    }

    @Test public void duplicateCopiesWallsWithTheirOpenings() {
        drawRoom();
        c.setTool(ToolType.DOOR);
        tap(2500, 5);
        c.setTool(ToolType.SELECT);
        for (Wall w : c.plan().walls) c.selection().add(w.id);
        c.duplicateSelection();
        assertEquals(8, c.plan().walls.size());
        assertEquals(2, c.plan().doors.size());
        assertEquals(2, c.plan().rooms.size());
        for (Door d : c.plan().doors) assertTrue(d.attached);
        c.undo();
        assertEquals(4, c.plan().walls.size());
    }

    @Test public void rotatingTheSelectionKeepsTheRoomClosed() {
        drawRoom();
        c.setTool(ToolType.SELECT);
        for (Wall w : c.plan().walls) c.selection().add(w.id);
        c.rotateSelection(90);
        assertEquals(1, c.plan().rooms.size());
        assertEquals(4.8 * 3.8, c.plan().rooms.get(0).areaM2, 1e-6);
        assertEquals(4000, c.plan().rooms.get(0).widthM * 1000 + 200, 1e-3);   // 3.8 m + 2*0.1
    }

    @Test public void floorsAreUndoableAndElementsStayOnTheirFloor() {
        drawRoom();
        Floor f1 = c.addFloor();
        assertNotNull(f1);
        assertEquals(1, f1.level);
        assertEquals(f1.id, c.activeFloorId());
        assertEquals(0, c.plan().walls.size());
        c.setTool(ToolType.WALL);
        tap(0, 0); tap(3000, 0);
        assertEquals(1, c.plan().walls.size());
        c.setActiveFloor("floor0");
        assertEquals(4, c.plan().walls.size());
        assertEquals(2, doc.floors().size());
    }

    @Test public void roomToolAsksUiToEditTheTappedRoom() {
        drawRoom();
        c.setTool(ToolType.ROOM);
        tap(2500, 2000);
        assertTrue(messages.get(messages.size() - 1).startsWith("room:"));
        tap(9000, 9000);
        assertTrue(messages.get(messages.size() - 1).contains("closed room"));
    }

    @Test public void snapGoesToEndpointsFirstThenGrid() {
        drawRoom();
        c.setTool(ToolType.WALL);
        // 40 mm away from the corner (=4 px at this zoom) snaps to the exact corner
        SnapResult r = c.snap(new com.buildvisionai.models.Vec2(5040, 30), null, null);
        assertEquals(SnapKind.ENDPOINT, r.kind);
        assertEquals(5000, r.point.x, 1e-9);
        assertEquals(0, r.point.y, 1e-9);
        SnapResult g = c.snap(new com.buildvisionai.models.Vec2(8010, 7020), null, null);
        assertEquals(SnapKind.GRID, g.kind);
        assertEquals(0, g.point.x % 250, 1e-9);
    }

    @Test public void dragDrawnWallCreatesOneWallAndStopsTheChain() {
        c.setTool(ToolType.WALL);
        drag(0, 0, 3000, 0);
        assertEquals(1, c.plan().walls.size());
        assertEquals(3000, c.plan().walls.get(0).lengthMm(), 1e-6);
        c.undo();
        assertEquals(0, c.plan().walls.size());
        assertNull(c.undoLabel());
        List<PlanElement> none = new ArrayList<>(c.plan().walls);
        assertTrue(none.isEmpty());
    }
}
