package com.behlul.liftlog.service;

import java.util.List;
import java.util.OptionalInt;

/**
 * Ranks as one comparable number, the same ladder the web's LP chart uses: every division below Master is 100 LP
 * wide (IRON IV 0 LP = 0, IRON III 0 LP = 100, ... DIAMOND I 99 LP = 2799), and Master, Grandmaster and Challenger
 * share one open-ended ladder from 2800, since their LP simply keeps counting across those three.
 */
public final class LolRanks {

    static final List<String> TIERS = List.of("IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "EMERALD", "DIAMOND");
    static final List<String> APEX_TIERS = List.of("MASTER", "GRANDMASTER", "CHALLENGER");
    static final List<String> DIVISIONS = List.of("IV", "III", "II", "I");
    static final int APEX_BASE = TIERS.size() * 400;
    /**
     * A single ranked game never moves more than this. A bigger step between two snapshots means something other
     * than one game happened in between (decay, a demotion shield, a placement), so it is not attributed.
     */
    static final int MAX_LP_PER_GAME = 100;

    private LolRanks() {
    }

    /** Empty for a tier or division this does not know (a new one Riot adds), so nothing is guessed from it. */
    public static OptionalInt score(String tier, String division, int lp) {
        if (tier == null) {
            return OptionalInt.empty();
        }
        if (APEX_TIERS.contains(tier)) {
            return OptionalInt.of(APEX_BASE + lp);
        }
        int tierIndex = TIERS.indexOf(tier);
        int divisionIndex = DIVISIONS.indexOf(division);
        if (tierIndex < 0 || divisionIndex < 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(tierIndex * 400 + divisionIndex * 100 + lp);
    }

    /**
     * The LP one game moved, from the rank before it and after it; null when it cannot be told: an unknown tier, no
     * change, a change whose direction contradicts the result (a remake, or LP moving for another reason), or a
     * step no single game makes. Promotions and demotions count across the division line (GOLD IV 90 → GOLD III 15
     * is +25), which is right since LP carries over.
     */
    public static Integer lpChange(String tierBefore, String divisionBefore, int lpBefore, String tierAfter,
                                   String divisionAfter, int lpAfter, boolean win) {
        OptionalInt before = score(tierBefore, divisionBefore, lpBefore);
        OptionalInt after = score(tierAfter, divisionAfter, lpAfter);
        if (before.isEmpty() || after.isEmpty()) {
            return null;
        }
        int delta = after.getAsInt() - before.getAsInt();
        if (delta == 0 || (delta > 0) != win || Math.abs(delta) > MAX_LP_PER_GAME) {
            return null;
        }
        return delta;
    }
}
