package com.buildvisionai.planner.props;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.models.Column;
import com.buildvisionai.models.ColumnShape;
import com.buildvisionai.models.Door;
import com.buildvisionai.models.DoorType;
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
import com.buildvisionai.planner.EditorController;
import com.buildvisionai.planner.PlanDocument;
import com.buildvisionai.models.Floor;
import com.buildvisionai.planner.props.ElementFields.Field;
import com.buildvisionai.planner.props.ElementFields.Kind;
import com.buildvisionai.utils.UnitSystem;

import org.junit.Test;

import java.util.List;

public class ElementFieldsTest {
    static final UnitSystem M = UnitSystem.METRIC;

    static Field field(List<Field> fs, String key) {
        for (Field f : fs) if (f.key.equals(key)) return f;
        throw new AssertionError("no field " + key + " in " + fs.size() + " fields");
    }

    @Test public void everyElementTypeOffersItsOwnFields() {
        PlanElement[] all = {
                new Wall("w", "g", 0, 0, 4000, 0, WallKind.EXTERNAL), new Door("d", "g", "w", 1000, DoorType.SINGLE),
                new Window("x", "g", "w", 2000, WindowType.SLIDING), new Column("c", "g", 0, 0, ColumnShape.SQUARE),
                new Stair("s", "g", 0, 0, StairType.STRAIGHT), new Furniture("f", "g", 0, 0, FurnitureType.BED),
                new Room("r", "g", "Room 1", RoomType.BEDROOM, 0, 0)};
        int[] expected = {4, 5, 4, 5, 6, 4, 2};
        for (int i = 0; i < all.length; i++) {
            List<Field> fs = ElementFields.of(all[i], M);
            assertEquals(all[i].getClass().getSimpleName(), expected[i], fs.size());
            for (Field f : fs) {
                assertNotNull(f.label);
                if (f.kind == Kind.CHOICE) {
                    assertEquals(f.choiceIds.size(), f.choiceLabels.size());
                    assertEquals("selected points at the current value", f.value, f.choiceLabels.get(f.selected));
                }
            }
        }
    }

    @Test public void wallFieldsShowCurrentValuesInTheChosenUnit() {
        Wall w = new Wall("w", "g", 0, 0, 4000, 0, WallKind.EXTERNAL);
        w.thicknessMm = 200;
        assertEquals(WallKind.EXTERNAL.label, field(ElementFields.of(w, M), "kind").value);
        String metric = field(ElementFields.of(w, M), "thickness").value;
        String imperial = field(ElementFields.of(w, UnitSystem.FEET_INCHES), "thickness").value;
        assertTrue(metric, metric.contains("200") || metric.contains("0.2"));
        assertTrue(imperial, imperial.contains("\"") || imperial.contains("in"));
    }

    @Test public void lengthLimitsAreEnforcedWithAHelpfulMessage() {
        Wall w = new Wall("w", "g", 0, 0, 4000, 0, WallKind.EXTERNAL);
        assertNull(ElementFields.validate(w, "thickness", "230", M));
        assertNull(ElementFields.validate(w, "thickness", "0.23 m", M));       // other units are understood
        assertNull(ElementFields.validate(w, "thickness", "23 cm", M));
        String tooThin = ElementFields.validate(w, "thickness", "10", M);
        assertNotNull(tooThin);
        assertTrue(tooThin, tooThin.startsWith("Enter between"));
        assertNotNull(ElementFields.validate(w, "thickness", "1000", M));
        assertNotNull(ElementFields.validate(w, "thickness", "thick", M));
        assertNotNull(ElementFields.validate(w, "thickness", "", M));
        assertNotNull(ElementFields.validate(w, "height", "500", M));
        // feet and inches
        assertNull(ElementFields.validate(w, "height", "9'", UnitSystem.FEET_INCHES));
    }

    @Test public void doorAndWindowLimitsDifferByElement() {
        Door d = new Door("d", "g", "w", 1000, DoorType.SINGLE);
        Window x = new Window("x", "g", "w", 2000, WindowType.SLIDING);
        assertNull(ElementFields.validate(d, "width", "900", M));
        assertNotNull(ElementFields.validate(d, "width", "300", M));
        assertNull(ElementFields.validate(x, "width", "400", M));      // fine for a window, too small for a door
        assertNotNull(ElementFields.validate(x, "sill", "2500", M));
        assertNull(ElementFields.validate(x, "sill", "0", M));
    }

    @Test public void roomNameAndRotationAreValidated() {
        Room r = new Room("r", "g", "Room 1", RoomType.BEDROOM, 0, 0);
        assertNull(ElementFields.validate(r, "name", "Study", M));
        assertNotNull(ElementFields.validate(r, "name", "   ", M));
        assertNotNull(ElementFields.validate(r, "name", "x".repeat(41), M));
        Furniture f = new Furniture("f", "g", 0, 0, FurnitureType.BED);
        assertNull(ElementFields.validate(f, "rotation", "45.5", M));
        assertNotNull(ElementFields.validate(f, "rotation", "abc", M));
        assertNotNull(ElementFields.validate(f, "rotation", "400", M));
    }

