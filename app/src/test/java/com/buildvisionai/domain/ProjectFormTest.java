package com.buildvisionai.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.models.BuildingType;
import com.buildvisionai.models.Project;

import org.junit.Test;

public class ProjectFormTest {
    private static ProjectForm.Result v(String name, String owner, String loc, String county, String w, String l, String budget) {
        return ProjectForm.validate(name, owner, loc, county, w, l, budget, BuildingType.MAISONETTE);
    }

    @Test public void aTypicalKenyanFormIsAccepted() {
        ProjectForm.Result r = v("  Kamau Maisonette ", "Charles Kamau", "Ruiru", "kiambu", "50ft", "100 ft", "5,500,000");
        assertTrue(r.errors.toString(), r.ok());
        Project p = r.project;
        assertEquals("Kamau Maisonette", p.projectName);
        assertEquals("Kiambu", p.county);                 // canonical spelling
        assertEquals(15.24, p.plotWidthM, 1e-9);
        assertEquals(30.48, p.plotLengthM, 1e-9);
        assertEquals(5_500_000, p.budgetKes, 0);
        assertEquals(BuildingType.MAISONETTE, p.buildingType);
    }

    @Test public void metresFeetAndAbbreviatedBudgetsAreRead() {
        Project p = v("A", "B", "", "Nairobi", "15.2", "30 m", "5m").project;
        assertEquals(15.2, p.plotWidthM, 1e-9);
        assertEquals(30.0, p.plotLengthM, 1e-9);
        assertEquals(5_000_000, p.budgetKes, 0);
        assertEquals(750_000, v("A", "B", "", "Nairobi", "20", "20", "KES 750k").project.budgetKes, 0);
        assertEquals("an empty budget means not set", 0, v("A", "B", "", "Nairobi", "20", "20", "").project.budgetKes, 0);
    }

    @Test public void everyBadFieldGetsItsOwnMessageInFormOrder() {
        ProjectForm.Result r = v("", " ", "x".repeat(81), "Atlantis", "abc", "2", "lots");
        assertFalse(r.ok());
        assertEquals("[name, owner, location, county, plotWidth, plotLength, budget]", r.errors.keySet().toString());
        assertEquals("Enter a project name.", r.errors.get(ProjectForm.NAME));
        assertEquals("Choose one of the 47 counties.", r.errors.get(ProjectForm.COUNTY));
        assertTrue(r.errors.get(ProjectForm.PLOT_WIDTH).contains("Enter a width"));
        assertTrue(r.errors.get(ProjectForm.PLOT_LENGTH).contains("between 3 m and 500 m"));
        assertTrue(r.errors.get(ProjectForm.BUDGET).contains("Enter an amount"));
        assertEquals(null, r.project);
    }

    @Test public void limitsAreEnforced() {
        assertTrue(v("A", "B", "", "Nairobi", "500", "500", "").ok());
        assertFalse(v("A", "B", "", "Nairobi", "501", "20", "").ok());
        assertFalse(v("A", "B", "", "Nairobi", "20", "2.9", "").ok());
        assertFalse(v("A", "B", "", "Nairobi", "20", "20", "-5").ok());
        assertFalse(v("A", "B", "", "Nairobi", "20", "20", "20000000000").ok());
        assertFalse(v("x".repeat(61), "B", "", "Nairobi", "20", "20", "").ok());
    }

    @Test public void aMissingBuildingTypeDefaultsToBungalow() {
        Project p = ProjectForm.validate("A", "B", "", "Mombasa", "20", "20", "", null).project;
        assertNotNull(p);
        assertEquals(BuildingType.BUNGALOW, p.buildingType);
    }
}
