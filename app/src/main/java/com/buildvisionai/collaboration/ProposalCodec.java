package com.buildvisionai.collaboration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** JSON form of a {@link ChangeProposal} */
public final class ProposalCodec {
    private ProposalCodec() {}

    private static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

    public static String toJson(ChangeProposal p) { return GSON.toJson(p); }

    /** Returns null for blank or malformed input rather than throwing; callers show "unreadable suggestion". */
    public static ChangeProposal fromJson(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            ChangeProposal p = GSON.fromJson(json, ChangeProposal.class);
            if (p == null) return null;
            for (ProposalOp op : p.ops) if (op == null || op.type == null) return null;
            return p;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
