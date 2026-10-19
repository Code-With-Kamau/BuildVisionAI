package com.buildvisionai.estimation;

import com.buildvisionai.geometry.DetectedRoom;
import com.buildvisionai.geometry.Footprint;
import com.buildvisionai.geometry.PlanMetrics;
import com.buildvisionai.geometry.RoomMetrics;
import com.buildvisionai.geometry.WallMetrics;
import com.buildvisionai.models.Column;
import com.buildvisionai.models.Door;
import com.buildvisionai.models.Floor;
import com.buildvisionai.models.Furniture;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.RoomType;
import com.buildvisionai.models.Stair;
import com.buildvisionai.models.StairType;
import com.buildvisionai.models.Vec2;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.WallKind;
import com.buildvisionai.models.WallMaterial;
import com.buildvisionai.models.Window;
import com.buildvisionai.planner.FloorPlan;
import com.buildvisionai.planner.PlanDocument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic material take-off. The same drawing and settings always produce the same schedule:
 * no AI, no randomness. All formulas are ordinary construction-estimating practice and are written
 * out below so a quantity surveyor can check them line by line.
 *
 * <p>What it measures: strip footings, foundation masonry, column pads, hardcore, ground slab,
 * walls (units and mortar), lintels, ring beams, columns, suspended slabs, stairs, roof, ceilings,
 * floor/wall finishes, plaster, paint, doors, windows, plumbing and electrical points.
 * What it only allows for (flagged as allowance): roof timber, septic tank, water-tank stand.
 * What it ignores: site works, boundary walls, fittings and furniture, external drainage runs.
 */
public final class MaterialEstimator {
    private MaterialEstimator() {}

    // ---- constants
    static final double COMPACTION_FACTOR = 1.20;          // loose hardcore -> compacted volume
    static final double SLAB_MESH_LAP = 1.10;
    static final double STEEL_FOOTING = 70, STEEL_PAD = 80, STEEL_BEAM = 110, STEEL_COLUMN = 150,
            STEEL_SLAB = 110, STEEL_LINTEL = 120, STEEL_STAIR = 110;
    static final double RING_BEAM_DEPTH_MM = 150;
    static final double LINTEL_BEARING_MM = 200;            // each side
    static final double LINTEL_DEPTH_MM = 150;
    static final double G28_COVER_WIDTH_M = 0.85, G28_LENGTH_M = 3.0, G28_END_LAP_M = 0.15;
    static final double TILES_PER_M2_ROOF = 10;
    static final double ROOF_TIMBER_M3_PER_M2 = 0.028;      // allowance
    static final double ROOF_FIXINGS_KG_PER_M2 = 0.20;
    static final double GYPSUM_BOARD_M2 = 2.4 * 1.2;
    static final double PAINT_COVER_INT = 11.0;             // m2 per litre per coat
    static final double PAINT_COVER_EXT = 8.0;
    static final double TILE_ADHESIVE_M2_PER_BAG = 4.5;
    static final double TILE_GROUT_KG_PER_M2 = 0.5;
    static final double STAIR_GOING_M = 0.25;
    static final double STAIR_SLAB_M = 0.15;
    static final double PIPE_WATER_M_PER_FIXTURE = 7, PIPE_WASTE_M_PER_FIXTURE = 3, PIPE_SOIL_M_PER_WC = 4;
    static final double AREA_PER_LIGHT_POINT_M2 = 4.0;

