package com.buildvisionai.estimation;

/** A measured quantity of work, priced with the matching labour rate. */
public enum WorkItem {
    EXCAVATION(Item.LAB_EXCAVATION_M3),
    HARDCORE(Item.LAB_HARDCORE_M3),
    CONCRETE(Item.LAB_CONCRETE_M3),
    FORMWORK(Item.LAB_FORMWORK_M2),
    STEEL(Item.LAB_STEEL_KG),
    MASONRY(Item.LAB_MASONRY_M2),
    PLASTER(Item.LAB_PLASTER_M2),
    SCREED(Item.LAB_SCREED_M2),
    TILING(Item.LAB_TILING_M2),
    PAINTING(Item.LAB_PAINTING_M2),
    ROOFING(Item.LAB_ROOFING_M2),
    CEILING(Item.LAB_CEILING_M2),
    DOORS_WINDOWS(Item.LAB_DOORS_WINDOWS_M2),
    PLUMBING_POINTS(Item.LAB_PLUMBING_POINT),
    ELECTRICAL_POINTS(Item.LAB_ELECTRICAL_POINT);

    public final Item labourItem;

    WorkItem(Item labourItem) { this.labourItem = labourItem; }
}
