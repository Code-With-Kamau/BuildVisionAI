# BuildVision AI — Geometry, material and cost engines

Three deterministic, Android-free stages turn a drawing into money:

```
PlanDocument ──► GeometryEngine ──► PlanMetrics ──► MaterialEstimator ──► QuantitySchedule
 (walls, rooms,   (topology,         (per wall,      (formulas, written     (material lines +
  openings,        areas, lengths)     room, floor)    out, with settings)    labour work items)
  stairs...)                                                                       │
                                                                                   ▼
                  RatePack (region + finish + project quotes) ──► CostEstimator ──► CostEstimate
                                                                  (min / avg / max, categories,
                                                                   add-ons, benchmark)
```

`EstimateRunner.run(plan, settings, ratePack)` is the single entry point used by the estimate screen, the assistant and the report worker, so all three always agree.

**No AI and no randomness anywhere in these stages.** The same drawing, settings and rates always give the same numbers; every figure on a result traces back to a quantity, a rate and a setting. An AI service may be used afterwards only to phrase a result; it cannot change a number.

> Verification status: all three stages are pure Java and covered by JVM tests (quantities checked against hand calculations on small plans; a full reference house). The **rates are seed values, not surveyed market data** (see §3.2), and the **formulas have not been reviewed by a quantity surveyor**. Treat the output as a pre-design estimate.

## 1. Geometry engine (`geometry`)

**Units:** millimetres in the model, metres and m² in results.

### 1.1 Walls to rooms (`WallGraph`)

Walls are drawn as centre lines with a thickness. To find rooms the engine builds a planar graph:

1. split every wall wherever another wall crosses it or ends on it (a T-junction), *virtually*: walls are never modified, so a wall keeps its identity for doors, windows and estimation;
2. merge nearby points into vertices;
3. drop dangling edges (walls that are part of no loop);
4. walk the faces with the half-edge rule "next = clockwise neighbour of the twin". Every bounded face comes out with positive area and each connected component has exactly one outer face with negative area.

A closed loop becomes a **room**; the outer face of a connected group becomes its **footprint** (outline including wall thickness). `PlanTopology` holds both, is immutable once built and is cached once per plan revision.

**Documented limitations** (not silently wrong): walls that overlap collinearly are not merged, and an island (a separate loop fully inside a room) is not subtracted from that room.

`RoomSync` keeps room records consistent after every edit: a room follows its loop through an **anchor point**, so it keeps its name and type when surrounding walls move or when a wall splits it (the half holding the anchor keeps it); a new closed loop becomes "Room n"; a broken loop removes its room, and undo brings it back with its name. These changes go into the same transaction as the wall edit, so one undo reverts both.

### 1.2 Measurements (`GeometryEngine`, `PlanMetrics`)

