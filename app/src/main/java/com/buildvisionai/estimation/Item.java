package com.buildvisionai.estimation;

import com.buildvisionai.models.WallMaterial;

/**
 * Catalogue of everything that can appear on a quantity schedule. Each item has one unit and one
 * rate code (its enum name) in the {@link RatePack}. Materials are priced supply-only; labour items
 * (LAB_*) carry installation, so nothing is counted twice.
 *
 * <p>{@code finishSensitive} items are scaled by the project's {@link FinishLevel}.
 */
public enum Item {
    // --- concrete & masonry materials
    CEMENT_50KG("Simba cement 50 kg", "bag", false, false),
    SAND_T("Building sand", "tonne", false, false),
    BALLAST_T("Ballast / coarse aggregate 20 mm", "tonne", false, false),
    HARDCORE_M3("Hardcore filling (compacted)", "m3", false, false),
    DPM_M2("Damp-proof membrane 1000 gauge", "m2", false, false),
    TERMITICIDE_M2("Anti-termite treatment", "m2", false, false),
    BRC_A142_M2("Welded mesh A142", "m2", false, false),
    STEEL_KG("Reinforcement steel (Y-bars)", "kg", false, false),
    BINDING_WIRE_KG("Binding wire", "kg", false, false),
    FORMWORK_M2("Formwork (timber, reusable)", "m2", false, false),
    UNIT_BLOCK_100("Hollow block 4\" (100 mm)", "no.", false, false),
    UNIT_BLOCK_150("Hollow block 6\" (150 mm)", "no.", false, false),
    UNIT_BLOCK_200("Hollow block 8\" (200 mm)", "no.", false, false),
    UNIT_STONE_225("Machine-cut stone 9\" (225 mm)", "no.", false, false),
    UNIT_BRICK_112("Clay brick", "no.", false, false),
    UNIT_BRICK_225("Clay brick", "no.", false, false),
    // --- roofing
    TIMBER_M3("Roof timber (trusses, purlins)", "m3", false, false),
    ROOF_SHEET_G28("Iron sheet G28 pre-painted, 3 m", "no.", false, false),
    ROOF_TILE_CONCRETE("Concrete roofing tile", "no.", false, false),
    RIDGE_M("Ridge / hip capping", "m", false, false),
    ROOF_FIXINGS_KG("Roofing nails, screws, washers", "kg", false, false),
    FASCIA_M("Fascia board, painted", "m", false, false),
    GUTTER_M("PVC gutter", "m", false, false),
    DOWNPIPE_M("PVC downpipe", "m", false, false),
    WATERPROOFING_M2("Roof waterproofing membrane", "m2", false, false),
    // --- finishes
    GYPSUM_BOARD("Gypsum board 12.5 mm, 2.4 x 1.2 m", "no.", true, false),
    CEILING_FRAMING_M2("Ceiling framing / brandering", "m2", false, false),
    TILE_FLOOR_M2("Floor tiles", "m2", true, false),
    TILE_WALL_M2("Wall tiles", "m2", true, false),
    TILE_ADHESIVE_BAG("Tile adhesive 25 kg", "bag", false, false),
    TILE_GROUT_KG("Tile grout", "kg", false, false),
    SKIRTING_M("Tile skirting", "m", true, false),
    PAINT_UNDERCOAT_L("Undercoat paint", "litre", false, false),
    PAINT_INT_L("Interior emulsion paint", "litre", true, false),
    PAINT_EXT_L("Exterior weather-resistant paint", "litre", true, false),
    DOOR_SINGLE_M2("Single door with frame and ironmongery", "m2", true, false),
    DOOR_DOUBLE_M2("Double door with frame and ironmongery", "m2", true, false),
    DOOR_SLIDING_M2("Sliding door (aluminium) with glass", "m2", true, false),
    WINDOW_SLIDING_M2("Sliding window (aluminium) with glass", "m2", true, false),
    WINDOW_CASEMENT_M2("Casement window with glass", "m2", true, false),
    WINDOW_FIXED_M2("Fixed window with glass", "m2", true, false),
    WINDOW_GRILL_M2("Window burglar-proofing grill", "m2", false, false),
    HANDRAIL_M("Stair handrail / balustrade", "m", true, false),
    // --- plumbing & drainage
    WC_SET("Water closet set", "no.", true, false),
    BASIN_SET("Wash basin with taps", "no.", true, false),
    SHOWER_SET("Shower tray and mixer", "no.", true, false),
    BATHTUB_SET("Bathtub with taps", "no.", true, false),
    SINK_UNIT("Kitchen sink with taps", "no.", true, false),
    PIPE_WATER_M("Water supply pipe and fittings", "m", false, false),
    PIPE_WASTE_M("Waste pipe 50 mm and fittings", "m", false, false),
    PIPE_SOIL_M("Soil pipe 110 mm and fittings", "m", false, false),
    INSPECTION_CHAMBER("Inspection chamber with cover", "no.", false, false),
    SEPTIC_SYSTEM("Septic tank and soak pit (allowance)", "no.", false, false),
    SEWER_CONNECTION("Sewer connection (allowance)", "no.", false, false),
    WATER_TANK_L("Water storage tank", "litre", false, false),
    TANK_STAND("Water tank stand (allowance)", "no.", false, false),
    // --- electrical
    ELEC_LIGHT_POINT("Lighting point: cable, conduit, switch", "no.", false, false),
    ELEC_SOCKET_POINT("Socket outlet point: cable, conduit, socket", "no.", false, false),
    ELEC_COOKER_POINT("Cooker control point", "no.", false, false),
    ELEC_HEATER_POINT("Water heater point", "no.", false, false),
    ELEC_DB("Distribution board with breakers", "no.", false, false),
    ELEC_EARTHING("Earthing set", "no.", false, false),
    LIGHT_FITTING("Light fitting", "no.", true, false),
    // --- labour (installation only)
    LAB_EXCAVATION_M3("Excavation and backfill", "m3", false, true),
    LAB_HARDCORE_M3("Hardcore laying and compaction", "m3", false, true),
    LAB_CONCRETE_M3("Concrete mixing, placing and curing", "m3", false, true),
    LAB_FORMWORK_M2("Formwork fixing and striking", "m2", false, true),
    LAB_STEEL_KG("Steel cutting, bending and fixing", "kg", false, true),
    LAB_MASONRY_M2("Block / stone / brick laying", "m2", false, true),
    LAB_PLASTER_M2("Plastering", "m2", false, true),
    LAB_SCREED_M2("Screeding", "m2", false, true),
    LAB_TILING_M2("Tiling", "m2", false, true),
    LAB_PAINTING_M2("Painting (all coats)", "m2", false, true),
    LAB_ROOFING_M2("Roof carpentry and covering", "m2", false, true),
    LAB_CEILING_M2("Ceiling installation", "m2", false, true),
    LAB_DOORS_WINDOWS_M2("Fixing doors and windows", "m2", false, true),
    LAB_PLUMBING_POINT("Plumbing installation", "point", false, true),
    LAB_ELECTRICAL_POINT("Electrical installation", "point", false, true);

    public final String description;
    public final String unit;
    public final boolean finishSensitive;
    public final boolean labour;

    Item(String description, String unit, boolean finishSensitive, boolean labour) {
        this.description = description;
        this.unit = unit;
        this.finishSensitive = finishSensitive;
        this.labour = labour;
    }

    /** Rate code used by rate packs and stored with estimates. */
    public String code() { return name(); }

    /** Masonry item for a wall material. */
    public static Item unitFor(WallMaterial m) { return valueOf("UNIT_" + m.name()); }

    /** True for items bought in whole pieces: quantities are rounded up. */
    public boolean wholeUnits() { return unit.equals("no.") || unit.equals("bag"); }
}
