# BuildVision AI — Database schema

One on-device SQLite database (`buildvision.db`) through Room 2.8.5, version 1, 21 tables. The schema is exported to `app/schemas/` on every build (`room.schemaLocation`); commit those JSON files after the first successful build so migration tests have something to read. There is deliberately **no** `fallbackToDestructiveMigration()`: a missing migration must fail in testing, not wipe a person's designs.

> Verification status: the table design, triggers, and all 67 `@Query` statements are verified on Room 2.8.5 with Room's annotation processor generating clean DAO implementations and schemas exported to `app/schemas/`.

## 1. Design rules

1. **Plans are relational.** Every wall, room, door, window, column, stair and furniture item is a row. A room is linked to its walls by `room_walls`. There is no JSON blob for a plan. (The only JSON stored is a suggestion's change proposal, which is a short payload of operations, not a design.)
2. **Composite keys carry the version.** Plan rows are keyed `(versionId, id)`, so the *same element id* exists in every version. That is what makes a clone cheap and exact: copy the rows with a new `versionId`, keep every `id`, and every cross-reference (`wallId`, `floorId`, `roomId`) is still valid inside the new version.
3. **Cascading foreign keys follow the building.** `version → floor → {wall, room, column, stair, furniture}`, `wall → {door, window}`, `room → room_walls ← wall`. Deleting a floor or version removes what is on it.
4. **Frozen versions are protected in the database** by triggers (§4), in addition to the checks in the domain layer and the repository.
5. **Derived numbers are stored only where they are a snapshot.** A wall's length and angle are never stored (they follow from its end points). A room stores its measured areas because a report must show the numbers as they were; an estimate stores its totals and lines for the same reason.
6. **Doubles for money and millimetres.** Lengths are millimetres, areas m², money KES.

## 2. Tables

### Projects, versions

| Table | Key | Foreign keys | Notes |
|---|---|---|---|
| `projects` | `projectId` | — | name, owner, location, county, plot width/length (m), budget (KES), building type, status, `headVersionId`, `archived`, timestamps. Index on `updatedAt` |
| `versions` | `id` | `projectId → projects` CASCADE | `number` (unique per project), `label`, `parentVersionId`, `createdBy`, `workflowState`. Unique index `(projectId, number)` |

`projects.headVersionId` and `versions.parentVersionId` are plain columns, not foreign keys, so deleting a draft never fights a pointer. The head is always the **newest version number**: every new draft and every restore takes the next number and becomes the head, and discarding the head draft moves the head to the newest remaining version.

### The drawing (all keyed by version)

| Table | Key | Parent (cascade) | Main columns |
|---|---|---|---|
| `floors` | `(versionId, id)` | `versions` | `level`, `name`, `floorToFloorMm` |
| `walls` | `(versionId, id)` | `floors` | `startX/Y`, `endX/Y`, `thicknessMm`, `heightMm`, `kind`, `material` |
| `rooms` | `(versionId, id)` | `floors` | `name`, `roomType`, anchor `x/y`, `areaM2`, `grossAreaM2`, `perimeterM`, `widthM`, `lengthM` |
| `room_walls` | `(versionId, roomId, wallId)` | `rooms` and `walls` | `position` (order around the room) |
| `doors` | `(versionId, id)` | `walls` | `floorId`, `offsetMm` along the wall, width/height, `doorType`, `swing` |
| `windows` | `(versionId, id)` | `walls` | `floorId`, `offsetMm`, width/height, `sillMm`, `windowType` |
| `columns` | `(versionId, id)` | `floors` | `x/y`, width/depth, `rotationDeg`, `shape`, `heightMm` |
| `stairs` | `(versionId, id)` | `floors` | `x/y`, width/depth, `rotationDeg`, `stairType`, `riseMm`, `riserMm` |
| `furniture` | `(versionId, id)` | `floors` | `x/y`, width/depth, `rotationDeg`, `furnitureType` |

Every one of these has an index on `(versionId, floorId)` (doors and windows also on `(versionId, wallId)`; `room_walls` on `(versionId, wallId)`), so loading a floor does not scan.

### Estimates and prices

| Table | Key | Parent | Notes |
|---|---|---|---|
| `estimate_settings` | `versionId` | `versions` CASCADE | The design assumptions: finish level, roof type/pitch/overhang, foundation depth, footing, plinth, slab, hardcore, screed, plaster, mortar joint, pad size, window grills, ceilings, sanitation, water tank litres, preliminaries/overhead-profit/contingency/fees percentages, NCA levy flag, nine wastage factors. Frozen with the version |
| `estimates` | `id` | `versions` CASCADE | A stored snapshot: rate pack id and date, region, finish level, gross floor area, subtotal and total (min/avg/max), cost per m². Index `(versionId, createdAt)`. The repository keeps the latest 20 per version (`MAX_STORED_ESTIMATES`) |
| `estimate_lines` | `(estimateId, lineNo)` | `estimates` CASCADE | category, item code, basis text, quantity, rate and total (min/avg/max), allowance flag |
| `estimate_addons` | `(estimateId, code)` | `estimates` CASCADE | preliminaries, overhead and profit, contingency, fees, NCA levy: label, percentage, amounts (min/avg/max), note |
| `rate_overrides` | `(projectId, itemCode)` | `projects` CASCADE | The project's own price quotes (min/avg/max) that replace the built-in rate of one item |

Estimates and rate overrides are **not** frozen by the triggers: re-pricing a locked design is allowed, changing the design is not.

### People and collaboration

| Table | Key | Foreign keys | Notes |
|---|---|---|---|
| `users` | `id` | — | display name, email, phone, `remoteId` (reserved for a future account service) |
| `members` | `(projectId, userId)` | `projects` CASCADE, `users` CASCADE | `role`, `registrationNo` (e.g. BORAQS/EBK/NCA number), `displayName`, `addedAt`. Index on `userId` |
| `comments` | `id` | `versions` CASCADE | optional `elementId` (a comment can point at a wall or room), author name and role as written, `resolved`. Index `(versionId, createdAt)` |
| `suggestions` | `id` | `versions` CASCADE | `source` (PERSON or AI), `status`, `title`, `rationale`, `payloadJson` (the proposal), decision fields. Index `(versionId, status)` |
| `approvals` | `id` | `versions` CASCADE | `stage`, `decision`, `comment`, user and role at the time. Append-only. Index `(versionId, createdAt)` |

Comments, suggestions and approvals reference only the **version**, never the member row. Removing a member therefore cannot delete the review history, and the author's name and role are copied into each record.

## 3. Relationships

```
projects 1──* versions 1──* floors 1──* walls 1──* doors
   │              │            │          └──* windows
   │              │            ├──* rooms *──* walls   (via room_walls)
   │              │            ├──* columns
   │              │            ├──* stairs
   │              │            └──* furniture
   │              ├── 1 estimate_settings
   │              ├──* estimates 1──* estimate_lines
   │              │              └──* estimate_addons
   │              ├──* comments, suggestions, approvals
   │              └── parentVersionId  (plain column: version it was copied from)
   ├──* members *──1 users
   └──* rate_overrides
```

## 4. Version guard triggers (`VersionGuard`)

Installed by `AppDatabase` on every open (`CREATE TRIGGER IF NOT EXISTS`, so it is idempotent and restores them after a migration that recreated a table). They raise `ABORT` with a message that starts with a stable code:

| Rule | Tables / event | Message |
|---|---|---|
| Plan rows of a version whose state is not `DRAFT` cannot be inserted, updated or deleted | `floors, walls, rooms, room_walls, doors, windows, columns, stairs, furniture, estimate_settings`: insert, update, delete (30 triggers) | `BV_LOCKED` |
| `APPROVED` can only move to `LOCKED`; `LOCKED` is final | update of `versions.workflowState` | `BV_STATE` |
| A version that has left draft cannot be deleted on its own | delete on `versions`, unless its project is already gone | `BV_HISTORY` |
| Approval records are append-only | update on `approvals` always; delete only while the version still exists | `BV_APPEND_ONLY` |

Two details worth knowing:

* **Deleting a whole project still works.** The cascade removes the version row first; the triggers only fire while the version (or its project) still exists, so a project delete is not blocked by its own history.
* **Triggers are not part of Room's schema validation.** They live outside the exported schema JSON. A migration that drops and renames a guarded table must call `VersionGuard.dropStatements()` first, because SQLite refuses to rename a table a trigger still names.

## 5. Version lifecycle in the repository

| Operation | What happens (all inside one transaction) |
|---|---|
| Create project | Project row, first version (Draft, number 1) with an empty ground floor, default estimate assumptions, the creator as a user and as the project's Owner member; head set |
| Save plan (`savePlanSync`) | Refuses any state but Draft with a message for the person. Replaces the plan rows (`replacePlan`: delete the floors of the version, which cascades, then insert everything) and upserts the settings, then touches the project's `updatedAt` |
| Open for edit | A draft is edited in place; anything else gets a **new draft copy**; the original is never touched |
| New draft / restore | New version row with the next number, `parentVersionId` = the source, label "Version n" or "Restored from version n"; `clonePlan(from, to)` copies every plan table with `INSERT ... SELECT` keeping ids; `copySettings`; head moved to the new version and project status derived from it |
| Discard draft | Only a Draft and never the project's only version. The head moves to the newest remaining version when the discarded draft was the head |
| Workflow change | `versions.workflowState` update plus an `approvals` row (the workflow decides; the repository persists both together) |

"Changes requested" at either review stage returns the **same** version to `DRAFT` (documented behaviour of `ApprovalWorkflow`); it is the one place a version re-enters the editable state, and the approval record of the request stays in the history.

## 6. Write patterns

* `@Upsert` for single rows. `REPLACE` is avoided on purpose: with foreign keys on, `INSERT OR REPLACE` deletes the old row and cascades into its children. A check in the SQLite test demonstrates the pitfall.
* Whole-plan replacement and cloning are `@Transaction` methods on `PlanDao`; mappers (`PlanMapper`, `EstimateMapper`, `ProjectMapper`, `CollaborationMapper`, `Codes`) turn models into rows and back, and report anything they had to leave out as warnings instead of failing silently.
* Lists on screen come from Room `LiveData` queries (projects, versions); everything else is a synchronous call on the single background thread through `ProjectRepository.run(...)`.

## 7. Migrations

Version 1 is the first release. For each later schema: bump `version`, add a `Migration` (never destructive), run the migration test against the exported JSON of the previous version, and keep the guard triggers working (drop before a rename, they are recreated on open).

## 8. How it was checked

* `sqltest/guard_test.py`: builds the schema on a real SQLite 3.45.1 and runs 9 checks, all passing: a draft is editable; a frozen version (four states) blocks writes; changes-requested returns a version to draft; the state only moves forward; a project delete cascades even when its versions are locked; approvals are append-only; version delete rules; a clone from a locked version into a new draft works; and the `REPLACE` pitfall against `@Upsert`-style upsert.
* `sqltest/dao_sql_check.py`: prepares all 67 `@Query` statements against that schema and checks that every `:parameter` has a matching method argument.
* JVM tests: mapper round trips (model → rows → model), and `VersionPolicy` numbering and restore rules.

Not covered: Room's compile-time query validation, generated DAO classes, `LiveData` invalidation, and real-device migrations.
