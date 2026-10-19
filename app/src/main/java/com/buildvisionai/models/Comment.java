package com.buildvisionai.models;

/** A discussion comment on a version, optionally pinned to one element (wall, room ...). */
public final class Comment {
    public String id;
    public String versionId;
    /** Element the comment points at, or null for a general comment. */
    public String elementId;
    public String authorId;
    public String authorName;
    public UserRole authorRole;
    public String text;
    public long createdAt;
    public boolean resolved;
}
