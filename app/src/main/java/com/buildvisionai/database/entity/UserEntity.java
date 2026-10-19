package com.buildvisionai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A person known to this device. */
@Entity(tableName = "users")
public class UserEntity {
    @PrimaryKey
    @NonNull public String id = "";
    @NonNull public String displayName = "";
    @Nullable public String email;
    @Nullable public String phone;
    @Nullable public String remoteId;
}
