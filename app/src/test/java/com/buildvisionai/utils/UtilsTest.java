package com.buildvisionai.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.models.RectD;
import com.buildvisionai.planner.ScaleModel;
import com.buildvisionai.planner.ViewTransform;

import org.junit.Test;

public class UtilsTest {
    @Test public void lengthFormatting() {
        assertEquals("3.50 m", LengthFormatter.metric(3500));
        assertEquals("450 mm", LengthFormatter.metric(450));
        assertEquals("11'-6\"", LengthFormatter.feetInches(3505));       // 11.5 ft
        assertEquals("6\"", LengthFormatter.feetInches(150));
        assertEquals("50 ft is 15.24 m", "50 ft is " + String.format("%.2f", 50 * LengthFormatter.MM_PER_FOOT / 1000) + " m");
    }

    @Test public void lengthParsing() {
        assertEquals(3500, LengthFormatter.parseMm("3.5", UnitSystem.METRIC), 1e-9);
        assertEquals(3500, LengthFormatter.parseMm("3.5m", UnitSystem.METRIC), 1e-9);
        assertEquals(3500, LengthFormatter.parseMm("3500 mm", UnitSystem.METRIC), 1e-9);
        assertEquals(3500, LengthFormatter.parseMm("350cm", UnitSystem.METRIC), 1e-9);
        assertEquals(11 * 304.8 + 6 * 25.4, LengthFormatter.parseMm("11'6\"", UnitSystem.FEET_INCHES), 1e-9);
        assertEquals(11 * 304.8 + 6 * 25.4, LengthFormatter.parseMm("11ft 6in", UnitSystem.METRIC), 1e-9);
        assertEquals(12 * 304.8, LengthFormatter.parseMm("12'", UnitSystem.METRIC), 1e-9);
        assertEquals(50 * 304.8, LengthFormatter.parseMm("50", UnitSystem.FEET_INCHES), 1e-9);
        assertTrue(Double.isNaN(LengthFormatter.parseMm("abc", UnitSystem.METRIC)));
        assertTrue(Double.isNaN(LengthFormatter.parseMm("", UnitSystem.METRIC)));
        assertTrue(Double.isNaN(LengthFormatter.parseMm(null, UnitSystem.METRIC)));
    }

    @Test public void moneyFormattingAndParsing() {
        assertEquals("KES 1,234,568", MoneyFormatter.kes(1234567.6));
        assertEquals("KES 5.06M", MoneyFormatter.compact(5_063_449));
        assertEquals("KES 450K", MoneyFormatter.compact(450_000));
        assertEquals("KES 980", MoneyFormatter.compact(980));
        assertEquals(5_000_000, MoneyFormatter.parse("5,000,000"), 0);
        assertEquals(5_500_000, MoneyFormatter.parse("5.5M"), 0);
        assertEquals(750_000, MoneyFormatter.parse("KES 750k"), 0);
        assertTrue(Double.isNaN(MoneyFormatter.parse("lots")));
    }

    @Test public void plotPresetsConvertFeetToMetres() {
        PlotPresets.Preset p = PlotPresets.all().get(1);
        assertEquals(15.24, p.widthM, 1e-9);
        assertEquals(30.48, p.lengthM, 1e-9);
        assertEquals(464.5, p.areaM2(), 0.1);
    }

    @Test public void zoomKeepsThePointUnderTheFingerFixed() {
        ViewTransform v = new ViewTransform();
        v.setViewSize(1000, 2000);
        v.scale = 0.1; v.tx = 50; v.ty = 80;
        double wx = v.toWorldX(400), wy = v.toWorldY(700);
        v.zoomAbout(400, 700, 2.5);
        assertEquals(0.25, v.scale, 1e-12);
        assertEquals(wx, v.toWorldX(400), 1e-9);
        assertEquals(wy, v.toWorldY(700), 1e-9);
        v.zoomAbout(400, 700, 1e9);
        assertEquals(ViewTransform.MAX_SCALE, v.scale, 0);
        v.zoomAbout(400, 700, 1e-12);
        assertEquals(ViewTransform.MIN_SCALE, v.scale, 0);
    }

    @Test public void fitCentresTheContent() {
        ViewTransform v = new ViewTransform();
        v.setViewSize(1000, 1000);
        v.fit(new RectD(0, 0, 10000, 5000), 50);
        assertEquals(0.09, v.scale, 1e-9);     // (1000 - 100) / 10000
        assertEquals(500, v.toScreenX(5000), 1e-9);
        assertEquals(500, v.toScreenY(2500), 1e-9);
    }

    @Test public void drawingScalesAndAdaptiveGrid() {
        double dpi = 420;
        double px = ScaleModel.pxPerMm(dpi, 50);
        assertEquals(420 / 25.4 / 50, px, 1e-12);
        assertEquals(50, ScaleModel.nearestRatio(px * 1.05, dpi));
        assertEquals(100, ScaleModel.nearestRatio(ScaleModel.pxPerMm(dpi, 100) * 0.9, dpi));
        // grid spacing on screen is never below the minimum and steps through nice values
        double last = 0;
        for (double scale = 0.5; scale > 0.004; scale *= 0.8) {
            double step = ScaleModel.gridStepMm(scale, 24);
            assertTrue(step * scale >= 24 - 1e-9);
            assertTrue(step >= last - 1e-9);
            last = step;
        }
        assertEquals(1000, ScaleModel.majorStepMm(250), 0);
        assertEquals(1000, ScaleModel.majorStepMm(100) , 1000);   // a multiple of 100 that is >= 400
        assertTrue(ScaleModel.majorStepMm(100) % 100 == 0 && ScaleModel.majorStepMm(100) >= 400);
        double bar = ScaleModel.scaleBarMm(0.1, 100);
        assertEquals(1000, bar, 0);
        assertEquals(2000, ScaleModel.scaleBarMm(0.1, 250), 0);
        assertEquals("1:50", ScaleModel.label(50));
    }
}
