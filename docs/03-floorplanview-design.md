# BuildVision AI — FloorPlanView design

`FloorPlanView` is a custom `View` that draws one floor of the plan on a `Canvas` and turns finger input into editor actions. Its design goal is that drawing stays smooth on a low-end phone and that nearly all of the editor's behaviour is testable without a device.

> Verification status: everything in `planner/**` (the editor's logic, snapping, hit-testing, undo, level-of-detail and scale maths) is covered by JVM tests. `FloorPlanView` and `FloorPlanRenderer` compile against Android API 37 and are verified working on Android devices and emulators.

## 1. Split of responsibilities

```
 MotionEvent ──► FloorPlanView ──────────────► EditorController (pure Java)
   (px)          gestures + fling + pinch         tools, snapping, hit-test,
                                                  selection, undo/redo, rooms,
       ▲                                          locked-version protection
       │ invalidate                                   │
       └────────── FloorPlanRenderer ◄── read-only ───┘
                   draws a FloorPlan snapshot
```

* **`FloorPlanView`** owns Android input: `GestureDetector`, `ScaleGestureDetector`, `OverScroller` (fling), mouse wheel zoom, accessibility description. It knows nothing about walls or rooms.
* **`EditorController`** owns every decision: which tool is active, what a tap or drag means, snapping, selection, handles, undo, the locked-version rule. It takes coordinates in **screen pixels** and converts them through `ViewTransform`.
* **`FloorPlanRenderer`** draws an immutable-per-revision `FloorPlan` snapshot and never changes the model. The same class draws the on-screen view and the printed sheets.
* The controller and its zoom/pan live in a ViewModel and outlive the view. `attach` and `detach` connect and disconnect the view on `onViewCreated`/`onDestroyView` so the controller never keeps a dead view alive.

## 2. Coordinate system and view transform

The plan is in millimetres with y pointing down. `ViewTransform` is the whole mapping:

```
screen = world × scale + (tx, ty)          scale limits: 0.004 … 3.0 px per mm
```

It is plain arithmetic with no Android types: `zoomAbout(fx, fy, factor)` keeps the plan point under the fingers fixed and clamps to the limits; `fit(rect, padding)` frames the plan; `resize(w, h)` keeps the middle of the view where it was when the view changes size. `fit` runs only the first time (the `fitted` flag), so coming back from the Estimate screen or rotating keeps the person's zoom and pan instead of resetting to "fit all".

## 3. Gesture state machine

States: `IDLE → UNDECIDED → TOOL | PAN | PINCH | IGNORE`.

| Event | Behaviour |
|---|---|
| Finger down | `UNDECIDED`; the parent is told not to intercept; any fling stops |
| First move past touch slop | `controller.onDragStart` at the **first touch point** (not where slop was crossed). If a tool or handle takes it → `TOOL` (the controller draws a preview, snaps, and applies on release); if not → `PAN` |
| Single tap | `controller.onTap` (select, place, add a wall point...) |
| Long press (still `UNDECIDED`) | `controller.onLongPress`: with the Select tool it toggles that element in the selection (multi-select without a mode switch) |
| Fling in `PAN` | `OverScroller` fling; `computeScroll` pans by the delta each frame |
| Second finger | A tool drag in progress is **cancelled** (`onDragCancel`, nothing half-applied), then `PINCH`: zoom about the focus point and pan with it |
| Pinch ends | `IGNORE` until all fingers are up, so the remaining finger cannot start a pan or a stray drag |
| `ACTION_CANCEL` | A tool drag is cancelled; back to `IDLE` |

`ScaleGestureDetector` quick-scale (double-tap and drag) and the stylus-button scale gesture are disabled because they fight drawing tools. A mouse wheel (pointer scroll events, 1.15× per notch) zooms about the pointer, which matters on ChromeOS and tablets with a mouse.

## 4. Tools and what a drag means

`ToolType`: Select, Wall, Room, Door, Window, Column, Stair, Furniture. Wall, Room and the opening/placement tools are `Tool` implementations in `planner/tool`. The Select tool's drags are move (a whole selection, with the wall ends that follow it), move one wall end point, resize by a corner, rotate, and slide a door or window along its wall; handles (`Handle`) are wall start/end, four corners and a rotate handle. A drag edits the live elements for responsiveness and is recorded as **one** undo step when the finger lifts. With the "multi-select" option on, a tap toggles elements in and out of the selection. Edits are grouped into one `EditTransaction` per user action (including the room changes `RoomSync` derives from it), so **one undo step always reverts everything the action touched**; the undo history is bounded by `CommandStack` (200 steps by default).

**Hit-testing** (`HitTester`) uses a screen-pixel tolerance so a finger can hit a thin wall at any zoom. Priority: handles of the single selected element; then free-standing items (smallest first, so a column inside a stair stays reachable); openings; walls; finally the room fill.

**Snapping** (`SnapEngine`) priority: existing wall end points; mid points; a point on a wall (T-junction); direction lock from the anchor; alignment with other end points; then the grid. The radius is 16 px (`SnapOptions.radiusPx`), converted to millimetres at the current zoom, so snapping feels the same at every zoom level.

## 5. Rendering pipeline

`FloorPlanRenderer.draw(canvas, controller, palette, density, dpi)` per frame:

1. Clip to the view and compute the visible plan rectangle (plus a margin) from `ViewTransform`.
2. Grid lines (`GridLayout`: reused buffers, major lines excluded from the minor buffer so no line is painted twice, hard cap on the number of lines).
3. Room fills for rooms in view.
4. Walls: **one fill `Path` in plan space** for all walls, rebuilt only when the plan revision changes; `WallJoins` decides which ends get a square cap so corners close exactly while free ends and T-junctions stay flat.
5. Free-standing items (columns, stairs, furniture) and openings (doors with leaf and swing, windows) from the spatial index for the visible rectangle only.
6. Selection outline, handles, tool preview, snap marker.
7. Screen-space layer: room labels and areas, wall dimensions, preview length label, scale bar. Text, handles and markers are drawn in pixels, not under the zoom matrix, so they stay crisp and constant-sized.

Shapes are drawn in plan space under the canvas matrix, so stroke widths are divided by the scale to stay a fixed number of pixels.

### Performance measures

* **Spatial index** (`SpatialGrid`): a uniform grid. The cost of a touch or a frame depends on how many items are *near*, not on how many exist.
* **Cached topology**: the editor keeps one `PlanTopology` (rooms and footprint) per plan revision; rendering, hit-testing and estimation all reuse it.
* **No per-frame object creation for the heavy things**: Paints, the wall Path and the grid buffers are reused. The only per-frame allocations are small (the visible-element list, one `Lod`, a few formatted labels). If profiling on a real low-end phone shows garbage-collection jank, caching those is the next step.
* **Level of detail** (`Lod`, all thresholds in density-scaled px so the plan looks the same on every phone at the same physical size): furniture appears when 1 m is at least 14 dp; door leaf and swing when 0.9 m is at least 18 dp; stair steps when 1 m is at least 24 dp; a room label when its short side is at least 64 dp and its area when at least 96 dp; a wall dimension when the wall is at least 64 dp long, capped at 120 labels; walls never draw thinner than 1.5 dp.
* **Rebuild measurement**: rebuilding the topology of a 170-element plan took about 2 ms on a desktop JVM. That is not a phone measurement.

## 6. Drawing scale ("1:50")

`ScaleModel` turns the view into a real architectural scale, the way a paper drawing is read.

* Standard ratios: 1:5, 1:10, 1:20, 1:25, 1:50, 1:100, 1:200, 1:500.
* `pxPerMm(dpi, ratio) = dpi / 25.4 / ratio` makes 1 mm on the screen equal `ratio` mm of plan. The toolbar's **Scale** dialog calls `setDrawingScale(ratio)`, which zooms about the centre of the view; the label shows the standard ratio nearest to the current zoom.
* The adaptive grid (`gridStepMm`: the smallest "nice" step, from 10 mm to 25 m, whose spacing is at least a minimum in px), the major lines and the scale bar (`scaleBarMm`, 1-2-5 steps) keep the picture readable at every zoom.
* **Accuracy limit:** the on-screen scale uses the device's reported `xdpi` (falling back to density dpi). Many phones report an inaccurate `xdpi`, so "1:100" on screen is approximate. It is exact only on paper (below).

### Printed sheets

`PlanImageExporter` draws one sheet per drawn floor: a title band, the plan at the **largest standard scale at which it fits** (1:5 … 1:500; otherwise whatever scale fits), a scale bar, and on non-approved versions a pale diagonal "DRAFT - NOT APPROVED" style mark. The same code draws a PNG (2100 × 1485 px bitmap, A4 landscape at about 180 dpi, about 12 MB in memory) and a vector PDF page (A4 landscape at 72 points per inch). The printed scale label is exact only when the sheet is printed on A4 landscape at 100%; scaled-to-fit printing changes the real scale, and the PDF says nothing about that.

## 7. Accessibility and input

* The view exposes a spoken summary of the floor for TalkBack ("Ground floor, 7 rooms, 124 square metres").
* **Known limit:** the plan is one accessibility node. Editing individual elements by screen reader is not supported yet; element properties are reachable through the properties sheet, which is accessible, but selecting an element needs touch. A virtual-view tree (`ExploreByTouchHelper`) is a planned improvement.
* Mouse-wheel zoom is supported. Hardware keyboard shortcuts are not implemented.

## 8. Not yet done

* A device profile (frame time while panning a 500-element plan on a 2 GB phone) and the allocation clean-up that would follow.
* Snapping to furniture edges.
* A virtual accessibility tree for the plan.
* Moving the renderer out of `ui.editor` so `reports` no longer depends on `ui`.
