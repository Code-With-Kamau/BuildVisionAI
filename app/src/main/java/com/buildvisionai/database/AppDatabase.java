package com.buildvisionai.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.buildvisionai.database.dao.CollaborationDao;
import com.buildvisionai.database.dao.EstimateDao;
import com.buildvisionai.database.dao.PlanDao;
import com.buildvisionai.database.dao.ProjectDao;
import com.buildvisionai.database.dao.VersionDao;
import com.buildvisionai.database.entity.ApprovalEntity;
import com.buildvisionai.database.entity.ColumnEntity;
import com.buildvisionai.database.entity.CommentEntity;
import com.buildvisionai.database.entity.DoorEntity;
import com.buildvisionai.database.entity.EstimateAddOnEntity;
import com.buildvisionai.database.entity.EstimateEntity;
import com.buildvisionai.database.entity.EstimateLineEntity;
import com.buildvisionai.database.entity.EstimateSettingsEntity;
import com.buildvisionai.database.entity.FloorEntity;
import com.buildvisionai.database.entity.FurnitureEntity;
import com.buildvisionai.database.entity.MemberEntity;
import com.buildvisionai.database.entity.ProjectEntity;
import com.buildvisionai.database.entity.RateOverrideEntity;
import com.buildvisionai.database.entity.RoomEntity;
import com.buildvisionai.database.entity.RoomWallEntity;
import com.buildvisionai.database.entity.StairEntity;
import com.buildvisionai.database.entity.SuggestionEntity;
import com.buildvisionai.database.entity.UserEntity;
import com.buildvisionai.database.entity.VersionEntity;
import com.buildvisionai.database.entity.WallEntity;
import com.buildvisionai.database.entity.WindowEntity;

/**
 * The single on-device database. The schema is exported to {@code app/schemas} on every build (see
 * the {@code room.schemaLocation} processor argument in app/build.gradle) so each released version
 * can be checked by a migration test.
 *
 *There is deliberately no {@code fallbackToDestructiveMigration()}: a user's designs are worth
 * more than a convenient upgrade path, so a missing migration must fail loudly in testing rather
 * than silently wipe the database.
 */
@Database(
        entities = {
                ProjectEntity.class, VersionEntity.class,
                FloorEntity.class, WallEntity.class, RoomEntity.class, RoomWallEntity.class,
                DoorEntity.class, WindowEntity.class, ColumnEntity.class, StairEntity.class, FurnitureEntity.class,
                EstimateSettingsEntity.class, RateOverrideEntity.class,
                EstimateEntity.class, EstimateLineEntity.class, EstimateAddOnEntity.class,
                UserEntity.class, MemberEntity.class, CommentEntity.class, SuggestionEntity.class, ApprovalEntity.class
        },
        version = 1,
        exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public static final String FILE_NAME = "buildvision.db";

    public abstract ProjectDao projects();

    public abstract VersionDao versions();

    public abstract PlanDao plans();

    public abstract EstimateDao estimates();

    public abstract CollaborationDao collaboration();

    private static volatile AppDatabase instance;

    public static AppDatabase get(Context context) {
        AppDatabase local = instance;
        if (local == null) {
            synchronized (AppDatabase.class) {
                local = instance;
                if (local == null) {
                    local = builder(Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, FILE_NAME));
                    instance = local;
                }
            }
        }
        return local;
    }

    /** For instrumented tests: an in-memory database with the same guard triggers. */
    public static AppDatabase inMemory(Context context) {
        return builder(Room.inMemoryDatabaseBuilder(context.getApplicationContext(), AppDatabase.class));
    }

    private static AppDatabase builder(RoomDatabase.Builder<AppDatabase> b) {
        return b.addCallback(GUARD_CALLBACK).build();
    }

    /**
     * Installs the version-guard triggers on every open. The statements are IF NOT EXISTS, so this
     * is cheap and idempotent, and it also restores the triggers after a migration that recreated a
     * guarded table. A migration that drops and renames tables should call
     * {@link VersionGuard#dropStatements()} first, because SQLite refuses to rename a table that a
     * trigger still refers to.
     */
    private static final RoomDatabase.Callback GUARD_CALLBACK = new RoomDatabase.Callback() {
        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            for (String sql : VersionGuard.createStatements()) db.execSQL(sql);
        }
    };
}
