package com.buildvisionai.planner.props;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.models.DoorType;
import com.buildvisionai.models.WallKind;
import com.buildvisionai.planner.EditorState;
import com.buildvisionai.planner.ToolType;

import java.util.List;
import org.junit.Test;

public class ToolOptionsTest {
    @Test public void everyDrawingToolHasOptionsWithExactlyOneSelected() {
        EditorState s = new EditorState();
        for (ToolType t : ToolType.values()) {
            List<ToolOptions.Option> opts = ToolOptions.of(t, s);
            assertEquals(t.name(), ToolOptions.hasOptions(t), !opts.isEmpty());
            if (opts.isEmpty()) continue;
            int selected = 0;
            for (ToolOptions.Option o : opts) if (o.selected) selected++;
            assertEquals(t.name(), 1, selected);
        }
        assertFalse(ToolOptions.hasOptions(ToolType.SELECT));
        assertFalse(ToolOptions.hasOptions(ToolType.ROOM));
    }

    @Test public void choosingAnOptionUpdatesTheEditorStateAndTheNextListShowsIt() {
        EditorState s = new EditorState();
        assertTrue(ToolOptions.select(ToolType.WALL, s, WallKind.INTERNAL.name()));
        assertEquals(WallKind.INTERNAL, s.wallKind);
        boolean found = false;
        for (ToolOptions.Option o : ToolOptions.of(ToolType.WALL, s)) if (o.selected) found = o.id.equals("INTERNAL");
        assertTrue(found);
        assertTrue(ToolOptions.select(ToolType.DOOR, s, DoorType.DOUBLE.name()));
        assertEquals(DoorType.DOUBLE, s.doorType);
    }

    @Test public void unknownIdsAndOptionlessToolsChangeNothing() {
        EditorState s = new EditorState();
        WallKind before = s.wallKind;
        assertFalse(ToolOptions.select(ToolType.WALL, s, "NOT_A_KIND"));
        assertFalse(ToolOptions.select(ToolType.WALL, s, null));
        assertFalse(ToolOptions.select(ToolType.SELECT, s, "INTERNAL"));
        assertEquals(before, s.wallKind);
    }
}
