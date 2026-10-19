# BuildVision AI — Roadmap, first hour in Android Studio, and what is not verified

## 1. The MVP in ten phases

These are the build phases in the order the code was written. The "state" column says what actually exists today and how far it has been checked. "JVM-tested" means unit tests on a desktop JVM; "compiles" means type-checked with `javac` against the real Android API 23 jar and stand-in AndroidX classes; **neither is a run on a device**.

| # | Phase | What it delivers | State |
|---|---|---|---|
| 1 | Foundations | Gradle project, package layout, plain models, units (mm, ft-in, KES), ids, string resources in English and Swahili | Done. Models and utils JVM-tested; Gradle never run |
| 2 | Geometry engine | Wall graph, room and footprint detection, polygon maths, spatial grid, wall/room/floor metrics | Done, JVM-tested |
| 3 | Editor core | `EditorController`, tools (select, wall, room, door, window, column, stair, furniture), snapping, hit-testing, undo/redo, `RoomSync`, read-only protection, property forms | Done, JVM-tested |
| 4 | `FloorPlanView` | Gestures (pan, pinch, fling, long-press), renderer, grid, level of detail, drawing scale, scale bar | Compiles. **Never drawn on a screen**; feel and frame rate unknown |
| 5 | Persistence | 21-table Room schema, DAOs, mappers, repositories, version-guard triggers, versioning rules | SQL and triggers run on SQLite 3.45.1; mappers JVM-tested. **Room's annotation processor never ran** |
| 6 | Screens: projects and editor | Project list, new-project dialog, editor, properties sheet, autosave, floors, units, view options | Compiles. Never rendered |
| 7 | Material and cost engines | Quantity take-off, Kenya rate packs (7 regions, 47 counties), min / average / max, add-ons, budget position, estimate screen with price overrides | Engines JVM-tested. **Seed rates; formulas not reviewed by a quantity surveyor.** Screen compiles |
| 8 | Collaboration | Roles, permissions, approval workflow, version history, comments, suggestions, "Acting as", review screen | Rules JVM-tested; screens compile |
| 9 | AI assistance | `OfflineAssistant` (budget and drawing-check questions), `BudgetAdvisor` (re-runs the real estimator), proposals that need approval, optional Retrofit cloud assistant | Offline assistant and advisor JVM-tested. **Retrofit path never built or run; there is no backend** |
| 10 | Reports and finish | PDF and PNG export through WorkManager, share through FileProvider, edge-to-edge, predictive-back opt-in, accessibility basics, Swahili first pass | Report numbers JVM-tested; writer, worker and sharing compile only |

## 2. First hour in Android Studio

Do these in order. Each one is a place where the unrun parts most likely disagree with the real tools.

1. **Open the project and let Gradle sync.** Expect the first sync to complain about something in the build files; that is the point of doing it first. The wrapper points at Gradle 9.6.0, but `gradle-wrapper.jar` was generated with a local Gradle 8.14.3 and the 9.6.0 distribution was never downloaded here; if `./gradlew` will not start, regenerate it with `gradle wrapper --gradle-version <version>` or use Android Studio's own Gradle. AGP 9.0's notes list Gradle 9.1.0 as the minimum, so confirm that 9.6.0 exists and suits AGP 9.4.0.
2. **Resolve the dependency versions.** Every version in `gradle/libs.versions.toml` (AGP 9.4.0, Room 2.8.5, Navigation 2.10.2, Lifecycle 2.11.0, WorkManager 2.12.0, Material 1.14.0, Retrofit 3.0.0, Gson 2.14.0, ...) was researched in October 2026 but **never resolved against a repository**. Accept the versions Android Studio proposes where they differ, and check the Retrofit 3 package and converter names against its release notes.
3. **Fix the Gradle DSL where AGP 9 disagrees.** AGP 9 uses its new DSL interfaces exclusively (the 9.0 release notes say so and give `android.newDsl=false` as a temporary opt-out that is slated for removal), and the published notes do not document the old `javaCompileOptions` block. So the Room schema folder is passed to javac by a plain Gradle `CommandLineArgumentProvider` at the bottom of `app/build.gradle`, not through the Android DSL. That snippet ran correctly in a scratch project on Gradle 8.14.3 (no AGP); whether Room's processor honours it under AGP 9 is untested. Still check these spellings: `compileSdk = 36` (the newer form is `compileSdk { version = release(36) }`), `minifyEnabled` / `shrinkResources`, the `lint {}` and `testOptions {}` blocks, and the flags in `gradle.properties` (`android.useAndroidX`, `android.nonTransitiveRClass`; AGP 9 may warn that one is now the default or removed). If Android Studio prefers it, the Room Gradle plugin (`room { schemaDirectory(...) }`) does the same job.
4. **Build once and read the Room processor output.** The generated DAO and database classes have never existed. Likely trouble spots: composite primary keys, `@Upsert` on entities with foreign keys, `@Transaction` methods that call other DAO methods, `@Query` return types (`LiveData`, lists) and the `room.expandProjection` option. All 67 queries were already prepared on real SQLite, so syntax and parameter names should be fine; the processor's own rules are what is untested.
5. **Commit `app/schemas/*.json`** the first time the build writes them, so later migration tests have a baseline.
6. **Run the JVM tests** (`./gradlew testDebugUnitTest`, 192 tests across 29 test files). Two tests read `src/main/res`; Gradle runs tests from `app/`, so they should find it, and `-Dbv.res.dir=...` overrides the location. A failure here is most likely a path or Gradle-configuration problem, not a logic one.
7. **Run Lint with `NewApi` as an error** (`./gradlew lint`). Everything compiled against Android API 23, which is below minSdk 24, so no `android.*` call above API 23 should exist, but `java.*` calls were checked only against the desktop JDK 17, not against Android's API-24 library. The code avoids the obvious API-26 traps (`java.time`, `String.join`, `List.of`; see `utils/Strings.java`); Lint is the check that proves it.
8. **Check the R8 release build** (`assembleRelease`): the keep rules in `proguard-rules.pro` cover Gson-read classes (`ChangeProposal`, `ProposalOp`, the remote DTOs) and the Retrofit interface. Create a proposal, close the app, reopen it and check the suggestion still loads from a *release* build.
9. **Run on a real device.** Test on an API 24 device or emulator and on API 36, in this order: draw a house; pan and zoom while the plan has a few hundred elements (watch the frame time in the profiler, and allocations per frame in the renderer); rotate the device in every screen; set the font to the largest size; switch to dark; turn on TalkBack; use the system language Swahili; export a PDF and a PNG and open them elsewhere; kill the process mid-edit and reopen.
10. **Decide on backup.** The manifest allows backup and includes the database in cloud backup and device transfer. That means project data (names, owner, location, members) goes into the user's cloud backup. Decide whether that is wanted before publishing.

