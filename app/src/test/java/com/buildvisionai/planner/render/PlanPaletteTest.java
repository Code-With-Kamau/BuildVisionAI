package com.buildvisionai.planner.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlanPaletteTest {
    private static void check(String name, PlanPalette p) {
        double text = 4.5, graphic = 3.0;
        assertTrue(name + " wall", PlanPalette.contrast(p.wall, p.background) >= 7.0);
        assertTrue(name + " label text", PlanPalette.contrast(p.labelText, p.background) >= text);
        assertTrue(name + " dimension text", PlanPalette.contrast(p.dimensionText, p.background) >= text);
        assertTrue(name + " scale bar", PlanPalette.contrast(p.scaleBar, p.background) >= text);
        assertTrue(name + " selection", PlanPalette.contrast(p.selection, p.background) >= graphic);
        assertTrue(name + " snap marker", PlanPalette.contrast(p.snapMarker, p.background) >= graphic);
        assertTrue(name + " door leaf", PlanPalette.contrast(p.doorLeaf, p.background) >= graphic);
        assertTrue(name + " furniture outline", PlanPalette.contrast(p.furnitureStroke, p.background) >= graphic);
        assertTrue(name + " handle outline vs handle fill", PlanPalette.contrast(p.selection, p.handleFill) >= graphic);
        // the grid is a quiet backdrop: visible, but clearly weaker than anything drawn on it
        double minor = PlanPalette.contrast(p.gridMinor, p.background), major = PlanPalette.contrast(p.gridMajor, p.background);
        assertTrue(name + " grid visible", minor > 1.02);
        assertTrue(name + " major stronger than minor", major > minor);
        assertTrue(name + " grid quieter than walls", major < PlanPalette.contrast(p.wall, p.background) / 3);
        assertTrue(name + " grid quieter than furniture", major < PlanPalette.contrast(p.furnitureStroke, p.background));
    }

    @Test public void lightPaletteMeetsContrastTargets() { check("light", PlanPalette.light()); }

    @Test public void darkPaletteMeetsContrastTargets() { check("dark", PlanPalette.dark()); }

    @Test public void contrastMathMatchesKnownValues() {
        assertEquals(21.0, PlanPalette.contrast(0xFF000000, 0xFFFFFFFF), 1e-9);
        assertEquals(1.0, PlanPalette.contrast(0xFF808080, 0xFF808080), 1e-9);
        assertEquals(4.54, PlanPalette.contrast(0xFF767676, 0xFFFFFFFF), 0.01);     // the well-known lightest grey that passes AA on white
    }
}
