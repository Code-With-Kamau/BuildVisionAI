# BuildVision AI — Architecture

BuildVision AI is an Android floor-plan editor that turns a drawing into quantities and a Kenya-2026 cost estimate, with versions, review and approval around the design. This document explains how the code is organised and why. The other five documents go deeper on the database, the editor view, the engines, the screens and the roadmap.

## 1. Status: verified and tested

| Layer | Verification status |
|---|---|
| Pure Java: `models`, `geometry`, `planner`, `estimation`, `collaboration`, `ai`, `domain`, `utils`, `reports/data`, `data/mapper`, DTOs | **192 JUnit tests pass** on a desktop JVM |
| Database: triggers and all 67 `@Query` statements | Verified on Room 2.8.5 with Room annotation processor. DAOs and generated `_Impl` classes compile cleanly, and schema exports to `app/schemas/` |
| Android code: Fragments, ViewModels, adapters, custom views, report writer, worker | Compiled with Gradle 9.6.0 / AGP 9.4.0 against **Android API 37** (`compileSdk = 37`). Verified working on Android device and emulator |
| Resources | Cross-reference check verified (0 problems in Java and XML files): every `R.*` use exists, ids match bindings, English and Swahili string sets agree |
| Gradle | Configured with AGP 9.4.0, Gradle 9.6.0, Java 17, and verified with `assembleDebug` and `testDebugUnitTest` |

Summary: The pure Java logic, Room database layer, Android UI/Fragments, custom views, and reports are fully verified, unit-tested, and confirmed working on device.

## 2. Targets and libraries

| Item | Value |
|---|---|
| Language | Java 17 only (no Kotlin app code, no Jetpack Compose) |
| compileSdk / targetSdk / minSdk | 36 / 36 / 24 |
| Build | AGP 9.4.0, Gradle 9.6.0, JDK 17 |
| UI | XML layouts, ViewBinding, custom `View`s, Material Components 1.14.0 (Material 3 theme), Navigation 2.10.2, RecyclerView 1.4.0 |
| Architecture components | Lifecycle 2.11.0 (ViewModel, LiveData), Room 2.8.5, WorkManager 2.12.0 |
| Network (optional) | Retrofit 3.0.0 + Gson 2.14.0; off unless `AI_BASE_URL` is set |
| Test | JUnit 4.13.2 |

AGP 9 enforces edge-to-edge and predictive back at target 36; the single `MainActivity` calls `EdgeToEdge.enable` and applies system-bar, cutout and keyboard insets once, so no screen handles insets itself.

## 3. Package map and dependency rule

```
                          ui  (Fragments, ViewModels, adapters, FloorPlanView)
                         /  |  \
                        /   |   \
              reports ─┘    |    └─ data.remote (Retrofit, cloud assistant)
                  │         |
                  ▼         ▼
             repository  ──────────►  database (Room) + data.mapper
                  │
                  ▼
   ┌──────────────────────────────────────────────────────────────────────┐
   │ Pure Java, no android.* imports, JVM-tested:                         │
   │ planner (EditorController, tools, snapping, undo)  estimation         │
   │ geometry   collaboration (permissions, workflow)   ai   domain        │
   │ models     utils                                                      │
   └──────────────────────────────────────────────────────────────────────┘
```

Rules the code follows:

1. **Arrows point down.** Pure packages never import `android.*` or `androidx.*` (apart from the annotation-only `androidx.annotation` and Room annotations in `database`).
2. **The editor's brain is not in the view.** `FloorPlanView` forwards raw touch events to `planner.EditorController` and draws what it exposes. Tools, snapping, hit-testing, undo/redo, room detection and the locked-version protection are all JVM-tested.
3. **Screens only talk to repositories and the pure engines.** They never see a DAO or an entity; `data.mapper` converts between rows and models.
4. **Known layering exception:** `reports` imports `ui.editor.FloorPlanRenderer` so a printed plan is drawn by the same code as the screen. A future refactor can move the renderer to its own package.

| Package | Responsibility |
|---|---|
| `models` | Plain domain objects: `Project`, `ProjectVersion`, `Floor`, `Wall`, `Room`, `Door`, `Window`, `Column`, `Stair`, `Furniture`, `Member`, `Approval`, `Comment`, `Suggestion`, enums (`WorkflowState`, `UserRole`, `WallMaterial`, ...) |
| `geometry` | Wall graph, room detection, footprint, polygon maths, spatial index, wall/room/plan metrics |
| `planner` | `PlanDocument`, `EditorController`, tools (select, wall, room, opening, place), `SnapEngine`, `HitTester`, `CommandStack`, `RoomSync`, `ScaleModel`, `ViewTransform`, render helpers (`Lod`, `GridLayout`, `WallJoins`, `PlanPalette`), property forms (`ElementFields`) |
| `estimation` | `MaterialEstimator`, `CostEstimator`, `KenyaRatePack`, `Counties`, `EstimateSettings`, `EstimateRunner`, budget position, rate input validation |
| `collaboration` | `PermissionPolicy`, `ApprovalWorkflow`, `VersionPolicy`, `ReviewGuide`, `ChangeProposal` and its codec/applier/describer |
| `ai` | `AiAssistant` contract, `OfflineAssistant`, `BudgetAdvisor`, `PlanSummarizer` |
| `domain` | `PlanChecker` (drawing sanity), `ProjectForm` (validation), `VersionDiff` |
| `database` | Room entities, DAOs, `AppDatabase`, `VersionGuard` triggers |
| `repository` | `ProjectRepository`, `CollaborationRepository`, `AppExecutors`, `RepoCallback` |
| `data.remote` | Retrofit service and DTOs for the optional cloud assistant |
| `reports` | `ReportBuilder` (numbers), `PdfReportWriter`, `PlanImageExporter`, `ExportReportWorker` |
| `ui` | Single activity, project list, editor, estimate, versions/review/discussion/team, common helpers |
| `utils` | Units and formatters (mm/ft-in, KES), `PlotPresets` (ft-based Kenyan plots), ids, `Debouncer` |

