package com.buildvisionai.estimation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RateInputTest {
    @Test public void aGoodQuoteIsAccepted() {
        RateInput.Result r = RateInput.parse("800", "950", "1,100");
        assertTrue(r.errors.toString(), r.ok());
        assertEquals(800, r.rate.min, 0);
        assertEquals(950, r.rate.avg, 0);
        assertEquals(1100, r.rate.max, 0);
    }

    @Test public void equalAmountsAreAllowedForAFixedPrice() {
        assertTrue(RateInput.parse("900", "900", "900").ok());
    }

    @Test public void eachBadAmountIsReportedSeparately() {
        RateInput.Result r = RateInput.parse("", "0", "abc");
        assertFalse(r.ok());
        assertEquals("[min, avg, max]", r.errors.keySet().toString());
        assertNull(r.rate);
    }

    @Test public void outOfOrderAmountsAreRejected() {
        assertFalse(RateInput.parse("1000", "900", "1100").ok());
        assertFalse(RateInput.parse("800", "1200", "1100").ok());
    }

    @Test public void hugeOrNonFiniteAmountsAreRejected() {
        assertFalse(RateInput.parse("1", "2", "999999999999").ok());
        assertFalse(RateInput.parse("1", "Infinity", "3").ok());
        assertFalse(RateInput.parse("-5", "2", "3").ok());
    }

    @Test public void formatRoundTrips() {
        String[] s = RateInput.format(new Rate(792, 900, 1035));
        RateInput.Result r = RateInput.parse(s[0], s[1], s[2]);
        assertTrue(r.ok());
        assertEquals(900, r.rate.avg, 0);
    }
}
