package com.buildvisionai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.planner.render.PlanPalette;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Files;
import org.junit.Test;

/**
 * Reads the real colour resources and checks the contrast of every pair the theme and the cost
 * chart use, in light and dark: 4.5:1 for text, 3:1 for graphical marks (WCAG 2.x 1.4.3 / 1.4.11).
 * A palette edit that breaks readability fails here instead of in a user's hands.
 */
public class ThemeContrastTest {
    private static File resDir() {
        String prop = System.getProperty("bv.res.dir");
        File[] candidates = {
                prop == null ? null : new File(prop),
                new File("src/main/res"), new File("app/src/main/res")};
        for (File f : candidates) if (f != null && new File(f, "values/colors.xml").isFile()) return f;
        throw new AssertionError("res directory not found; run from app/ or pass -Dbv.res.dir");
    }

    private static Map<String, Integer> colors(String folder) throws Exception {
        String xml = new String(Files.readAllBytes(new File(resDir(), folder + "/colors.xml").toPath()), "UTF-8");
        Map<String, Integer> m = new HashMap<>();
        Matcher mt = Pattern.compile("<color name=\"([a-z0-9_]+)\">#([0-9A-Fa-f]{6})</color>").matcher(xml);
        while (mt.find()) m.put(mt.group(1), 0xFF000000 | Integer.parseInt(mt.group(2), 16));
        return m;
    }

    private static void atLeast(String set, Map<String, Integer> c, String fg, String bg, double min) {
        Integer f = c.get(fg), b = c.get(bg);
        assertNotNull(set + ": missing " + fg, f);
        assertNotNull(set + ": missing " + bg, b);
        double ratio = PlanPalette.contrast(f, b);
        assertTrue(String.format("%s: %s on %s is %.2f:1, needs %.1f", set, fg, bg, ratio, min), ratio >= min);
    }

    private static void checkSet(String folder) throws Exception {
        Map<String, Integer> c = colors(folder);
        String[][] text = {
                {"bv_on_surface", "bv_surface"}, {"bv_on_surface", "bv_card"},
                {"bv_on_surface_variant", "bv_surface"}, {"bv_on_surface_variant", "bv_card"},
                {"bv_primary", "bv_surface"}, {"bv_primary", "bv_card"},
                {"bv_on_primary", "bv_primary"},
                {"bv_on_primary_container", "bv_primary_container"},
                {"bv_on_secondary", "bv_secondary"},
                {"bv_on_secondary_container", "bv_secondary_container"},
                {"bv_on_error", "bv_error"}, {"bv_error", "bv_surface"}, {"bv_error", "bv_card"},
                {"bv_on_surface", "bv_surface_variant"}, {"bv_on_surface_variant", "bv_surface_variant"},
        };
        for (String[] p : text) atLeast(folder, c, p[0], p[1], 4.5);
        atLeast(folder, c, "bv_outline", "bv_surface", 3.0);
        atLeast(folder, c, "bv_outline", "bv_card", 3.0);
        for (int i = 1; i <= 7; i++) {
            atLeast(folder, c, "bv_chart_" + i, "bv_surface", 3.0);
            atLeast(folder, c, "bv_chart_" + i, "bv_card", 3.0);
        }
        atLeast(folder, c, "bv_icon_foreground", "bv_icon_background", 4.5);
    }

    @Test public void lightColoursAreReadable() throws Exception { checkSet("values"); }

    @Test public void darkColoursAreReadable() throws Exception { checkSet("values-night"); }

    @Test public void lightAndDarkDefineTheSameNames() throws Exception {
        assertEquals(colors("values").keySet(), colors("values-night").keySet());
    }

    @Test public void chartColoursAreDistinctFromEachOther() throws Exception {
        for (String folder : new String[]{"values", "values-night"}) {
            Map<String, Integer> c = colors(folder);
            for (int i = 1; i <= 7; i++) {
                for (int j = i + 1; j <= 7; j++) {
                    assertTrue(folder + " chart " + i + " vs " + j, !c.get("bv_chart_" + i).equals(c.get("bv_chart_" + j)));
                }
            }
        }
    }
}
