package com.buildvisionai.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.buildvisionai.models.AppUser;
import com.buildvisionai.utils.Ids;
import com.buildvisionai.utils.UnitSystem;

/**
 * The person using this device. Identity is local-first: an id made on first launch and an editable
 * display name. It also remembers, per project, which team member the device is currently acting as
 * (single-device review mode: the architect, engineer and owner can take turns on one phone).
 *
 * <p>This is a convenience, not security: anyone holding the unlocked device can switch who they
 * are acting as. Real enforcement needs accounts on a server, which is a later phase.
 */
public final class LocalProfile {
    private static final String FILE = "profile";
    private static final String KEY_ID = "user_id";
    private static final String KEY_NAME = "display_name";
    private static final String KEY_UNITS = "units";
    private static final String ACTING_PREFIX = "acting_";

    private final SharedPreferences prefs;

    public LocalProfile(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public synchronized String userId() {
        String id = prefs.getString(KEY_ID, null);
        if (id == null) {
            id = Ids.newId();
            prefs.edit().putString(KEY_ID, id).apply();
        }
        return id;
    }

    public String displayName() { return prefs.getString(KEY_NAME, ""); }

    public void setDisplayName(String name) { prefs.edit().putString(KEY_NAME, name == null ? "" : name.trim()).apply(); }

    public AppUser user() {
        AppUser u = new AppUser();
        u.id = userId();
        u.displayName = displayName();
        return u;
    }

    /** The member this device acts as on a project; the device user (the project's owner) until changed. */
    public String actingUserId(String projectId) { return prefs.getString(ACTING_PREFIX + projectId, userId()); }

    public void setActingUserId(String projectId, String userId) {
        prefs.edit().putString(ACTING_PREFIX + projectId, userId).apply();
    }

    /** Metric by default; the choice is remembered across projects. */
    public UnitSystem units() {
        try {
            return UnitSystem.valueOf(prefs.getString(KEY_UNITS, UnitSystem.METRIC.name()));
        } catch (IllegalArgumentException e) {
            return UnitSystem.METRIC;
        }
    }

    public void setUnits(UnitSystem units) { prefs.edit().putString(KEY_UNITS, units.name()).apply(); }
}
