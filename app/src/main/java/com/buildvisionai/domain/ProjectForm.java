package com.buildvisionai.domain;

import com.buildvisionai.estimation.Counties;
import com.buildvisionai.models.BuildingType;
import com.buildvisionai.models.Project;
import com.buildvisionai.utils.LengthFormatter;
import com.buildvisionai.utils.MoneyFormatter;
import com.buildvisionai.utils.UnitSystem;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validation of the "new project" form, kept free of Android so the rules are unit-tested. The
 * screen passes the raw text of each field; this returns either a {@link Project} ready for the
 * repository or one message per field that needs fixing.
 *
 * <p>Plot sizes accept metres ("15.2"), metres with a unit ("15.2 m") or feet ("50ft", "50'"),
 * because Kenyan plots are quoted in both. A bare number is metres. Budget accepts "5,000,000",
 * "5m", "750k" and "KES 5,000,000"; empty means "not set".
 */
public final class ProjectForm {
    public static final String NAME = "name", OWNER = "owner", LOCATION = "location", COUNTY = "county",
            PLOT_WIDTH = "plotWidth", PLOT_LENGTH = "plotLength", BUDGET = "budget";

    public static final double MIN_PLOT_M = 3, MAX_PLOT_M = 500;
    public static final double MAX_BUDGET_KES = 10_000_000_000.0;

    private ProjectForm() {}

    public static final class Result {
        /** Field key to message, in form order. Empty when the form is valid. */
        public final Map<String, String> errors = new LinkedHashMap<>();
        /** Set only when {@link #errors} is empty. */
        public Project project;

        public boolean ok() { return errors.isEmpty(); }
    }

    public static Result validate(String name, String owner, String location, String county,
                                  String plotWidth, String plotLength, String budget, BuildingType type) {
        Result r = new Result();
        String n = trim(name), o = trim(owner), loc = trim(location);

        if (n.isEmpty()) r.errors.put(NAME, "Enter a project name.");
        else if (n.length() > 60) r.errors.put(NAME, "Use 60 characters or fewer.");

        if (o.isEmpty()) r.errors.put(OWNER, "Enter the owner's name.");
        else if (o.length() > 60) r.errors.put(OWNER, "Use 60 characters or fewer.");

        if (loc.length() > 80) r.errors.put(LOCATION, "Use 80 characters or fewer.");

        String canonicalCounty = null;
        for (String c : Counties.all()) {
            if (c.equalsIgnoreCase(trim(county))) { canonicalCounty = c; break; }
        }
        if (canonicalCounty == null) r.errors.put(COUNTY, "Choose one of the 47 counties.");

        double w = plotMetres(plotWidth), l = plotMetres(plotLength);
        if (Double.isNaN(w)) r.errors.put(PLOT_WIDTH, "Enter a width such as 15.2 or 50ft.");
        else if (w < MIN_PLOT_M || w > MAX_PLOT_M) r.errors.put(PLOT_WIDTH, "Width must be between 3 m and 500 m.");
        if (Double.isNaN(l)) r.errors.put(PLOT_LENGTH, "Enter a length such as 30.5 or 100ft.");
        else if (l < MIN_PLOT_M || l > MAX_PLOT_M) r.errors.put(PLOT_LENGTH, "Length must be between 3 m and 500 m.");

        double b = 0;
        String bt = trim(budget);
        if (!bt.isEmpty()) {
            b = MoneyFormatter.parse(bt);
            if (Double.isNaN(b)) r.errors.put(BUDGET, "Enter an amount such as 5,000,000 or 5m.");
            else if (b < 0 || b > MAX_BUDGET_KES) r.errors.put(BUDGET, "Enter an amount between 0 and 10 billion.");
        }

        if (!r.ok()) return r;

        Project p = new Project();
        p.projectName = n;
        p.ownerName = o;
        p.location = loc;
        p.county = canonicalCounty;
        p.plotWidthM = w;
        p.plotLengthM = l;
        p.budgetKes = b;
        p.buildingType = type == null ? BuildingType.BUNGALOW : type;
        r.project = p;
        return r;
    }

    /** Metres from "15.2", "15.2 m", "50ft" or "50'"; NaN if it is none of those. */
    static double plotMetres(String text) {
        String t = trim(text);
        if (t.isEmpty()) return Double.NaN;
        double mm = LengthFormatter.parseMm(t, UnitSystem.METRIC);
        return Double.isNaN(mm) ? Double.NaN : mm / 1000.0;
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
}
