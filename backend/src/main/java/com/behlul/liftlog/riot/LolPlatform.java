package com.behlul.liftlog.riot;

import java.util.Arrays;
import java.util.Optional;

/**
 * A League server. Rank and summoner data live on the platform host (tr1.api.riotgames.com), while
 * accounts and matches are served from the wider regional host (europe.api.riotgames.com).
 */
public enum LolPlatform {
    TR1("tr1", "europe", "europe"),
    EUW1("euw1", "europe", "europe"),
    EUN1("eun1", "europe", "europe"),
    RU("ru", "europe", "europe"),
    ME1("me1", "europe", "europe"),
    NA1("na1", "americas", "americas"),
    BR1("br1", "americas", "americas"),
    LA1("la1", "americas", "americas"),
    LA2("la2", "americas", "americas"),
    KR("kr", "asia", "asia"),
    JP1("jp1", "asia", "asia"),
    OC1("oc1", "americas", "sea");

    private final String host;
    private final String accountRegion;
    private final String matchRegion;

    LolPlatform(String host, String accountRegion, String matchRegion) {
        this.host = host;
        this.accountRegion = accountRegion;
        this.matchRegion = matchRegion;
    }

    public String host() {
        return host;
    }

    public String accountRegion() {
        return accountRegion;
    }

    public String matchRegion() {
        return matchRegion;
    }

    public static Optional<LolPlatform> parse(String value) {
        return Arrays.stream(values()).filter(p -> p.name().equalsIgnoreCase(value)).findFirst();
    }
}
