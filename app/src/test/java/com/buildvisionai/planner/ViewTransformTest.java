package com.buildvisionai.planner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.models.RectD;

import org.junit.Test;

public class ViewTransformTest {
    private static ViewTransform framed(int w, int h) {
        ViewTransform v = new ViewTransform();
        v.setViewSize(w, h);
        v.fit(new RectD(0, 0, 10000, 8000), 48);
        return v;
    }

    @Test public void fitMarksTheTransformAsFramedOnlyWhenItWasApplied() {
        ViewTransform v = new ViewTransform();
        v.fit(new RectD(0, 0, 10000, 8000), 48);        // no size yet: nothing happens
        assertFalse(v.fitted);
        v.setViewSize(1080, 1800);
        v.fit(new RectD(0, 0, 10000, 8000), 48);
        assertTrue(v.fitted);
    }

    @Test public void resizeKeepsThePointAtTheMiddleOfTheViewInTheMiddle() {
        ViewTransform v = framed(1080, 1800);
        v.zoomAbout(200, 300, 2.5);                      // the person zoomed in somewhere off-centre
        double wx = v.toWorldX(1080 / 2.0), wy = v.toWorldY(1800 / 2.0);
        double scale = v.scale;
        v.resize(1800, 1080);                            // rotated to landscape
        assertEquals(wx, v.toWorldX(1800 / 2.0), 1e-6);
        assertEquals(wy, v.toWorldY(1080 / 2.0), 1e-6);
        assertEquals(scale, v.scale, 0.0);               // zoom level untouched
    }

    @Test public void resizeBeforeTheFirstFitOnlyRecordsTheSize() {
        ViewTransform v = new ViewTransform();
        v.tx = 5;
        v.ty = 7;
        v.resize(400, 300);
        assertEquals(5, v.tx, 0);
        assertEquals(7, v.ty, 0);
        assertEquals(400, v.viewW);
        assertEquals(300, v.viewH);
    }

    @Test public void copyKeepsTheFramedFlag() {
        assertTrue(framed(800, 600).copy().fitted);
    }
}
