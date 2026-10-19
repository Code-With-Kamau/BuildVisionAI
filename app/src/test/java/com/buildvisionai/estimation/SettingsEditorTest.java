package com.buildvisionai.estimation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SettingsEditorTest {
    @Test public void acceptsPlainNumbersPercentSignsAndWhitespace() {
        assertNull(SettingsEditor.validate(SettingsEditor.Field.CONTINGENCY, "7.5"));
        assertNull(SettingsEditor.validate(SettingsEditor.Field.CONTINGENCY, " 7.5 % "));
        assertNull(SettingsEditor.validate(SettingsEditor.Field.WATER_TANK, "10000"));
    }

    @Test public void rejectsGarbageOutOfRangeAndNonFiniteValues() {
        assertEquals("Enter a number.", SettingsEditor.validate(SettingsEditor.Field.ROOF_PITCH, "steep"));
        assertEquals("Enter a number.", SettingsEditor.validate(SettingsEditor.Field.ROOF_PITCH, ""));
        assertEquals("Enter a number.", SettingsEditor.validate(SettingsEditor.Field.ROOF_PITCH, "NaN"));
        assertEquals("Enter a number.", SettingsEditor.validate(SettingsEditor.Field.ROOF_PITCH, "Infinity"));
        assertEquals("Enter between 5 and 45 degrees.", SettingsEditor.validate(SettingsEditor.Field.ROOF_PITCH, "60"));
        assertEquals("Enter between 0 and 30 %.", SettingsEditor.validate(SettingsEditor.Field.CONTINGENCY, "-1"));
        assertNotNull(SettingsEditor.validate(SettingsEditor.Field.WATER_TANK, "5000.5"));
        assertNotNull(SettingsEditor.validate(SettingsEditor.Field.WATER_TANK, "50001"));
    }

    @Test public void applyWritesTheRightFieldAndIgnoresInvalidInput() {
        EstimateSettings s = new EstimateSettings();
        SettingsEditor.apply(s, SettingsEditor.Field.ROOF_PITCH, "25");
        SettingsEditor.apply(s, SettingsEditor.Field.CONTINGENCY, "10%");
        SettingsEditor.apply(s, SettingsEditor.Field.PRELIMINARIES, "6");
        SettingsEditor.apply(s, SettingsEditor.Field.OVERHEAD_PROFIT, "15");
        SettingsEditor.apply(s, SettingsEditor.Field.PROFESSIONAL_FEES, "4.5");
        SettingsEditor.apply(s, SettingsEditor.Field.WATER_TANK, "10000");
        assertEquals(25, s.roofPitchDeg, 0);
        assertEquals(10, s.contingencyPct, 0);
        assertEquals(6, s.preliminariesPct, 0);
        assertEquals(15, s.overheadProfitPct, 0);
        assertEquals(4.5, s.professionalFeesPct, 0);
        assertEquals(10000, s.waterTankLitres);
        SettingsEditor.apply(s, SettingsEditor.Field.ROOF_PITCH, "999");
        SettingsEditor.apply(s, SettingsEditor.Field.CONTINGENCY, "abc");
        assertEquals(25, s.roofPitchDeg, 0);
        assertEquals(10, s.contingencyPct, 0);
    }

    @Test public void formatRoundTripsThroughValidate() {
        EstimateSettings s = new EstimateSettings();
        for (SettingsEditor.Field f : SettingsEditor.Field.values()) {
            String shown = SettingsEditor.format(s, f);
            assertNull(f + " shows " + shown, SettingsEditor.validate(f, shown));
        }
        assertEquals("5", SettingsEditor.format(s, SettingsEditor.Field.CONTINGENCY));
        s.professionalFeesPct = 4.5;
        assertEquals("4.5", SettingsEditor.format(s, SettingsEditor.Field.PROFESSIONAL_FEES));
        assertTrue(SettingsEditor.format(s, SettingsEditor.Field.WATER_TANK).matches("\\d+"));
    }
}