    public static QuantitySchedule estimate(PlanDocument doc, EstimateSettings s) {
        Builder b = new Builder(s);
        List<Floor> floors = new ArrayList<>(doc.floors());
        Collections.sort(floors, Comparator.comparingInt(f -> f.level));

        // which floors have content, ground = lowest, top = highest
        List<Floor> used = new ArrayList<>();
        for (Floor f : floors) if (!doc.floorPlan(f.id).isEmpty()) used.add(f);
        if (used.isEmpty()) {
            b.out.warnings.add("The plan is empty: draw walls to get quantities.");
            return b.finish();
        }
        b.out.floors = used.size();
        int groundLevel = used.get(0).level;
        int topLevel = used.get(used.size() - 1).level;

        Map<Integer, Double> stairVoidByLevel = new HashMap<>();
        for (Floor f : used) {
            double v = 0;
            for (Stair st : doc.floorPlan(f.id).stairs) v += st.widthMm * st.depthMm / 1e6;
            stairVoidByLevel.put(f.level, v);
        }

        double buildingHeightM = 0;
        for (Floor f : used) buildingHeightM += f.floorToFloorMm / 1000.0;

        WallMaterial dominantExternal = WallMaterial.BLOCK_200;
        double bestExt = -1;
        Footprint topPrint = null;
        double topFootprintArea = 0, topPerimeter = 0;
        RectInfo topRect = null;

        for (Floor f : used) {
            FloorPlan fp = doc.floorPlan(f.id);
            PlanMetrics pm = fp.metrics();
            boolean ground = f.level == groundLevel;
            boolean top = f.level == topLevel;

            double footprintArea = Math.max(pm.topology.footprintAreaM2, pm.netFloorAreaM2);
            b.out.grossFloorAreaM2 += footprintArea;
            b.out.netFloorAreaM2 += pm.netFloorAreaM2;
            if (pm.topology.rooms.isEmpty()) {
                b.out.warnings.add(f.name + ": no closed rooms found. Close the wall loops so floors, ceilings, "
                        + "paint, plumbing and electrical can be measured.");
            }
            if (pm.unattachedOpenings > 0) {
                b.out.warnings.add(f.name + ": " + pm.unattachedOpenings + " door/window(s) are not attached to a wall "
                        + "that is long enough, so they are not counted.");
            }

            walls(b, fp, pm, f, ground);
            if (ground) groundSubstructure(b, fp, pm);
            else suspendedSlab(b, f, pm, stairVoidByLevel.getOrDefault(f.level - 1, 0.0));
            columns(b, fp, ground);
            stairs(b, fp);
            openings(b, fp, pm);
            rooms(b, fp, pm, f);
            externalFinishes(b, pm);
            plumbing(b, fp, pm);
            electrical(b, fp, pm, f);

            for (WallMetrics m : pm.walls) {
                if (m.kind == WallKind.EXTERNAL && m.lengthM > bestExt) {
                    bestExt = m.lengthM;
                    dominantExternal = m.material;
                }
            }
            if (top) {
                topFootprintArea = pm.topology.footprintAreaM2 > 0 ? pm.topology.footprintAreaM2 : pm.netFloorAreaM2;
                topPerimeter = pm.topology.externalPerimeterM > 0 ? pm.topology.externalPerimeterM
                        : 4 * Math.sqrt(Math.max(topFootprintArea, 1));
                topRect = RectInfo.of(pm);
            }
        }

        roof(b, topFootprintArea, topPerimeter, topRect, buildingHeightM, dominantExternal);
        services(b, used.size());
        b.out.notes.add("Wall quantities use wall centrelines and deduct openings; overlaps at corners are not deducted.");
        b.out.notes.add("Roof timber, septic tank / sewer connection and tank stand are allowances, not measurements.");
        b.out.notes.add("Excludes site clearing, boundary wall, landscaping, external drains, fittings and furniture.");
        return b.finish();
    }

    
    // Walls, lintels, ring beams
    

    private static void walls(Builder b, FloorPlan fp, PlanMetrics pm, Floor f, boolean ground) {
        EnumMap<WallMaterial, double[]> byMat = new EnumMap<>(WallMaterial.class); // {faceArea, volume}
        double ringBeamM = 0;
        double ringBeamVol = 0, ringBeamForm = 0;
        for (Wall w : fp.walls) {
            WallMetrics m = pm.byWallId.get(w.id);
            double[] acc = byMat.computeIfAbsent(w.material, k -> new double[2]);
            acc[0] += m.netFaceAreaM2;
            acc[1] += m.netFaceAreaM2 * w.thicknessMm / 1000.0;
            if (w.kind.loadBearing()) {
                double t = w.thicknessMm / 1000.0, d = RING_BEAM_DEPTH_MM / 1000.0;
                ringBeamM += m.lengthM;
                ringBeamVol += m.lengthM * t * d;
                ringBeamForm += m.lengthM * (2 * d + t);
            }
        }
        for (Map.Entry<WallMaterial, double[]> e : byMat.entrySet()) {
            masonry(b, CostCategory.WALLING, e.getKey(), e.getValue()[0], e.getValue()[1], f.name + " walls");
        }
        if (ringBeamVol > 0) {
            b.concrete(CostCategory.WALLING, ringBeamVol, Mix.C_1_15_3, STEEL_BEAM, ringBeamForm,
                    "ring beam / wall plate, " + f.name);
        }

        // lintels over every attached opening
        double lintelVol = 0, lintelForm = 0;
        for (Door d : fp.doors) {
            if (!d.attached) continue;
            Wall w = fp.wallsById.get(d.wallId);
            if (w == null) continue;
            double len = (d.widthMm + 2 * LINTEL_BEARING_MM) / 1000.0, t = w.thicknessMm / 1000.0, dep = LINTEL_DEPTH_MM / 1000.0;
            lintelVol += len * t * dep;
            lintelForm += len * (t + dep);
        }
        for (Window win : fp.windows) {
            if (!win.attached) continue;
            Wall w = fp.wallsById.get(win.wallId);
            if (w == null) continue;
            double len = (win.widthMm + 2 * LINTEL_BEARING_MM) / 1000.0, t = w.thicknessMm / 1000.0, dep = LINTEL_DEPTH_MM / 1000.0;
            lintelVol += len * t * dep;
            lintelForm += len * (t + dep);
        }
        if (lintelVol > 0) {
            b.concrete(CostCategory.WALLING, lintelVol, Mix.C_1_2_4, STEEL_LINTEL, lintelForm, "lintels, " + f.name);
        }
        if (ground) {
            // footings and foundation masonry for load-bearing walls
            double excavation = 0, footingVol = 0, fwFace = 0;
            EnumMap<WallMaterial, double[]> fw = new EnumMap<>(WallMaterial.class);
            double h = b.s.foundationWallHeightMm() / 1000.0;
            for (Wall w : fp.walls) {
                if (!w.kind.loadBearing()) continue;
                double len = w.lengthMm() / 1000.0;
                double width = (w.thicknessMm + 2 * b.s.footingProjectionMm) / 1000.0;
                excavation += width * (b.s.foundationDepthMm / 1000.0) * len;
                footingVol += width * (b.s.footingThicknessMm / 1000.0) * len;
                double[] acc = fw.computeIfAbsent(w.material, k -> new double[2]);
                acc[0] += len * h;
                acc[1] += len * h * w.thicknessMm / 1000.0;
                fwFace += len * h;
            }
            if (footingVol > 0) {
                b.concrete(CostCategory.FOUNDATION, footingVol, Mix.C_1_2_4, STEEL_FOOTING, 0, "strip footings");
                b.work(WorkItem.EXCAVATION, excavation);
            }
            for (Map.Entry<WallMaterial, double[]> e : fw.entrySet()) {
                masonry(b, CostCategory.FOUNDATION, e.getKey(), e.getValue()[0], e.getValue()[1], "foundation walling");
            }
            b.out.notes.add("Foundation: strip footings under load-bearing walls, " + Math.round(b.s.foundationDepthMm)
                    + " mm trench, footing projects " + Math.round(b.s.footingProjectionMm) + " mm each side. "
                    + "A structural engineer must confirm sizes for the actual soil.");
            if (fwFace <= 0) b.out.warnings.add("No load-bearing walls on the ground floor: no strip footings were measured.");
        }
    }

