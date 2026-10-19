package com.buildvisionai.geometry;

import com.buildvisionai.models.WallKind;
import com.buildvisionai.models.WallMaterial;

/** Quantities of one wall, measured on its centreline. Face areas are for ONE face. */
public final class WallMetrics {
    public String wallId;
    public WallKind kind;
    public WallMaterial material;
    public double lengthM;
    public double heightM;
    public double thicknessMm;
    public double grossFaceAreaM2;
    public double openingAreaM2;
    public double netFaceAreaM2;
    /** Masonry volume (m3) = net face area x thickness. */
    public double volumeM3;
    public int doorCount;
    public int windowCount;
}
