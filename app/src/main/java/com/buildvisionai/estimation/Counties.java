package com.buildvisionai.estimation;


import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Kenya's 47 counties and the price region each belongs to. */
public final class Counties {
    private Counties() {}

    private static final Map<String, Region> MAP = new LinkedHashMap<>();

    private static void put(Region r, String... names) {
        for (String n : names) MAP.put(n, r);
    }

    static {
        put(Region.NAIROBI_METRO, "Nairobi", "Kiambu", "Kajiado", "Machakos");
        put(Region.CENTRAL, "Nyeri", "Kirinyaga", "Murang'a", "Nyandarua", "Laikipia", "Meru", "Embu", "Tharaka-Nithi");
        put(Region.COAST, "Mombasa", "Kwale", "Kilifi", "Lamu", "Tana River", "Taita-Taveta");
        put(Region.RIFT_VALLEY, "Nakuru", "Narok", "Kericho", "Bomet", "Uasin Gishu", "Trans-Nzoia", "Nandi", "Baringo", "Elgeyo-Marakwet");
        put(Region.WESTERN_NYANZA, "Kakamega", "Vihiga", "Bungoma", "Busia", "Siaya", "Kisumu", "Homa Bay", "Migori", "Kisii", "Nyamira");
        put(Region.EASTERN, "Kitui", "Makueni", "Isiolo");
        put(Region.NORTHERN_ARID, "Turkana", "West Pokot", "Samburu", "Marsabit", "Garissa", "Wajir", "Mandera");
    }

    /** All county names, alphabetical. */
    public static List<String> all() {
        List<String> l = new ArrayList<>(MAP.keySet());
        Collections.sort(l);
        return l;
    }

    public static Region regionOf(String county) {
        if (county == null) return Region.NAIROBI_METRO;
        Region r = MAP.get(county.trim());
        if (r != null) return r;
        for (Map.Entry<String, Region> e : MAP.entrySet()) {
            if (e.getKey().equalsIgnoreCase(county.trim())) return e.getValue();
        }
        return Region.NAIROBI_METRO;
    }

    public static boolean isCounty(String name) {
        return name != null && regionOf(name) != null && MAP.keySet().stream().anyMatch(k -> k.equalsIgnoreCase(name.trim()));
    }
}
