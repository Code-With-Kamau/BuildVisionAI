package com.buildvisionai.reports.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a report shows, as plain text and numbers. The Android PDF/PNG writers only lay this
 * out.
 */
public final class ReportModel {
    public String title;
    public String projectName, owner, location, county, buildingType;
    public String plotText;
    public String budgetText;
    public String versionText;
    public String statusText;
    public String generatedAt;
    /** Diagonal mark such as "DRAFT - NOT APPROVED"; null on approved or locked versions. */
    public String watermark;

    public final List<FloorSection> floors = new ArrayList<>();
    public final List<QuantityRow> quantities = new ArrayList<>();
    public final List<CostRow> costRows = new ArrayList<>();
    public final List<CostRow> addOnRows = new ArrayList<>();
    public CostRow total;
    public String perM2Text;
    public String vatText;
    public String benchmarkText;
    public String ratePackText;
    public final List<String> assumptions = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    public final List<String> issues = new ArrayList<>();
    public final List<SignOff> signOffs = new ArrayList<>();
    public String disclaimer;

    public static final class FloorSection {
        public String name;
        public String grossAreaText, netAreaText, countsText;
        public final List<RoomRow> rooms = new ArrayList<>();
    }

    public static final class RoomRow {
        public String name, type, area, size;
    }

    public static final class QuantityRow {
        public String category, description, unit, quantity, note;
        public boolean allowance;
    }

    public static final class CostRow {
        public String label, min, avg, max, share;
    }

    public static final class SignOff {
        public String who, role, stage, decision, comment, date;
    }
}