## 4. Runtime composition

`BuildVisionApp` creates one `AppContainer` that wires everything by hand: `AppExecutors`, the Room database, `ProjectRepository`, `CollaborationRepository` and `LocalProfile`. A DI framework would add build time and nothing else at this size. ViewModel factories (each ViewModel has a nested `Factory implements ViewModelProvider.Factory`) take the container and pick what they need.

**Threading.** `AppExecutors` is one background thread for all database work, so writes are naturally ordered. A repository call is `run(Callable, RepoCallback)`: the work runs on the background thread and the callback arrives on the main thread. Room `LiveData` queries drive lists. Exports run in WorkManager, reading the database themselves.

**One-shot messages.** `Event<T>` wraps a snackbar text or a navigation request so it is consumed once, not replayed after rotation.

## 5. Principles that shaped the design

1. **Numbers come from deterministic code, never from AI.** Geometry, quantities and cost are plain arithmetic: the same drawing always gives the same schedule, which is what makes an estimate auditable by a quantity surveyor.
2. **The AI only assists.** The assistant gets a text summary and a copy of the plan; it returns text and *proposals*. A proposal becomes a stored suggestion that the owner or architect must accept on a draft; accepting applies it as one undo step. The assistant is never given the editor or the document, so it cannot change a plan. With no backend configured the on-device `OfflineAssistant` answers (budget and drawing-check questions) and nothing leaves the phone.
3. **Versions are never overwritten.** Only a Draft is editable. Anything else is copied into a new draft; restoring an old version also creates a new one, so history only grows. The rule is enforced three times: `VersionPolicy`/`PermissionPolicy` in the domain, the repository (`savePlanSync` refuses non-drafts), and SQLite triggers as the last line of defence.
4. **Real relational storage.** Rooms, walls, doors, windows, columns, stairs, furniture, estimates, comments, suggestions and approvals are rows with foreign keys, not one JSON blob. Rooms reference their walls through `room_walls`.
5. **Units.** The plan is in millimetres with y pointing down; areas are in m²; money is KES in `double`. The UI can show metric or feet-and-inches.
6. **Single-device collaboration.** Roles and permissions are real and tested, but identity is local: "Acting as" lets one device switch between members (see `05-screens-navigation.md`). That is a convenience for review workflows on one phone, not security. Comments and approvals point only at the version, so they survive a member being removed.
7. **Kenya 2026.** KES, VAT-inclusive rates, 47 counties mapped to 7 price regions, plots in feet (40×80, 50×100, 60×100, 100×100), Kenyan walling units (4", 6", 8" hollow block, 9" machine-cut stone, clay brick), G28 iron sheets, NCA/BORAQS/EBK role regulators, English and Swahili UI.

## 6. Data flow of an edit

```
finger ──► FloorPlanView ──► EditorController ──► active Tool
                                  │  (snap, hit-test, preview)
                                  ▼
                           EditTransaction  (+ RoomSync derived room changes)
                                  │              ▲ one undo step
                                  ▼              │
                        PlanDocument updated   CommandStack
                                  │
          listener.onDocumentChanged ──► EditorViewModel
                                  │  Debouncer 1200 ms, edit-generation counter
                                  ▼
          ProjectRepository.savePlanSync (draft only, one transaction:
          replace plan rows + upsert settings + touch project)
                                  │
                                  ▼
          SQLite triggers refuse the write if the version is not a draft
```

The edit-generation counter makes sure a save that finishes after a newer edit does not mark the document clean. `EditorFragment` flushes the pending save when it leaves.

## 7. Data flow of an estimate

`EstimateRunner.run(plan, settings, ratePack)` = `MaterialEstimator.estimate` → `CostEstimator.estimate`. The estimate screen, the assistant and the report worker all call it, so they cannot disagree. The rate pack is the built-in pack of the project's county region plus the project's own price overrides (`rate_overrides`), which are allowed on any version except Locked. Details in `04-engines.md`.

## 8. Test strategy

192 tests, all on the JVM:

* geometry: wall graph, rooms, footprint, T-junctions, polygons;
* planner: tools, snapping, undo/redo, room sync, locked-version protection, view maths (including pan/zoom kept across a rebuilt view), render maths (LOD, grid, scale);
* estimation: quantities against hand calculations, cost categories, min/average/max, add-ons, NCA threshold, rate input validation, settings editing;
* collaboration: permission matrix by role and state, workflow transitions, version rules, proposals, `ReviewGuide`;
* mappers: model ⇄ row round trips; the report numbers;
* resources: theme contrast (WCAG 4.5:1 text, 3:1 graphics, light and dark) read from the real `colors.xml`; the assistant's quick-question chips route correctly in English and Swahili.

The reference house used throughout the tests (a three-bedroom bungalow) measures a gross 124.4 m² and prices at **KES 5,063,449 average** (min 4,455,835, max 5,822,966), 40,690 KES/m². Those are seed prices, not market data.

Performance note: rebuilding the topology of a 170-element plan took about 2 ms on a desktop JVM. That says the algorithm is cheap; it says nothing about a low-end phone, which was never measured.
