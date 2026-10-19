package com.buildvisionai.models;

//
public enum RoomType {
    //          label              habitable wet    tiledFloor ceiling wallTileMm sockets fill (ARGB)
    BEDROOM(    "Bedroom",         true,  false, true,  true,  0,    4, 0xFFFFE9D6),
    MASTER_BEDROOM("Master Bedroom", true, false, true,  true,  0,    5, 0xFFFFDCC0),
    LIVING_ROOM("Living Room",     true,  false, true,  true,  0,    6, 0xFFE3F2E1),
    DINING_ROOM("Dining Room",     true,  false, true,  true,  0,    3, 0xFFD9EFD5),
    KITCHEN(    "Kitchen",         true,  true,  true,  true,  1200, 8, 0xFFFFF3C4),
    PANTRY(     "Pantry",          false, false, true,  true,  0,    2, 0xFFFFF8DC),
    LAUNDRY(    "Laundry",         false, true,  true,  true,  1200, 2, 0xFFD6EAF8),
    BATHROOM(   "Bathroom",        false, true,  true,  true,  2100, 1, 0xFFC9E7F5),
    TOILET(     "Toilet",          false, true,  true,  true,  2100, 0, 0xFFBFE0F0),
    OFFICE(     "Office",          true,  false, true,  true,  0,    5, 0xFFE8E0F5),
    GARAGE(     "Garage",          false, false, false, false, 0,    2, 0xFFE0E0E0),
    BALCONY(    "Balcony",         false, false, true,  false, 0,    1, 0xFFDDEFEF),
    VERANDA(    "Veranda",         false, false, true,  false, 0,    2, 0xFFD5E8E8),
    STORE(      "Store",           false, false, false, true,  0,    1, 0xFFEADFCF),
    CUSTOM(     "Custom Room",     true,  false, true,  true,  0,    3, 0xFFEFEFEF);

    public final String label;
    public final boolean habitable;
    public final boolean wet;
    public final boolean tiledFloor;
    public final boolean ceiling;
    public final int wallTileHeightMm;
    public final int sockets;
    public final int fillColor;

    RoomType(String label, boolean habitable, boolean wet, boolean tiledFloor, boolean ceiling,
             int wallTileHeightMm, int sockets, int fillColor) {
        this.label = label;
        this.habitable = habitable;
        this.wet = wet;
        this.tiledFloor = tiledFloor;
        this.ceiling = ceiling;
        this.wallTileHeightMm = wallTileHeightMm;
        this.sockets = sockets;
        this.fillColor = fillColor;
    }
}
