# BuildVision AI — Screens, navigation and review workflow

One activity (`MainActivity`) hosts a Navigation Component graph of four fragments. Everything else (property forms, the assistant, suggestions) is a bottom sheet or a dialog on top of one of them.

> Verification status: the navigation graph, layouts, and fragments compile against Android API 37, pass unit tests, and are verified working on Android devices and emulators. The permission matrix, workflow, and `ReviewGuide` are pure Java and unit-tested.

## 1. Screen map

```
                       ┌────────────────────────┐
                       │ ProjectListFragment    │  start destination
                       │ projects, new, archive │
                       └───────┬──────────┬─────┘
          open a project      │          │  "Versions and team" (item menu)
                              ▼          ▼
                ┌──────────────────┐   ┌─────────────────────┐
                │ EditorFragment   │◄──┤ VersionsFragment    │
                │ (versionId)      │──►│ (projectId,         │
                │                  │   │  versionId?)        │
                └───┬──────────────┘   └─────────────────────┘
                    │ Estimate
                    ▼
                ┌──────────────────┐
                │ EstimateFragment │
                │ (versionId)      │
                └──────────────────┘
```

| Screen | Purpose | Main pieces |
|---|---|---|
| **Projects** | List, create, archive, delete | Material toolbar (show archived, "Your name"), `RecyclerView` of projects (name, location and county, plot size and area, budget, last-updated time, status chip taken from the head version), extended FAB "New project", empty state. Item menu: Versions and team, Archive / Unarchive, Delete (with confirmation) |
| **New project** (dialog) | Name, owner, location, county (47), plot preset or width × length, budget (KES), building type | Plot presets in feet (40×80, 50×100, 60×100, 100×100) fill width and length in metres; validation by `ProjectForm`; creating a project also creates its first draft and a ground floor |
| **Editor** | Draw and edit one version, one floor at a time | Toolbar (undo, redo, Estimate; overflow: assistant, suggestions, versions, view options, units); banner when the version is read-only; `FloorPlanView`; floor button, scale button, zoom in / out / fit; tool chips (Select, Wall, Room, Door, Window, Column, Stair, Furniture); options for the active tool; a selection bar (properties, duplicate, rotate, delete) |
| **Properties** (bottom sheet) | Edit the selected element or room by typed values | Fields generated from `planner.props.ElementFields` (length, thickness, height, size, rotation, type, material, offset, sill, name, room type), values in the person's unit system; each committed change is one undo step |
| **Assistant** (bottom sheet) | Ask about budget or drawing problems | Quick-question chips, a text box, replies in text; replies may carry **proposals** that become suggestions |
| **Suggestions** (bottom sheet) | See proposals, accept or reject | Listed per version; accepting applies the change to the draft as one undo step |
| **Estimate** | Cost, items and assumptions of a version | Tabs **Cost** (total with min / max, budget position, benchmark, donut by category, add-ons, VAT note, warnings, rate-pack note, Save estimate, Export PDF / PNG), **Items** (lines by category; tap a line to set the project's own price), **Assumptions** (finish, roof, pitch, sanitation, tank, ceilings, grills, add-on percentages, NCA levy switch) |
| **Versions** | History, review, discussion, team | "Acting as" selector and four tabs: **Versions**, **Review**, **Discussion**, **Team** (see §4) |

All text is in string resources in English and Swahili (287 strings each; the Swahili is a first pass and needs a native speaker's review). Some text still comes from code and is English-only; the list is in `06-roadmap.md`.

## 2. Navigation graph (`res/navigation/nav_graph.xml`)

Start destination `projectListFragment`.

| From | Action | To | Notes |
|---|---|---|---|
| Projects | `action_projects_to_editor` | Editor | argument `versionId` (the project's head version) |
| Projects | `action_projects_to_versions` | Versions | arguments `projectId`, optional `versionId` |
| Editor | `action_editor_to_estimate` | Estimate | argument `versionId` |
| Editor | `action_editor_to_versions` | Versions | |
| Editor | `action_editor_to_editor` | Editor | `popUpTo` the editor, inclusive: switching to a new draft replaces the editor instead of stacking a second one |
| Versions | `action_versions_to_editor` | Editor | `popUpTo` the editor, inclusive, so Back from the opened draft goes to the project list rather than a stale editor |

Arguments are plain strings read through `requireArguments()`; Safe Args is not used.

**One guard for every navigation.** `ui.common.Nav.go(fragment, actionId, args)` navigates only if the current destination really has that action. A double tap, or a tap during a transition, would otherwise try to navigate from a destination that has already changed and throw `IllegalArgumentException`; now the second call is ignored.

**Back behaviour.** The manifest opts in to predictive back (`enableOnBackInvokedCallback`). There are no custom back callbacks: Back is the Navigation Component's own pop, and the editor saves its pending change when it leaves (autosave runs 1.2 s after an edit and again when the screen pauses or is destroyed).

**Edge to edge.** At target 36 the app always draws behind the system bars. `MainActivity` calls `EdgeToEdge.enable` and applies one padding from the system bars, display cutout and keyboard to the host view, so no fragment deals with insets.

## 3. The editor in use

* **Drawing.** Choose a tool chip, then drag or tap. Walls and rooms are drawn point by point and finished with *Done*; the active tool's type options (wall kind, door type, window type, column shape, stair type, furniture type) appear as chips in a strip under the canvas. Thickness, height and material follow the wall kind by default and are changed afterwards in the properties sheet.
* **Selecting.** The Select tool taps, long-presses (toggle in or out of the selection), moves, resizes by a corner, rotates by the handle and slides doors and windows along their wall.
* **Floors.** The floor button lists floors; with edit rights it also adds one. The plan shows the active floor only.
* **Scale.** The scale button chooses a drawing ratio (1:5 … 1:500). The label shows the ratio nearest to the current zoom.
* **View options.** Grid, dimensions, room names, snapping and angle lock.
* **Units.** Metric or feet and inches, switched from the overflow menu. The model stays in millimetres.
* **Saving.** The subtitle shows *Saved*, *Saving*, *Waiting to save* or *Could not save*. A failed save shows a Snackbar with the reason and a **Retry** action that stays until dismissed; the document remains marked as unsaved meanwhile.
* **Read-only.** When the version is not a draft the banner says so and offers *Make editable copy*, which creates a new draft (the original stays untouched). When the person's role cannot edit, the banner says that instead; only Select stays enabled so they can still look at things and comment.
* **Rotation and process death.** The controller, zoom and pan live in the ViewModel and survive rotation. The editor restores *the document* from the database; it does not restore a half-drawn wall.

## 4. Roles, permissions and the review workflow

### 4.1 Role matrix (`PermissionPolicy`)

| Permission | Owner | Architect | Engineer | Quantity surveyor | Contractor |
|---|:-:|:-:|:-:|:-:|:-:|
| View plan, view costs, comment, export report | ✓ | ✓ | ✓ | ✓ | ✓ |
| Edit plan (draft only) | ✓ | ✓ | | | |
| Edit prices in the estimate | ✓ | | | ✓ | ✓ |
| Suggest changes | ✓ | ✓ | ✓ | ✓ | ✓ |
| Accept or reject a suggestion (draft only) | ✓ | ✓ | | | |
| Create a version | ✓ | ✓ | | | |
| Submit for review (draft only) | ✓ | ✓ | | | |
| Professional review | | ✓ | ✓ | ✓ | |
| Owner approval | ✓ | | | | |
| Lock an approved version | ✓ | | | | |
| Manage members | ✓ | | | | |

The version state then removes permissions:

* Edit plan, decide suggestion and submit work **only on a Draft**. A version in any other state is frozen for everyone.
* Professional review is possible only while the version is *in Professional Review*; owner approval only in *Owner Review*; locking only when *Approved*.
* Suggestions can be made on a Draft or in Professional Review.
* **Prices** (`EDIT_ESTIMATE`) are refused only when the version is Locked, because a price override changes the estimate and not the design. The **assumptions** on the estimate screen (finish, roof, wastage...) are part of the design, are frozen with it and follow `EDIT_PLAN`.

There is exactly one Owner per project: the person who created it. Members who can be added are Architect, Engineer, Quantity surveyor and Contractor, with an optional registration number (BORAQS, EBK or NCA, depending on the role).

### 4.2 States

```
        submit                 professional approves            owner approves            owner locks
Draft ─────────► Professional ───────────────────► Owner ───────────────────► Approved ─────────► Locked
  ▲               Review                            Review                                         (final)
  │                   │ changes requested               │ changes requested
  └───────────────────┴─────────────────────────────────┘
```

* The number of distinct professional approvals needed before the owner is asked is `ApprovalWorkflow.DEFAULT_REQUIRED_PROFESSIONALS` (1).
* **A round starts after the last "changes requested".** Approvals given before it do not count after the design is changed and resubmitted (`ApprovalWorkflow.currentRound`).
* Asking for changes must come with a comment saying what to change (`ReviewGuide.Action.commentRequired`).
* **"Changes requested" returns the same version to Draft.** That is the one point where a version becomes editable again. The record of who asked for what stays in the approval history, and anyone who prefers to keep the reviewed state untouched can start a new draft from it instead.
* An **Approved** version can only move to **Locked**. A Locked version can never be edited, and no change can be made to it: *Make editable copy* creates a new draft, which starts a new version number.
* The project status shown in the list is derived from the **head** version (the newest version number).

### 4.3 What the person sees (`ReviewGuide`)

The Review tab is driven by `ReviewGuide.actions(role, state)`, which asks `PermissionPolicy`, the same rule the repository enforces, so the screen never offers a button the workflow would refuse. It shows the stage name, a one-sentence explanation of the stage, the buttons this member may press (*Submit for review*, *Approve as professional*, *Approve as owner*, *Ask for changes*, *Lock this version*) and the append-only record of decisions. When the acting member has no button, it says so and names who acts next ("As Engineer you can't take a step on this version right now. Next: the owner approves it or asks for changes."). Discussion shows comments (optionally tied to an element) and the suggestions made about the version. Team lists members and, for the Owner, adds and removes them.

### 4.4 "Acting as" on a single device

There are no accounts. `LocalProfile` stores the device owner's name, and the Versions screen has an **Acting as** selector listing the project's members, so an architect, an engineer and the owner can take turns on one phone. It changes which buttons appear and what the repository allows, and the member's name and role are copied onto each comment and approval they make.

**This is a convenience, not security.** Anyone holding the phone can choose any member, including the Owner, and the database is not encrypted. Real multi-user collaboration needs accounts and a server, which the data model prepares for (`users.remoteId`) but this version does not include.

## 5. Estimate, export and share

1. Open the estimate from the editor. `EstimateViewModel` loads the version, the project's county rate pack and its price overrides, and runs `EstimateRunner`.
2. **Cost** shows the total, its min and max, the budget position, a benchmark note and the add-ons. **Items** lists every quantity and price; tapping one opens a dialog for the project's own quote (validated by `RateInput`); the screen marks overridden items. **Assumptions** edits the settings (only on a draft, for a role that can edit the plan; otherwise the fields are disabled).
3. **Save estimate** stores a snapshot (the repository keeps the latest 20 per version) so a report can show the numbers as they were.
4. **Export PDF / PNG** enqueues an `ExportReportWorker` (WorkManager `OneTimeWorkRequest`, input `versionId` and `format`). The worker loads the version itself, runs the same engines and writes to `cacheDir/reports/`: a **PDF** (A4 portrait text pages, A4 landscape plan pages) with, in order, the project summary and cost summary, floors and rooms, a plan sheet per drawn floor, the quantity schedule, assumptions / price warnings / drawing issues, the review and approval record and a closing disclaimer; or one **PNG** (2100 × 1485) per floor. Files older than a day are removed.
5. On success the screen shares the file or files through `FileProvider` (authority `${applicationId}.files`, path `reports/` in `res/xml/file_paths.xml`) with `ACTION_SEND` or `ACTION_SEND_MULTIPLE` and read-permission grants. A job is delivered once (`ExportJob.delivered`), so rotating the screen does not share the file twice.

An exported file is a copy. The record is the version in the database.

## 6. Accessibility and layout

* Icon buttons have content descriptions (including a per-row description on each project's overflow button, so a screen reader says which project it acts on). Colors are checked for contrast by a unit test (WCAG 4.5:1 for text, 3:1 for graphics, light and dark). Touch-target sizes and large-font behaviour were not measured.
* The plan is **one** accessibility node with a spoken summary. Editing individual elements by screen reader is not supported yet (see `03-floorplanview-design.md`).
* Layouts are single-column. There is **no tablet or landscape-specific layout**; the editor canvas simply gets more room.
* The theme is `Theme.Material3.DayNight.NoActionBar`, so dark mode follows the system (`values-night/colors.xml`); the dark palette was checked for contrast only.

## 7. Known limits of the screens

* Plain `AlertDialog`s (name, price, decision) lose a half-typed value when the device rotates; the sheets and the editor do not.
* The assistant's conversation is held in the view and is lost on rotation; the proposals it produced are stored as suggestions and are not lost.
* Suggestion revert uses the editor's undo.
* Some text is still English-only: the offline assistant's replies, `PlanChecker` and other domain messages, enum labels, a few ViewModel messages, text printed in the PDF and the canvas text "Scale 1:%d".
* Every screen was checked only by reading and compiling; run each one on a small phone at the largest font size before trusting the layouts.
