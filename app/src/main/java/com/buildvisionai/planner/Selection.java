package com.buildvisionai.planner;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Ids of the selected elements; the last one added is the "primary" (shows handles). */
public final class Selection {
    private final LinkedHashSet<String> ids = new LinkedHashSet<>();

    public void set(String id) { ids.clear(); if (id != null) ids.add(id); }
    public void add(String id) { ids.remove(id); ids.add(id); }
    public void toggle(String id) { if (!ids.remove(id)) ids.add(id); }
    public void remove(String id) { ids.remove(id); }
    public void clear() { ids.clear(); }
    public boolean contains(String id) { return ids.contains(id); }
    public boolean isEmpty() { return ids.isEmpty(); }
    public int size() { return ids.size(); }
    public Set<String> ids() { return Collections.unmodifiableSet(ids); }

    public String primaryId() {
        String last = null;
        for (String s : ids) last = s;
        return last;
    }

    public boolean isSingle() { return ids.size() == 1; }
}