    /** Units + mortar for a face area of one wall material. */
    private static void masonry(Builder b, CostCategory cat, WallMaterial mat, double faceM2, double volumeM3, String basis) {
        if (faceM2 <= 1e-9) return;
        double unitsNet = faceM2 * mat.unitsPerM2(b.s.mortarJointMm);
        b.line(cat, Item.unitFor(mat), unitsNet, b.s.wastage.masonryUnits, basis, false);
        double mortarWet = Math.max(0, volumeM3 - unitsNet * mat.unitVolumeM3());
        b.mortar(cat, mortarWet, Mix.M_1_4, basis + " mortar");
        b.work(WorkItem.MASONRY, faceM2);
    }


    // Ground floor sub-structure and suspended slabs

    private static void groundSubstructure(Builder b, FloorPlan fp, PlanMetrics pm) {
        double area = pm.netFloorAreaM2;
        if (area <= 0) return;
        double hardcore = area * b.s.hardcoreMm / 1000.0 * COMPACTION_FACTOR;
        b.line(CostCategory.FOUNDATION, Item.HARDCORE_M3, hardcore, 0, "hardcore " + Math.round(b.s.hardcoreMm) + " mm under slab", false);
        b.work(WorkItem.HARDCORE, area * b.s.hardcoreMm / 1000.0);
        b.line(CostCategory.FOUNDATION, Item.DPM_M2, area * SLAB_MESH_LAP, 0, "damp-proof membrane", false);
        b.line(CostCategory.FOUNDATION, Item.TERMITICIDE_M2, area, 0, "anti-termite treatment", false);
        b.line(CostCategory.FOUNDATION, Item.BRC_A142_M2, area * SLAB_MESH_LAP, b.s.wastage.steel, "slab mesh A142", false);
        b.concrete(CostCategory.FOUNDATION, area * b.s.groundSlabMm / 1000.0, Mix.C_1_2_4, 0, 0,
                "ground slab " + Math.round(b.s.groundSlabMm) + " mm");
    }

    private static void suspendedSlab(Builder b, Floor f, PlanMetrics pm, double stairVoidM2) {
        double footprint = pm.topology.footprintAreaM2 > 0 ? pm.topology.footprintAreaM2 : pm.netFloorAreaM2;
        double area = Math.max(0, footprint - stairVoidM2);
        if (area <= 0) return;
        double t = b.s.upperSlabMm / 1000.0;
        double perimeter = pm.topology.externalPerimeterM;
        b.concrete(CostCategory.WALLING, area * t, Mix.C_1_15_3, STEEL_SLAB, area + perimeter * t,
                "suspended slab, " + f.name + (stairVoidM2 > 0 ? " (stair opening deducted)" : ""));
    }

    
    // Columns and stairs


