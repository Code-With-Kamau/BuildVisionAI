package com.buildvisionai.models;

/** A person's role on one project. A person can hold different roles on different projects. */
public final class Member {
    public String projectId;
    public String userId;
    public String displayName;
    public UserRole role;
    /** Professional registration number (BORAQS / EBK / NCA) when the role has one; informational, not verified here. */
    public String registrationNo;
    public long addedAt;
}