* Openings (doors, windows) are **attached** to the wall they sit on by their offset along it; an opening that does not fit on a long-enough wall is reported as unattached and is **not counted** (a warning says so).
* Wall quantities use the **centre-line method**: length of the axis × height, minus attached openings. It slightly over-counts at corners; that is the usual quick take-off convention and is accepted for a pre-design estimate (stated in the estimate's notes).
* Per room: net area, gross area, perimeter, width and length of the oriented bounding box (`PolygonOps`: per-edge offsetting, simplicity test, oriented extents). Per floor: net floor area, footprint area, external perimeter, wall lengths and face areas.
* `SpatialGrid` (uniform-grid index) serves the editor's hit-testing, snapping and viewport culling.

### 1.3 Drawing checks (`domain.PlanChecker`)

Objective problems in the drawing only: room with no door, habitable room with no window, unattached opening, walls that belong to no room (info), stair on the top floor (info), footprint exceeding the plot (error) and a plot-coverage figure (info). It deliberately **does not check building regulations**: those vary by county and change, so it reports coverage for the person to compare with what their approving authority requires.

## 2. Material engine (`estimation.MaterialEstimator`)

Input: `PlanDocument` + `EstimateSettings`. Output: `QuantitySchedule` (material lines with the basis of each quantity, labour work items by measure, notes, warnings, gross and net floor area).

**Measured:** strip footings, foundation masonry, column pads, hardcore (×1.20 compaction), ground slab (damp-proof membrane, termite treatment, A142 mesh with 10% lap, concrete), walls (units and mortar, by wall material), lintels (opening width + 200 mm bearing each side, 150 mm deep), ring beams (150 mm deep on load-bearing walls), columns, suspended slabs (stair voids deducted), stairs, roof (G28 sheets 0.85 m cover × 3.0 m with 0.15 m end lap, or concrete tiles at 10/m², or flat slab; ridge, fascia, gutters, downpipes, waterproofing), ceilings (gypsum 2.4 × 1.2 m), floor and wall tiles with adhesive and grout, skirting, plaster, paint (interior 11 m²/litre/coat, exterior 8 m²/litre/coat, undercoat), doors, windows, burglar-proof grills (option), handrails, plumbing fixtures and pipe runs by fixture count, electrical points (one light point per 4 m²).

**Only allowed for, and flagged as allowances:** roof timber (0.028 m³ per m² of roof), septic tank / sewer connection, water-tank stand.

**Ignored (stated in the notes):** site clearing, boundary wall, landscaping, external drainage runs, fittings and furniture.

### 2.1 How concrete and mortar are measured

`Mix` converts a mix ratio and a dry-volume factor (1.54 for concrete, 1.33 for mortar) to cement bags, sand tonnes and ballast tonnes per m³. Mixes used: 1:3:6 (blinding/strip footings), 1:2:4 (slabs, lintels, columns), 1:1.5:3 (beams), mortar 1:3 and 1:4. A 50 kg bag is 0.0347 m³. Steel is by kg per m³ of concrete (70 footings, 80 pads, 110 beams, 150 columns, 110 slabs, 120 lintels, 110 stairs) and formwork by contact area.

### 2.2 Walling units (Kenya)

`WallMaterial`: hollow block 4" (100 mm, 400×200), 6" (150 mm), 8" (200 mm), machine-cut stone 9" (225 mm, 450×225), clay brick half-brick (112 mm) and one-brick (225 mm) walls. Units per m² come from the unit size and a mortar joint (setting, default 10 mm), plus wastage.

### 2.3 Settings (`EstimateSettings`)

The assumptions that belong to the design and are frozen with the version: finish level (Basic / Standard / Premium), roof type (gabled or hipped, iron sheet or concrete tile; flat slab), pitch (default 20°), overhang, foundation depth, footing thickness and projection, plinth height, slab and screed thicknesses, hardcore, plaster, mortar joint, pad size, window grills, ceilings, sanitation system, water-tank litres, add-on percentages, NCA levy flag, and **nine wastage factors** (defaults: masonry units 5%, concrete 5%, mortar 25%, steel 5%, tiles 10%, paint 5%, roof sheets 5%, boards 10%, timber 10%). Every field has validation limits (`SettingsEditor`) so a typed value cannot produce a nonsensical quantity.

## 3. Cost engine (`estimation.CostEstimator`)

Pure multiplication and addition over the `QuantitySchedule`.

### 3.1 Min / average / max

Every rate is a triple. The built-in packs use `Rate.around(avg)` = **−12% / typical / +15%**, so a result carries the price risk instead of one falsely precise number:

* `CostLine`: quantity × rate (min, avg, max), flagged if the quantity is an allowance;
* `CategoryCost` per category: **Foundation, Walling & superstructure, Roofing, Finishes doors & windows, Plumbing & drainage, Electrical, Labour**;
* subtotal, then add-ons, then total, each in min / avg / max;
* cost per m² of gross floor area, checked against an indicative benchmark range (KES 35,000 – 122,000 per m², used only to flag a result that looks implausibly low or high).

Labour is priced from work-item quantities (excavation, hardcore, concrete, formwork, steel, masonry, plaster, screed, tiling, painting, roofing, ceiling, doors and windows, plumbing points, electrical points), not as a percentage of materials.

### 3.2 Rate packs (`KenyaRatePack`, `Region`, `Counties`)

* 47 counties map to 7 price regions with a multiplier on every rate: Nairobi metro 1.00, Central highlands 0.98, Coast 1.06, Rift Valley 1.00, Western & Nyanza 1.01, Lower Eastern 1.04, Northern & arid 1.25.
* Pack id `KE-<REGION>-2026`, dated "2026-10 (seed)", prices **VAT-inclusive**, KES.
* **These are indicative seed values, not surveyed market data.** They were set so that a simple masonry house lands inside published per-m² ranges. The region multipliers are placeholders for the same reason. The disclaimer is shown next to every estimate and printed on every report. Replace them with current local quotes.
* Finish level scales only finish-sensitive items: Basic ×0.70, Standard ×1.00, Premium ×1.80.
* A project can override any item with its own quote (min / avg / max), stored in `rate_overrides`; the estimate screen marks overridden items and validates input (`RateInput`: positive, min ≤ avg ≤ max, sane limits). Overrides are allowed on any version except Locked and never touch the design.

### 3.3 Add-ons (`StatutoryItem`)

Percentages of the construction subtotal, each editable in the estimate screen:

| Add-on | Default | Note |
|---|---|---|
| Preliminaries | 8% | site set-up, supervision, insurance, temporary services |
| Contractor overhead and profit | 12% | set to 0 if the owner manages the fundis |
| Contingency | 5% | unforeseen work and price movement |
| Professional fees | 0% | architect, engineer, QS |
| NCA levy | off | 0.5% only when the subtotal is at least KES 5,000,000; **verify the current rate and threshold with the National Construction Authority** before relying on it |

The 8% and 12% defaults are editable assumptions, not statutory figures. The VAT component (16%) is shown as "included in the total", not added.

### 3.4 Budget position and the budget assistant

`BudgetPosition` compares the owner's budget with the min / average / max totals and says one of three things in plain words: it fits even at high prices, it fits only at typical prices, or it is over. `BudgetAdvisor` finds ways to come within budget (basic finishes, iron sheets instead of tiles, ...) and computes each saving by **re-running the real estimator** with the changed setting, so the saving is exact for the model; combined options are re-estimated because savings are not additive. Each option comes with a proposal that a person must accept on a draft; the advisor changes nothing itself.

## 4. Reference numbers (the test house)

A three-bedroom bungalow in Nairobi used throughout the tests: gross floor area **124.4 m²**; total **KES 5,063,449** average, 4,455,835 minimum, 5,822,966 maximum; **40,690 KES per m²** (inside the benchmark range). These come from seed prices; they demonstrate that the pipeline is consistent, not that a house costs that much.

## 5. What would make the engines trustworthy

1. A quantity surveyor reads `MaterialEstimator` formula by formula (they are written out with their constants in one block) and corrects the constants.
2. Replace the seed rate pack with a maintained one (supplier quotes per region, dated), and show the date prominently.
3. Reconcile with two or three real bills of quantities for houses of known cost and adjust the benchmark range.
4. Add the items currently ignored if the product needs them (boundary wall, external works, septic tank sizing).