    private static void columns(Builder b, FloorPlan fp, boolean ground) {
        double vol = 0, form = 0, padVol = 0, padExc = 0;
        for (Column c : fp.columns) {
            double h = c.heightMm / 1000.0;
            vol += c.sectionAreaM2() * h;
            double w = c.widthMm / 1000.0, d = c.depthMm / 1000.0;
            double per = c.isElliptical() ? Math.PI * (w + d) / 2 : 2 * (w + d);
            form += per * h;
            if (ground) {
                double pad = b.s.padSizeMm / 1000.0;
                padVol += pad * pad * b.s.padThicknessMm / 1000.0;
                padExc += pad * pad * b.s.foundationDepthMm / 1000.0;
            }
        }
        if (vol > 0) b.concrete(CostCategory.WALLING, vol, Mix.C_1_15_3, STEEL_COLUMN, form, "columns (" + fp.columns.size() + ")");
        if (padVol > 0) {
            b.concrete(CostCategory.FOUNDATION, padVol, Mix.C_1_2_4, STEEL_PAD, 0, "column pads");
            b.work(WorkItem.EXCAVATION, padExc);
        }
    }

    private static void stairs(Builder b, FloorPlan fp) {
        for (Stair st : fp.stairs) {
            int steps = st.stepCount();
            double rise = st.riseMm / 1000.0;
            double run = steps * STAIR_GOING_M;
            double flightW = st.stairType == StairType.STRAIGHT ? st.widthMm / 1000.0 : 1.0;
            double slope = Math.hypot(rise, run);
            double waist = STAIR_SLAB_M * slope * flightW;
            double stepsVol = 0.5 * (rise / steps) * STAIR_GOING_M * flightW * steps;
            double landing = 0;
            if (st.stairType == StairType.L_SHAPED) landing = flightW * flightW * STAIR_SLAB_M;
            if (st.stairType == StairType.U_SHAPED) landing = flightW * (2 * flightW + 0.1) * STAIR_SLAB_M;
            double form = slope * flightW + 2 * slope * STAIR_SLAB_M + landing / STAIR_SLAB_M;
            b.concrete(CostCategory.WALLING, waist + stepsVol + landing, Mix.C_1_15_3, STEEL_STAIR, form,
                    "staircase (" + st.stairType.label + ", " + steps + " risers)");
            double rail = slope + (st.stairType == StairType.STRAIGHT ? 1.0 : 2.0);
            b.line(CostCategory.FINISHES, Item.HANDRAIL_M, rail, 0, "stair handrail", false);
        }
    }

    // Doors, windows


    private static void openings(Builder b, FloorPlan fp, PlanMetrics pm) {
        double areaTotal = 0;
        for (Door d : fp.doors) {
            if (!d.attached) continue;
            double a = d.widthMm * d.heightMm / 1e6;
            Item it = d.doorType == com.buildvisionai.models.DoorType.SINGLE ? Item.DOOR_SINGLE_M2
                    : d.doorType == com.buildvisionai.models.DoorType.DOUBLE ? Item.DOOR_DOUBLE_M2 : Item.DOOR_SLIDING_M2;
            b.line(CostCategory.FINISHES, it, a, 0, d.doorType.label.toLowerCase() + "s", false);
            areaTotal += a;
        }
        for (Window w : fp.windows) {
            if (!w.attached) continue;
            double a = w.widthMm * w.heightMm / 1e6;
            Item it = w.windowType == com.buildvisionai.models.WindowType.SLIDING ? Item.WINDOW_SLIDING_M2
                    : w.windowType == com.buildvisionai.models.WindowType.CASEMENT ? Item.WINDOW_CASEMENT_M2 : Item.WINDOW_FIXED_M2;
            b.line(CostCategory.FINISHES, it, a, 0, w.windowType.label.toLowerCase() + " windows", false);
            areaTotal += a;
            Wall host = fp.wallsById.get(w.wallId);
            if (b.s.windowGrills && host != null && host.kind == WallKind.EXTERNAL) {
                b.line(CostCategory.FINISHES, Item.WINDOW_GRILL_M2, a, 0, "grills on external windows", false);
            }
        }
        b.work(WorkItem.DOORS_WINDOWS, areaTotal);
    }

    // Room finishes: floor, walls, ceiling, paint
    

