package com.buildvisionai.collaboration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.models.Room;
import com.buildvisionai.models.RoomType;
import com.buildvisionai.models.SuggestionSource;
import com.buildvisionai.planner.PlanDocument;

import java.util.List;
import org.junit.Test;

public class ProposalDescriberTest {
    @Test public void everyOpTypeHasAReadableLine() {
        ChangeProposal p = new ChangeProposal("all", "r", SuggestionSource.AI);
        for (ProposalOp.Type t : ProposalOp.Type.values()) p.add(new ProposalOp(t, "x", "BASIC", 150, 1200));
        List<String> lines = ProposalDescriber.lines(p, null);
        assertEquals(ProposalOp.Type.values().length, lines.size());
        for (String l : lines) assertTrue(l, l.length() > 5 && !l.contains("null"));
    }

    @Test public void lengthsAreFormattedAndLabelsReplaceEnumNames() {
        ChangeProposal p = new ChangeProposal("t", "r", SuggestionSource.AI);
        p.add(ProposalOp.plan(ProposalOp.Type.SET_WALL_THICKNESS, "w", null, 150));
        p.add(ProposalOp.plan(ProposalOp.Type.SET_WALL_HEIGHT, "w", null, 2700));
        p.add(ProposalOp.setting(ProposalOp.Type.SET_FINISH_LEVEL, "BASIC", 0));
        p.add(ProposalOp.setting(ProposalOp.Type.SET_ROOF_TYPE, "GABLED_IRON_SHEET", 0));
        List<String> l = ProposalDescriber.lines(p, null);
        assertEquals("Wall: thickness 150 mm", l.get(0));
        assertEquals("Wall: height 2.70 m", l.get(1));
        assertEquals("Finish level: Basic", l.get(2));
        assertEquals("Roof: Gabled - iron sheets (G28)", l.get(3));
    }

    @Test public void roomsAreNamedFromThePlanAndMissingElementsAreFlagged() {
        PlanDocument doc = new PlanDocument();
        doc.put(new Room("r1", "f1", "Master Bedroom", RoomType.BEDROOM, 0, 0));
        ChangeProposal p = new ChangeProposal("t", "r", SuggestionSource.PROFESSIONAL);
        p.add(ProposalOp.plan(ProposalOp.Type.RENAME_ROOM, "r1", "Study", 0));
        p.add(ProposalOp.plan(ProposalOp.Type.SET_ROOM_TYPE, "gone", "OFFICE", 0));
        List<String> l = ProposalDescriber.lines(p, doc);
        assertEquals("Room \"Master Bedroom\": rename to \"Study\"", l.get(0));
        assertTrue(l.get(1), l.get(1).contains("no longer in the plan"));
    }

    @Test public void badInputNeverThrows() {
        ChangeProposal p = new ChangeProposal("t", "r", SuggestionSource.AI);
        p.add(ProposalOp.setting(ProposalOp.Type.SET_FINISH_LEVEL, "LAVISH", 0));
        p.add(ProposalOp.setting(ProposalOp.Type.SET_ROOF_TYPE, null, 0));
        p.ops.add(null);
        p.ops.add(new ProposalOp());
        List<String> l = ProposalDescriber.lines(p, null);
        assertEquals(2, l.size());
        assertEquals("Finish level: LAVISH", l.get(0));
        assertTrue(ProposalDescriber.lines(null, null).isEmpty());
    }

    @Test public void costEffectShowsSignAndIgnoresMissingEstimates() {
        ChangeProposal p = new ChangeProposal("t", "r", SuggestionSource.AI);
        assertNull(ProposalDescriber.effect(p));
        p.estimatedDeltaKes = -120_000;
        assertEquals("Estimated cost change: -KES 120,000", ProposalDescriber.effect(p));
        p.estimatedDeltaKes = 50_000;
        assertEquals("Estimated cost change: +KES 50,000", ProposalDescriber.effect(p));
        p.estimatedDeltaKes = Double.POSITIVE_INFINITY;
        assertNull(ProposalDescriber.effect(p));
    }
}
