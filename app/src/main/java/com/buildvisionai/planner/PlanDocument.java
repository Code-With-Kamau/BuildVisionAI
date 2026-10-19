package com.buildvisionai.planner;

import com.buildvisionai.models.Floor;
import com.buildvisionai.models.PlanElement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The editable plan of one project version: floors plus every element on them, keyed by id.
 *
 * <p>Mutations go through {@link EditTransaction} (undoable). Gesture code that mutates live
 * elements during a drag, for speed, must call {@link #touch()} so cached {@link FloorPlan}
 * snapshots are rebuilt. Elements are found by id, never by object identity, because applying a
 * transaction replaces instances with copies.
 */
public final class PlanDocument {
    private final List<Floor> floors = new ArrayList<>();
    private final LinkedHashMap<String, PlanElement> elements = new LinkedHashMap<>();
    private long revision;
    private final Map<String, FloorPlan> cache = new HashMap<>();
    private long cacheRevision = -1;

    public long revision() { return revision; }

    /** Marks the plan changed (invalidates cached floor snapshots). */
    public void touch() { revision++; }

    // floors

    public List<Floor> floors() { return Collections.unmodifiableList(floors); }

    public Floor floor(String id) {
        for (Floor f : floors) if (f.id.equals(id)) return f;
        return null;
    }

    public void putFloor(Floor f) {
        for (int i = 0; i < floors.size(); i++) {
            if (floors.get(i).id.equals(f.id)) { floors.set(i, f); sortFloors(); revision++; return; }
        }
        floors.add(f);
        sortFloors();
        revision++;
    }

    public void removeFloor(String id) {
        floors.removeIf(f -> f.id.equals(id));
        revision++;
    }

    private void sortFloors() {
        Collections.sort(floors, Comparator.comparingInt(f -> f.level));
    }

    //  elements 
    public PlanElement get(String id) { return elements.get(id); }

    public void put(PlanElement e) { elements.put(e.id, e); revision++; }

    public PlanElement remove(String id) {
        PlanElement e = elements.remove(id);
        if (e != null) revision++;
        return e;
    }

    public Collection<PlanElement> all() { return Collections.unmodifiableCollection(elements.values()); }

    public int elementCount() { return elements.size(); }

    public FloorPlan floorPlan(String floorId) {
        if (cacheRevision != revision) {
            cache.clear();
            cacheRevision = revision;
        }
        FloorPlan fp = cache.get(floorId);
        if (fp == null) {
            fp = new FloorPlan(floorId, revision, elements.values());
            cache.put(floorId, fp);
        }
        return fp;
    }

    /** Deep copy (floors and elements), used when a version is cloned. */
    public PlanDocument copy() {
        PlanDocument d = new PlanDocument();
        for (Floor f : floors) d.floors.add(f.copy());
        for (PlanElement e : elements.values()) d.elements.put(e.id, e.copy());
        d.revision = 0;
        return d;
    }
}
