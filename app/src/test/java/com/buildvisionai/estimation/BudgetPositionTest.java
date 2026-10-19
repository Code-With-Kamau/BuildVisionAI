package com.buildvisionai.estimation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BudgetPositionTest {
    @Test public void noBudgetOrNoCostIsNotSet() {
        assertEquals(BudgetPosition.Kind.NOT_SET, BudgetPosition.of(0, 1, 2, 3).kind);
        assertEquals(BudgetPosition.Kind.NOT_SET, BudgetPosition.of(-5, 1, 2, 3).kind);
        assertEquals(BudgetPosition.Kind.NOT_SET, BudgetPosition.of(5_000_000, 0, 0, 0).kind);
    }

    @Test public void fitsEvenAtHighPricesReportsTheSpareMoney() {
        BudgetPosition p = BudgetPosition.of(6_000_000, 4_400_000, 5_000_000, 5_800_000);
        assertEquals(BudgetPosition.Kind.WITHIN_EVEN_AT_HIGH, p.kind);
        assertEquals(200_000, p.amountKes, 1e-6);
    }

    @Test public void fitsAtTypicalButNotHighReportsBothNumbers() {
        BudgetPosition p = BudgetPosition.of(5_200_000, 4_400_000, 5_000_000, 5_800_000);
        assertEquals(BudgetPosition.Kind.WITHIN_AT_TYPICAL, p.kind);
        assertEquals(200_000, p.amountKes, 1e-6);
        assertEquals(600_000, p.overshootAtHighKes, 1e-6);
    }

    @Test public void overBudgetSaysWhetherTheLowestPricesWouldFit() {
        BudgetPosition near = BudgetPosition.of(4_700_000, 4_400_000, 5_000_000, 5_800_000);
        assertEquals(BudgetPosition.Kind.OVER, near.kind);
        assertEquals(300_000, near.amountKes, 1e-6);
        assertTrue(near.fitsAtLowestPrices);
        BudgetPosition far = BudgetPosition.of(3_000_000, 4_400_000, 5_000_000, 5_800_000);
        assertFalse(far.fitsAtLowestPrices);
    }

    @Test public void boundariesCountAsFitting() {
        assertEquals(BudgetPosition.Kind.WITHIN_EVEN_AT_HIGH, BudgetPosition.of(5_800_000, 4_400_000, 5_000_000, 5_800_000).kind);
        assertEquals(BudgetPosition.Kind.WITHIN_AT_TYPICAL, BudgetPosition.of(5_000_000, 4_400_000, 5_000_000, 5_800_000).kind);
    }
}