    private static void rooms(Builder b, FloorPlan fp, PlanMetrics pm, Floor f) {
        Map<DetectedRoom, RoomMetrics> byShape = new IdentityHashMap<>();
        for (RoomMetrics rm : pm.rooms) byShape.put(rm.shape, rm);
        EstimateSettings s = b.s;
        Wastage w = s.wastage;

        for (Room r : fp.rooms) {
            DetectedRoom d = fp.shapeOf(r);
            RoomMetrics rm = d == null ? null : byShape.get(d);
            if (rm == null) continue;
            RoomType t = r.roomType;
            double floorA = d.netAreaM2;
            // plaster behind everything, screed under everything
            b.mortar(CostCategory.FINISHES, rm.netWallSurfaceM2 * s.plasterMm / 1000.0, Mix.M_1_4, "internal plaster");
            b.work(WorkItem.PLASTER, rm.netWallSurfaceM2);
            b.mortar(CostCategory.FINISHES, floorA * s.screedMm / 1000.0, Mix.M_1_3, "floor screed");
            b.work(WorkItem.SCREED, floorA);

            double tiled = 0;
            if (t.tiledFloor) {
                b.line(CostCategory.FINISHES, Item.TILE_FLOOR_M2, floorA, w.tiles, "floor tiles", false);
                tiled += floorA;
                double skirting = Math.max(0, d.netPerimeterM - rm.doorWidthTotalM);
                b.line(CostCategory.FINISHES, Item.SKIRTING_M, skirting, w.tiles, "tile skirting", false);
            }
            double wallTiles = 0;
            if (t.wallTileHeightMm > 0) {
                wallTiles = Math.max(0, d.netPerimeterM - rm.doorWidthTotalM) * t.wallTileHeightMm / 1000.0;
                b.line(CostCategory.FINISHES, Item.TILE_WALL_M2, wallTiles, w.tiles, "wall tiles in " + t.label.toLowerCase() + "s", false);
                tiled += wallTiles;
            }
            if (tiled > 0) {
                b.line(CostCategory.FINISHES, Item.TILE_ADHESIVE_BAG, tiled / TILE_ADHESIVE_M2_PER_BAG, w.tiles, "tile adhesive", false);
                b.line(CostCategory.FINISHES, Item.TILE_GROUT_KG, tiled * TILE_GROUT_KG_PER_M2, 0, "tile grout", false);
                b.work(WorkItem.TILING, tiled);
            }

            double ceiling = (s.ceilings && t.ceiling) ? floorA : 0;
            if (ceiling > 0) {
                b.line(CostCategory.FINISHES, Item.GYPSUM_BOARD, ceiling / GYPSUM_BOARD_M2, w.boards, "gypsum ceiling", false);
                b.line(CostCategory.FINISHES, Item.CEILING_FRAMING_M2, ceiling, 0, "ceiling framing", false);
                b.work(WorkItem.CEILING, ceiling);
            }
            double paintWalls = Math.max(0, rm.netWallSurfaceM2 - wallTiles);
            double paintArea = paintWalls + ceiling;
            if (paintArea > 0) {
                b.line(CostCategory.FINISHES, Item.PAINT_UNDERCOAT_L, paintArea / PAINT_COVER_INT, w.paint, "internal undercoat", false);
                b.line(CostCategory.FINISHES, Item.PAINT_INT_L, 2 * paintArea / PAINT_COVER_INT, w.paint, "internal finish, 2 coats", false);
                b.work(WorkItem.PAINTING, paintArea);
            }
        }
    }

    private static void externalFinishes(Builder b, PlanMetrics pm) {
        double area = 0;
        for (WallMetrics m : pm.walls) if (m.kind == WallKind.EXTERNAL) area += m.netFaceAreaM2;
        if (area <= 0) return;
        b.mortar(CostCategory.FINISHES, area * (b.s.plasterMm + 3) / 1000.0, Mix.M_1_4, "external plaster");
        b.work(WorkItem.PLASTER, area);
        b.line(CostCategory.FINISHES, Item.PAINT_UNDERCOAT_L, area / PAINT_COVER_EXT, b.s.wastage.paint, "external sealer", false);
        b.line(CostCategory.FINISHES, Item.PAINT_EXT_L, 2 * area / PAINT_COVER_EXT, b.s.wastage.paint, "external finish, 2 coats", false);
        b.work(WorkItem.PAINTING, area);
    }


    // Roof


    private static final class RectInfo {
        final double longM, shortM;

        RectInfo(double longM, double shortM) { this.longM = longM; this.shortM = shortM; }

        static RectInfo of(PlanMetrics pm) {
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            List<Footprint> fps = pm.topology.footprints;
            if (fps.isEmpty()) {
                double side = Math.sqrt(Math.max(pm.netFloorAreaM2, 1));
                return new RectInfo(side, side);
            }
            for (Footprint f : fps) {
                for (Vec2 p : f.outline) {
                    minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
                    minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
                }
            }
            double w = (maxX - minX) / 1000.0, h = (maxY - minY) / 1000.0;
            return new RectInfo(Math.max(w, h), Math.min(w, h));
        }
    }