    @Test public void applyChangesTheCopyAndNormalisesRotation() {
        Furniture f = new Furniture("f", "g", 0, 0, FurnitureType.BED);
        ElementFields.apply(f, "rotation", "-90", M);
        assertEquals(270, f.rotationDeg, 1e-9);
        ElementFields.apply(f, "width", "1.8 m", M);
        assertEquals(1800, f.widthMm, 1e-9);
        ElementFields.apply(f, "width", "garbage", M);
        assertEquals("unparsable values change nothing", 1800, f.widthMm, 1e-9);
        ElementFields.apply(f, "nonsense", "1", M);
    }

    @Test public void changingWallTypeKeepsACustomThickness() {
        Wall w = new Wall("w", "g", 0, 0, 4000, 0, WallKind.EXTERNAL);
        ElementFields.apply(w, "kind", "PARTITION", M);
        assertEquals(WallKind.PARTITION, w.kind);
        assertEquals("defaults follow the type", WallKind.PARTITION.defaultMaterial, w.material);
        assertEquals(WallKind.PARTITION.defaultThicknessMm(), w.thicknessMm, 0);

        Wall custom = new Wall("w2", "g", 0, 0, 4000, 0, WallKind.EXTERNAL);
        custom.thicknessMm = 250;
        ElementFields.apply(custom, "kind", "INTERNAL", M);
        assertEquals("a thickness the user chose is kept", 250, custom.thicknessMm, 0);
        assertEquals(WallKind.INTERNAL, custom.kind);
    }

    @Test public void changingMaterialMovesThicknessUnlessCustomised() {
        Wall w = new Wall("w", "g", 0, 0, 4000, 0, WallKind.INTERNAL);
        ElementFields.apply(w, "material", WallMaterial.STONE_225.name(), M);
        assertEquals(WallMaterial.STONE_225.thicknessMm, w.thicknessMm, 0);
        w.thicknessMm = 260;
        ElementFields.apply(w, "material", WallMaterial.BLOCK_150.name(), M);
        assertEquals(260, w.thicknessMm, 0);
    }

    @Test public void doorTypeFollowsDefaultWidthButNotACustomOne() {
        Door d = new Door("d", "g", "w", 1000, DoorType.SINGLE);
        ElementFields.apply(d, "doorType", "DOUBLE", M);
        assertEquals(DoorType.DOUBLE.defaultWidthMm, d.widthMm, 0);
        d.widthMm = 1300;
        ElementFields.apply(d, "doorType", "SLIDING", M);
        assertEquals(1300, d.widthMm, 0);
    }

    @Test public void flipsCycleThroughAllFourSwings() {
        Door d = new Door("d", "g", "w", 1000, DoorType.SINGLE);
        d.swing = SwingDirection.LEFT_IN;
        ElementFields.apply(d, "flipHinge", "", M);
        assertEquals(SwingDirection.RIGHT_IN, d.swing);
        ElementFields.apply(d, "flipSide", "", M);
        assertEquals(SwingDirection.RIGHT_OUT, d.swing);
        ElementFields.apply(d, "flipHinge", "", M);
        ElementFields.apply(d, "flipSide", "", M);
        assertEquals(SwingDirection.LEFT_IN, d.swing);
    }

    @Test public void furnitureTypeFollowsDefaultSizeButNotACustomOne() {
        Furniture f = new Furniture("f", "g", 0, 0, FurnitureType.BED);
        ElementFields.apply(f, "furnitureType", "SOFA", M);
        assertEquals(FurnitureType.SOFA.defaultWidthMm, f.widthMm, 0);
        assertEquals(FurnitureType.SOFA.defaultDepthMm, f.depthMm, 0);
        f.widthMm = 2500;
        ElementFields.apply(f, "furnitureType", "DESK", M);
        assertEquals(2500, f.widthMm, 0);
    }

    @Test public void appliedThroughTheEditorItIsOneUndoStepAndTheLiveElementIsNotMutatedFirst() {
        PlanDocument doc = new PlanDocument();
        doc.putFloor(new Floor("g", 0, "Ground"));
        Wall w = new Wall("w1", "g", 0, 0, 4000, 0, WallKind.EXTERNAL);
        doc.put(w);
        EditorController c = new EditorController(doc, "g");
        String err = ElementFields.validate(w, "thickness", "230", M);
        assertNull(err);
        c.updateElement("w1", "Change thickness", copy -> ElementFields.apply(copy, "thickness", "230", M));
        assertEquals(230, ((Wall) c.doc().get("w1")).thicknessMm, 0);
        assertTrue(c.canUndo());
        c.undo();
        assertEquals(WallKind.EXTERNAL.defaultThicknessMm(), ((Wall) c.doc().get("w1")).thicknessMm, 0);
    }
}
