package com.buildvisionai.models;

public final class Furniture extends PlacedElement {
    public FurnitureType furnitureType;

    public Furniture(String id, String floorId, double x, double y, FurnitureType furnitureType) {
        super(id, floorId, x, y, furnitureType.defaultWidthMm, furnitureType.defaultDepthMm);
        this.furnitureType = furnitureType;
    }

    @Override public ElementType type() { return ElementType.FURNITURE; }

    @Override public Furniture copy() {
        Furniture f = new Furniture(id, floorId, x, y, furnitureType);
        f.widthMm = widthMm;
        f.depthMm = depthMm;
        copyPlacedInto(f);
        return f;
    }
}