    private static void roof(Builder b, double footprintM2, double perimeterM, RectInfo rect, double buildingHeightM,
                             WallMaterial gableMaterial) {
        if (footprintM2 <= 0) return;
        EstimateSettings s = b.s;
        double o = s.roofOverhangMm / 1000.0;
        double band = perimeterM * o + 4 * o * o;
        double planArea = footprintM2 + band;
        RoofType rt = s.roofType;
        double eaves = perimeterM + 8 * o;

        if (rt == RoofType.FLAT_SLAB) {
            double t = s.upperSlabMm / 1000.0;
            b.concrete(CostCategory.ROOFING, planArea * t, Mix.C_1_15_3, STEEL_SLAB, planArea + eaves * t, "flat roof slab");
            b.line(CostCategory.ROOFING, Item.WATERPROOFING_M2, planArea * 1.10, 0, "roof waterproofing with upturns", false);
            b.line(CostCategory.ROOFING, Item.DOWNPIPE_M, Math.max(2, Math.ceil(eaves / 12.0)) * buildingHeightM, 0, "downpipes", false);
            b.out.notes.add("Flat roof: a structural engineer must design the slab; waterproofing falls and screed to falls are not included.");
            return;
        }

        double cos = Math.cos(Math.toRadians(s.roofPitchDeg));
        double roofArea = planArea / cos;
        b.out.notes.add(String.format(java.util.Locale.US, "Roof: %s, pitch %.0f deg, overhang %.0f mm; roof area %.1f m2 from the top-floor footprint.",
                rt.label, s.roofPitchDeg, s.roofOverhangMm, roofArea));
        if (rt.cover == RoofType.Cover.IRON_SHEET) {
            double per = G28_COVER_WIDTH_M * (G28_LENGTH_M - G28_END_LAP_M);
            b.line(CostCategory.ROOFING, Item.ROOF_SHEET_G28, roofArea / per, s.wastage.roofSheets, "iron sheets, " + String.format(java.util.Locale.US, "%.2f", per) + " m2 cover each", false);
        } else {
            b.line(CostCategory.ROOFING, Item.ROOF_TILE_CONCRETE, roofArea * TILES_PER_M2_ROOF, s.wastage.roofSheets, "concrete tiles, 10 per m2", false);
        }
        double ridge = rt.hipped ? rect.longM + 1.5 * rect.shortM : rect.longM;
        b.line(CostCategory.ROOFING, Item.RIDGE_M, ridge, 0.05, rt.hipped ? "ridge and hips (from bounding box)" : "ridge", false);
        b.line(CostCategory.ROOFING, Item.ROOF_FIXINGS_KG, roofArea * ROOF_FIXINGS_KG_PER_M2, 0, "roofing fixings", false);
        b.line(CostCategory.ROOFING, Item.TIMBER_M3, roofArea * ROOF_TIMBER_M3_PER_M2, s.wastage.timber, "roof structure (allowance)", true);
        b.line(CostCategory.ROOFING, Item.FASCIA_M, eaves, 0.05, "fascia", false);
        double gutter = rt.hipped ? eaves : 2 * (rect.longM + 2 * o);
        b.line(CostCategory.ROOFING, Item.GUTTER_M, gutter, 0.05, "gutters", false);
        b.line(CostCategory.ROOFING, Item.DOWNPIPE_M, Math.max(2, Math.ceil(gutter / 10.0)) * buildingHeightM, 0, "downpipes", false);
        b.work(WorkItem.ROOFING, roofArea);

        if (!rt.hipped) {
            // triangular gable-end masonry above the wall plate (two gables)
            double span = rect.shortM;
            double gableFace = span * span * Math.tan(Math.toRadians(s.roofPitchDeg)) / 2.0;
            masonryAllowance(b, gableMaterial, gableFace, "gable ends");
        }
    }

    private static void masonryAllowance(Builder b, WallMaterial mat, double faceM2, String basis) {
        if (faceM2 <= 0) return;
        double unitsNet = faceM2 * mat.unitsPerM2(b.s.mortarJointMm);
        b.line(CostCategory.WALLING, Item.unitFor(mat), unitsNet, b.s.wastage.masonryUnits, basis, false);
        b.mortar(CostCategory.WALLING, Math.max(0, faceM2 * mat.thicknessMm / 1000.0 - unitsNet * mat.unitVolumeM3()), Mix.M_1_4, basis + " mortar");
        b.work(WorkItem.MASONRY, faceM2);
        b.work(WorkItem.PLASTER, faceM2 * 2);
    }

    // Plumbing

