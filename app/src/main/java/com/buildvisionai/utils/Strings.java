package com.buildvisionai.utils;

/**
 * Small replacements for Java library calls that Android only has from a newer API level than the
 * app's minSdk 24 (String.
 * 
 */
public final class Strings {
    private Strings() {}

    public static String join(String separator, Iterable<? extends CharSequence> parts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (CharSequence p : parts) {
            if (!first) sb.append(separator);
            sb.append(p);
            first = false;
        }
        return sb.toString();
    }
}