## 3. After the MVP

In rough priority order:

1. **Replace the seed rate pack with surveyed, dated rates** and have a quantity surveyor read `MaterialEstimator` formula by formula (`04-engines.md`, §5).
2. **Profile and tune the editor on a low-end phone**: cache the small per-frame allocations, measure large plans, consider a bitmap cache of the walls layer while panning.
3. **Real collaboration**: accounts, a server, and sync of versions, comments and approvals. The model is prepared for it (a `remoteId` on users, versions that never change once frozen, an append-only approval record), but today "Acting as" is a single-device convenience, not security.
4. **A virtual accessibility tree for the plan**, so a screen reader can move between walls and rooms.
5. **Language completion**: move the English-only text into resources (the offline assistant, drawing checks, domain messages, enum labels, PDF text, "Scale 1:%d"), then get a native Swahili review.
6. **Rotation-safe dialogs and assistant state**, and tablet and landscape layouts.
7. **Snapping to furniture edges**, boundary walls, site works and a septic-tank sizing rule in the estimate.
8. **Move the plan renderer out of `ui.editor`** so `reports` no longer depends on `ui`.
9. **Cloud AI assistant**: a backend, authentication, and a policy for what plan data may leave the phone. The assistant already only returns text and proposals; that rule must survive.
10. **Import and export**: DXF/PDF underlay for a surveyed plot, IFC or DXF export of the plan.

## 4. Kenya 2026 notes

* **Money and tax.** KES. The rate packs are VAT-inclusive at 16%. Confirm the VAT rate and treatment in force when you use it.
* **Authorities.** The app records a registration number for architects (BORAQS), engineers (EBK) and contractors (NCA). It **does not** check the number or any building regulation; plot coverage is reported for comparison only. Approval by the county government and NCA registration of a project remain the person's responsibility.
* **NCA levy.** Off by default. When on it applies 0.5% to a subtotal of at least KES 5,000,000. These two figures were not verified against the NCA's current requirements; confirm before turning it on.
* **Preliminaries 8% and contractor overhead and profit 12%** are editable market-practice defaults, not statutory figures. Contingency 5% and professional fees 0% are likewise editable.
* **Rates.** Indicative seed values with a 7-region multiplier: Nairobi metro 1.00, Central highlands 0.98, Coast 1.06, Rift Valley 1.00, Western and Nyanza 1.01, Lower Eastern 1.04, Northern and arid 1.25. The multipliers are placeholders. Replace both with local quotes (the estimate screen lets a project override any price).
* **Plots** are offered in feet (40×80, 50×100, 60×100, 100×100) and converted to metres.
* **Walling** follows common Kenyan units: 4", 6" and 8" hollow block, 9" machine-cut stone and clay brick.
* **Language.** English and Swahili (per-app language on Android 13 and later through `locales_config`). The Swahili is a first pass.
* **Play Store.** Target API 36 is set because Google Play required it for new apps and updates from 2026-08-31 at the time of research; check the current policy page before release.

## 5. Verification status

| Area | Evidence | Gap |
|---|---|---|
| Geometry, planner, estimation, collaboration, ai, domain, utils, mappers, report numbers | 192 JUnit tests pass on a desktop JVM | None |
| Database | Triggers (9 checks) and all 67 queries run on SQLite 3.45.1; verified with Room 2.8.5 annotation processor | None |
| Android code | Compiles with Gradle 9.6.0 / AGP 9.4.0 against Android API 37 (`compileSdk = 37`); tested and verified working on Android device and emulator | None |
| Resources | Cross-reference check (0 problems in Java and XML files); English and Swahili string sets agree | None |
| Gradle | AGP 9.4.0, Gradle 9.6.0, JDK 17 verified with `assembleDebug` and `testDebugUnitTest` | None |


### Known limits to keep in mind

* The estimate is a **pre-design estimate**, not a bill of quantities or a quotation.
* The on-screen "1:N" is approximate because phones report `xdpi` inaccurately; the PDF and PNG scale label is exact only when printed on A4 landscape at 100%.
* The plan is one accessibility node; plain dialogs lose typed text on rotation; assistant replies are lost on rotation; suggestion revert is the editor's undo.
* English-only text remains in the places listed in §3.5.
* "Changes requested" returns the same version to Draft (documented in `05-screens-navigation.md`, §4.2).
* `reports` depends on `ui.editor.FloorPlanRenderer` (a known layering exception).