    private static void plumbing(Builder b, FloorPlan fp, PlanMetrics pm) {
        Map<DetectedRoom, int[]> counts = new IdentityHashMap<>(); // {wc, basin, shower, bath, sink}
        Map<DetectedRoom, Room> roomOf = new IdentityHashMap<>();
        for (Room r : fp.rooms) {
            DetectedRoom d = fp.shapeOf(r);
            if (d != null) { roomOf.put(d, r); counts.put(d, new int[5]); }
        }
        int looseWc = 0, looseShower = 0, looseBath = 0, looseSink = 0;
        for (Furniture fu : fp.furniture) {
            if (!fu.furnitureType.plumbingFixture) continue;
            DetectedRoom d = pm.topology.roomContaining(fu.x, fu.y);
            Room r = d == null ? null : roomOf.get(d);
            boolean washRoom = r != null && (r.roomType == RoomType.BATHROOM || r.roomType == RoomType.TOILET);
            int[] c = d == null ? null : counts.get(d);
            switch (fu.furnitureType) {
                case TOILET: if (c != null) c[0]++; else looseWc++; break;
                case SHOWER: if (c != null) c[2]++; else looseShower++; break;
                case BATHTUB: if (c != null) c[3]++; else looseBath++; break;
                case SINK:
                    if (c != null) { if (washRoom) c[1]++; else c[4]++; } else looseSink++;
                    break;
                default: break;
            }
        }
        int wc = looseWc, basin = 0, shower = looseShower, bath = looseBath, sink = looseSink, washing = 0;
        for (Map.Entry<DetectedRoom, Room> e : roomOf.entrySet()) {
            int[] c = counts.get(e.getKey());
            RoomType t = e.getValue().roomType;
            boolean any = c[0] + c[1] + c[2] + c[3] + c[4] > 0;
            if (!any) { // no fixtures drawn: assume the usual set for the room type
                switch (t) {
                    case TOILET: c[0] = 1; c[1] = 1; break;
                    case BATHROOM: c[2] = 1; c[1] = 1; break;
                    case KITCHEN: c[4] = 1; break;
                    case LAUNDRY: c[4] = 1; washing++; break;
                    default: break;
                }
            }
            wc += c[0]; basin += c[1]; shower += c[2]; bath += c[3]; sink += c[4];
        }
        int fixtures = wc + basin + shower + bath + sink;
        if (fixtures <= 0) return;
        b.line(CostCategory.PLUMBING, Item.WC_SET, wc, 0, "water closets", false);
        b.line(CostCategory.PLUMBING, Item.BASIN_SET, basin, 0, "wash basins", false);
        b.line(CostCategory.PLUMBING, Item.SHOWER_SET, shower, 0, "showers", false);
        b.line(CostCategory.PLUMBING, Item.BATHTUB_SET, bath, 0, "bathtubs", false);
        b.line(CostCategory.PLUMBING, Item.SINK_UNIT, sink, 0, "kitchen/laundry sinks", false);
        b.line(CostCategory.PLUMBING, Item.PIPE_WATER_M, fixtures * PIPE_WATER_M_PER_FIXTURE + washing * 4, 0.05, "7 m supply per fixture", true);
        b.line(CostCategory.PLUMBING, Item.PIPE_SOIL_M, wc * PIPE_SOIL_M_PER_WC, 0.05, "4 m soil pipe per WC", true);
        b.line(CostCategory.PLUMBING, Item.PIPE_WASTE_M, (fixtures - wc) * PIPE_WASTE_M_PER_FIXTURE, 0.05, "3 m waste pipe per other fixture", true);
        b.line(CostCategory.PLUMBING, Item.INSPECTION_CHAMBER, Math.max(2, Math.ceil(wc / 2.0) + 1), 0, "inspection chambers (allowance)", true);
        b.work(WorkItem.PLUMBING_POINTS, fixtures + washing);
    }


    // Electrical


    private static void electrical(Builder b, FloorPlan fp, PlanMetrics pm, Floor f) {
        int lights = 0, sockets = 0, cooker = 0, heater = 0;
        for (Room r : fp.rooms) {
            DetectedRoom d = fp.shapeOf(r);
            if (d == null) continue;
            lights += (int) Math.max(1, Math.ceil(d.netAreaM2 / AREA_PER_LIGHT_POINT_M2));
            sockets += r.roomType.sockets;
            if (r.roomType == RoomType.KITCHEN) cooker++;
            if (r.roomType == RoomType.BATHROOM) heater++;
        }
        int externalDoors = 0;
        for (Door d : fp.doors) {
            Wall w = fp.wallsById.get(d.wallId);
            if (d.attached && w != null && w.kind == WallKind.EXTERNAL) externalDoors++;
        }
        lights += externalDoors;
        if (lights + sockets <= 0) return;
        b.line(CostCategory.ELECTRICAL, Item.ELEC_LIGHT_POINT, lights, 0, "1 per 4 m2 of room + 1 per external door", false);
        b.line(CostCategory.ELECTRICAL, Item.LIGHT_FITTING, lights, 0, "one fitting per light point", false);
        b.line(CostCategory.ELECTRICAL, Item.ELEC_SOCKET_POINT, sockets, 0, "sockets by room type", false);
        b.line(CostCategory.ELECTRICAL, Item.ELEC_COOKER_POINT, cooker, 0, "kitchens", false);
        b.line(CostCategory.ELECTRICAL, Item.ELEC_HEATER_POINT, heater, 0, "bathrooms", false);
        b.work(WorkItem.ELECTRICAL_POINTS, lights + sockets + cooker + heater);
    }

    
    // Whole-building services


