package com.buildvisionai.estimation;

/**
 * Built-in Kenya rate packs.
 *
 * <p><b>These are indicative seed values, not surveyed market data.</b> They were set so that a
 * simple masonry house lands inside published per-square-metre ranges, and are meant to be replaced
 * with current quotes from local suppliers and fundis. The app shows the disclaimer next to every
 * estimate. Region multipliers (see {@link Region}) are placeholders for the same reason.
 */
public final class KenyaRatePack {
    private KenyaRatePack() {}

    public static final String AS_OF = "2026-10 (seed)";
    public static final String DISCLAIMER =
            "Indicative seed prices in KES, VAT-inclusive, for a Nairobi-area site. Not surveyed market data: "
                    + "replace with current local quotes before using an estimate for any financial decision.";

    public static RatePack forRegion(Region region) {
        RatePack p = new RatePack("KE-" + region.name() + "-2026", "Kenya 2026 - " + region.label, region, AS_OF, DISCLAIMER);
        double f = region.factor;
        // concrete & masonry
        put(p, Item.CEMENT_50KG, 750, f);
        put(p, Item.SAND_T, 3000, f);
        put(p, Item.BALLAST_T, 2800, f);
        put(p, Item.HARDCORE_M3, 2800, f);
        put(p, Item.DPM_M2, 80, f);
        put(p, Item.TERMITICIDE_M2, 120, f);
        put(p, Item.BRC_A142_M2, 520, f);
        put(p, Item.STEEL_KG, 135, f);
        put(p, Item.BINDING_WIRE_KG, 220, f);
        put(p, Item.FORMWORK_M2, 600, f);
        put(p, Item.UNIT_BLOCK_100, 55, f);
        put(p, Item.UNIT_BLOCK_150, 75, f);
        put(p, Item.UNIT_BLOCK_200, 100, f);
        put(p, Item.UNIT_STONE_225, 90, f);
        put(p, Item.UNIT_BRICK_112, 22, f);
        put(p, Item.UNIT_BRICK_225, 22, f);
        // roofing
        put(p, Item.TIMBER_M3, 58000, f);
        put(p, Item.ROOF_SHEET_G28, 1350, f);
        put(p, Item.ROOF_TILE_CONCRETE, 120, f);
        put(p, Item.RIDGE_M, 450, f);
        put(p, Item.ROOF_FIXINGS_KG, 320, f);
        put(p, Item.FASCIA_M, 450, f);
        put(p, Item.GUTTER_M, 650, f);
        put(p, Item.DOWNPIPE_M, 500, f);
        put(p, Item.WATERPROOFING_M2, 900, f);
        // finishes, doors, windows
        put(p, Item.GYPSUM_BOARD, 1500, f);
        put(p, Item.CEILING_FRAMING_M2, 350, f);
        put(p, Item.TILE_FLOOR_M2, 1500, f);
        put(p, Item.TILE_WALL_M2, 1300, f);
        put(p, Item.TILE_ADHESIVE_BAG, 950, f);
        put(p, Item.TILE_GROUT_KG, 180, f);
        put(p, Item.SKIRTING_M, 250, f);
        put(p, Item.PAINT_UNDERCOAT_L, 330, f);
        put(p, Item.PAINT_INT_L, 420, f);
        put(p, Item.PAINT_EXT_L, 520, f);
        put(p, Item.DOOR_SINGLE_M2, 9500, f);
        put(p, Item.DOOR_DOUBLE_M2, 10500, f);
        put(p, Item.DOOR_SLIDING_M2, 15000, f);
        put(p, Item.WINDOW_SLIDING_M2, 12500, f);
        put(p, Item.WINDOW_CASEMENT_M2, 10000, f);
        put(p, Item.WINDOW_FIXED_M2, 8500, f);
        put(p, Item.WINDOW_GRILL_M2, 3500, f);
        put(p, Item.HANDRAIL_M, 4500, f);
        // plumbing & drainage
        put(p, Item.WC_SET, 9500, f);
        put(p, Item.BASIN_SET, 6500, f);
        put(p, Item.SHOWER_SET, 9000, f);
        put(p, Item.BATHTUB_SET, 28000, f);
        put(p, Item.SINK_UNIT, 10000, f);
        put(p, Item.PIPE_WATER_M, 380, f);
        put(p, Item.PIPE_WASTE_M, 320, f);
        put(p, Item.PIPE_SOIL_M, 700, f);
        put(p, Item.INSPECTION_CHAMBER, 18000, f);
        put(p, Item.SEPTIC_SYSTEM, 300000, f);
        put(p, Item.SEWER_CONNECTION, 120000, f);
        put(p, Item.WATER_TANK_L, 6.5, f);
        put(p, Item.TANK_STAND, 25000, f);
        // electrical
        put(p, Item.ELEC_LIGHT_POINT, 1800, f);
        put(p, Item.ELEC_SOCKET_POINT, 2200, f);
        put(p, Item.ELEC_COOKER_POINT, 5500, f);
        put(p, Item.ELEC_HEATER_POINT, 3000, f);
        put(p, Item.ELEC_DB, 18000, f);
        put(p, Item.ELEC_EARTHING, 8000, f);
        put(p, Item.LIGHT_FITTING, 1200, f);
        // labour (installation only)
        put(p, Item.LAB_EXCAVATION_M3, 750, f);
        put(p, Item.LAB_HARDCORE_M3, 800, f);
        put(p, Item.LAB_CONCRETE_M3, 3200, f);
        put(p, Item.LAB_FORMWORK_M2, 700, f);
        put(p, Item.LAB_STEEL_KG, 28, f);
        put(p, Item.LAB_MASONRY_M2, 450, f);
        put(p, Item.LAB_PLASTER_M2, 380, f);
        put(p, Item.LAB_SCREED_M2, 260, f);
        put(p, Item.LAB_TILING_M2, 700, f);
        put(p, Item.LAB_PAINTING_M2, 300, f);
        put(p, Item.LAB_ROOFING_M2, 900, f);
        put(p, Item.LAB_CEILING_M2, 500, f);
        put(p, Item.LAB_DOORS_WINDOWS_M2, 1800, f);
        put(p, Item.LAB_PLUMBING_POINT, 2200, f);
        put(p, Item.LAB_ELECTRICAL_POINT, 1000, f);
        return p;
    }

    private static void put(RatePack p, Item item, double avg, double regionFactor) {
        p.set(item, Rate.around(avg * regionFactor));
    }

    public static RatePack nairobi() { return forRegion(Region.NAIROBI_METRO); }
}
