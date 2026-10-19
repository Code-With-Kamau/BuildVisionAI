package com.buildvisionai.geometry;

import com.buildvisionai.models.RectD;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Uniform-grid spatial index. Hit testing, snapping and viewport culling use it so the cost of a
 * touch event or a frame depends on how many items are <em>near</em>, not on how many exist - what
 * keeps big plans smooth on low-end phones. Rebuilt (cheaply) when the plan revision changes.
 */
public final class SpatialGrid<T> {
    private final double cell;
    private final Map<Long, List<T>> cells = new HashMap<>();
    private int size;

    public SpatialGrid(double cellSizeMm) {
        this.cell = cellSizeMm;
    }

    private static long key(int cx, int cy) {
        return ((long) cx << 32) ^ (cy & 0xffffffffL);
    }

    private int cellOf(double v) { return (int) Math.floor(v / cell); }

    public void insert(T item, RectD r) {
        int x0 = cellOf(r.minX), x1 = cellOf(r.maxX), y0 = cellOf(r.minY), y1 = cellOf(r.maxY);
        // guard against pathological sizes (a wall across a 1 km plot at tiny cell size)
        long span = (long) (x1 - x0 + 1) * (y1 - y0 + 1);
        if (span > 4096) { x1 = x0 + 63; y1 = y0 + 63; }
        for (int cx = x0; cx <= x1; cx++) {
            for (int cy = y0; cy <= y1; cy++) {
                cells.computeIfAbsent(key(cx, cy), k -> new ArrayList<>(4)).add(item);
            }
        }
        size++;
    }

    /** Items whose cells overlap the query rectangle (superset of the true hits; each returned once). */
    public List<T> query(RectD r) {
        Set<T> seen = new HashSet<>();
        List<T> out = new ArrayList<>();
        int x0 = cellOf(r.minX), x1 = cellOf(r.maxX), y0 = cellOf(r.minY), y1 = cellOf(r.maxY);
        long span = (long) (x1 - x0 + 1) * (y1 - y0 + 1);
        if (span > 100_000) { // very large query: just return everything
            for (List<T> l : cells.values()) for (T t : l) if (seen.add(t)) out.add(t);
            return out;
        }
        for (int cx = x0; cx <= x1; cx++) {
            for (int cy = y0; cy <= y1; cy++) {
                List<T> l = cells.get(key(cx, cy));
                if (l == null) continue;
                for (T t : l) if (seen.add(t)) out.add(t);
            }
        }
        return out;
    }

    public List<T> queryPoint(double x, double y, double radius) {
        return query(new RectD(x - radius, y - radius, x + radius, y + radius));
    }

    public int size() { return size; }
}
