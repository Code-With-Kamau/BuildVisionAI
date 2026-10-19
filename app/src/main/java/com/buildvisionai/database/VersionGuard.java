package com.buildvisionai.database;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Database-level enforcement of the version rules, as SQLite triggers. The rules are also checked
 * in the domain layer ({@code VersionPolicy}, {@code PermissionPolicy}); these triggers are the last
 * line of defence, so a bug elsewhere cannot silently change a design that is under review,
 * approved or locked, or rewrite the approval history.
 *
 * <ul>
 *   <li>Plan rows (floors, walls, rooms, room_walls, doors, windows, columns, stairs, furniture) and
 *       estimate settings of a
 *       version whose state is not DRAFT cannot be inserted, updated or deleted.</li>
 *   <li>An APPROVED version can only move to LOCKED; LOCKED is final.</li>
 *   <li>A version that has left DRAFT cannot be deleted on its own (history is never rewritten);
 *       a draft can be discarded.</li>
 *   <li>Approval records are append-only.</li>
 * </ul>
 *
 * <p>Deleting a whole project still works: the cascade removes the version row first, and the
 * triggers only fire while the version row still exists. This class is plain Java (no Android
 * imports) so the SQL can be exercised on a desktop SQLite in tests.
 *
 * <p>Triggers are not part of Room's schema validation. {@code AppDatabase} installs them on every
 * open (the statements are IF NOT EXISTS), which also restores them after a migration that
 * recreated a guarded table. A migration that drops and renames tables should call
 * {@link #dropStatements()} first.
 */
public final class VersionGuard {
    private VersionGuard() {}

    /**
     * Tables whose rows belong to the design of one version and are frozen with it: the nine plan
     * tables plus the estimate assumptions. All have a {@code versionId} column. Stored estimates
     * and rate overrides are not guarded: re-pricing a locked design is allowed, changing it is not.
     */
    public static final List<String> GUARDED_TABLES = Collections.unmodifiableList(java.util.Arrays.asList(
            "floors", "walls", "rooms", "room_walls", "doors", "windows", "columns", "stairs", "furniture",
            "estimate_settings"));

    public static final String LOCKED_MESSAGE = "BV_LOCKED: the plan of a version that is not a draft cannot be changed";
    public static final String STATE_MESSAGE = "BV_STATE: an approved version can only be locked and a locked version cannot change";
    public static final String APPEND_ONLY_MESSAGE = "BV_APPEND_ONLY: approval records cannot be changed";
    public static final String HISTORY_MESSAGE = "BV_HISTORY: a version that has left draft cannot be deleted";

    private static String notDraft(String rowRef) {
        return "EXISTS (SELECT 1 FROM versions WHERE id = " + rowRef + ".versionId AND workflowState <> 'DRAFT')";
    }

    public static List<String> createStatements() {
        List<String> sql = new ArrayList<>();
        for (String t : GUARDED_TABLES) {
            sql.add(trigger("bv_lock_" + t + "_ins", "BEFORE INSERT ON " + t, notDraft("NEW"), LOCKED_MESSAGE));
            sql.add(trigger("bv_lock_" + t + "_upd", "BEFORE UPDATE ON " + t,
                    notDraft("OLD") + " OR " + notDraft("NEW"), LOCKED_MESSAGE));
            sql.add(trigger("bv_lock_" + t + "_del", "BEFORE DELETE ON " + t, notDraft("OLD"), LOCKED_MESSAGE));
        }
        sql.add(trigger("bv_state_forward_only", "BEFORE UPDATE OF workflowState ON versions",
                "OLD.workflowState IN ('APPROVED', 'LOCKED') AND NEW.workflowState <> OLD.workflowState "
                        + "AND NOT (OLD.workflowState = 'APPROVED' AND NEW.workflowState = 'LOCKED')", STATE_MESSAGE));
        sql.add(trigger("bv_versions_no_delete", "BEFORE DELETE ON versions",
                "OLD.workflowState <> 'DRAFT' AND EXISTS (SELECT 1 FROM projects WHERE projectId = OLD.projectId)",
                HISTORY_MESSAGE));
        sql.add(trigger("bv_approvals_no_update", "BEFORE UPDATE ON approvals", "1", APPEND_ONLY_MESSAGE));
        sql.add(trigger("bv_approvals_no_delete", "BEFORE DELETE ON approvals",
                "EXISTS (SELECT 1 FROM versions WHERE id = OLD.versionId)", APPEND_ONLY_MESSAGE));
        return sql;
    }

    public static List<String> dropStatements() {
        List<String> sql = new ArrayList<>();
        for (String t : GUARDED_TABLES) {
            sql.add("DROP TRIGGER IF EXISTS bv_lock_" + t + "_ins");
            sql.add("DROP TRIGGER IF EXISTS bv_lock_" + t + "_upd");
            sql.add("DROP TRIGGER IF EXISTS bv_lock_" + t + "_del");
        }
        sql.add("DROP TRIGGER IF EXISTS bv_state_forward_only");
        sql.add("DROP TRIGGER IF EXISTS bv_versions_no_delete");
        sql.add("DROP TRIGGER IF EXISTS bv_approvals_no_update");
        sql.add("DROP TRIGGER IF EXISTS bv_approvals_no_delete");
        return sql;
    }

    private static String trigger(String name, String event, String when, String message) {
        return "CREATE TRIGGER IF NOT EXISTS " + name + " " + event
                + " WHEN " + when
                + " BEGIN SELECT RAISE(ABORT, '" + message + "'); END";
    }
}
