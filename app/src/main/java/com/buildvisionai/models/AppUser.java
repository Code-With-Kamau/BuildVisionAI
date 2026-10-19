package com.buildvisionai.models;

/** A person using the app. Identity is local-first; {@code remoteId} is set when synced to a backend. */
public final class AppUser {
    public String id;
    public String displayName;
    public String email;
    public String phone;
    public String remoteId;
}