    private static void services(Builder b, int floors) {
        b.line(CostCategory.ELECTRICAL, Item.ELEC_DB, floors, 0, "one board per floor", false);
        b.line(CostCategory.ELECTRICAL, Item.ELEC_EARTHING, 1, 0, "earthing", false);
        if (b.s.sanitation == SanitationSystem.SEPTIC_TANK) {
            b.line(CostCategory.PLUMBING, Item.SEPTIC_SYSTEM, 1, 0, "septic tank and soak pit", true);
        } else if (b.s.sanitation == SanitationSystem.SEWER_CONNECTION) {
            b.line(CostCategory.PLUMBING, Item.SEWER_CONNECTION, 1, 0, "sewer connection", true);
        }
        if (b.s.waterTankLitres > 0) {
            b.line(CostCategory.PLUMBING, Item.WATER_TANK_L, b.s.waterTankLitres, 0, "storage tank", false);
            b.line(CostCategory.PLUMBING, Item.TANK_STAND, 1, 0, "tank stand", true);
        }
    }

    
    // Accumulator
    

    private static final class Builder {
        final EstimateSettings s;
        final QuantitySchedule out = new QuantitySchedule();
        private final Map<Long, MaterialLine> lines = new java.util.LinkedHashMap<>();

        Builder(EstimateSettings s) { this.s = s; }

        void work(WorkItem w, double amount) {
            if (amount <= 0) return;
            out.work.merge(w, amount, Double::sum);
        }

        MaterialLine line(CostCategory cat, Item item, double net, double waste, String basis, boolean allowance) {
            if (net <= 1e-9) return null;
            long key = (long) cat.ordinal() * 1000 + item.ordinal();
            MaterialLine l = lines.get(key);
            if (l == null) {
                l = new MaterialLine(cat, item);
                lines.put(key, l);
            }
            l.netQty += net;
            l.qty += net * (1 + waste);
            l.allowance |= allowance;
            l.addBasis(basis);
            return l;
        }

        /** Concrete: cement, sand, ballast, steel, binding wire, formwork + work quantities. */
        void concrete(CostCategory cat, double m3, Mix mix, double steelKgPerM3, double formworkM2, String basis) {
            if (m3 <= 1e-9) return;
            double wst = s.wastage.concrete;
            line(cat, Item.CEMENT_50KG, m3 * mix.bagsPerM3(), wst, basis + " " + mix.label, false);
            line(cat, Item.SAND_T, m3 * mix.sandTonnesPerM3(), wst, basis + " " + mix.label, false);
            line(cat, Item.BALLAST_T, m3 * mix.ballastTonnesPerM3(), wst, basis + " " + mix.label, false);
            work(WorkItem.CONCRETE, m3);
            if (steelKgPerM3 > 0) {
                double kg = m3 * steelKgPerM3;
                line(cat, Item.STEEL_KG, kg, s.wastage.steel, basis, false);
                line(cat, Item.BINDING_WIRE_KG, kg * 0.01, 0, basis, false);
                work(WorkItem.STEEL, kg);
            }
            if (formworkM2 > 0) {
                line(cat, Item.FORMWORK_M2, formworkM2, 0, basis, false);
                work(WorkItem.FORMWORK, formworkM2);
            }
        }

        /** Mortar / plaster / screed by finished (wet) volume. */
        void mortar(CostCategory cat, double wetM3, Mix mix, String basis) {
            if (wetM3 <= 1e-9) return;
            double wst = s.wastage.mortar;
            line(cat, Item.CEMENT_50KG, wetM3 * mix.bagsPerM3(), wst, basis + " " + mix.label, false);
            line(cat, Item.SAND_T, wetM3 * mix.sandTonnesPerM3(), wst, basis + " " + mix.label, false);
        }

        QuantitySchedule finish() {
            for (MaterialLine l : lines.values()) {
                if (l.item.wholeUnits()) l.qty = Math.ceil(l.qty - 1e-9);
                out.lines.add(l);
            }
            Collections.sort(out.lines, Comparator.<MaterialLine>comparingInt(l -> l.category.ordinal())
                    .thenComparingInt(l -> l.item.ordinal()));
            return out;
        }
    }
}
