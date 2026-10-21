package com.behlul.liftlog.service;

import java.util.Arrays;
import java.util.Optional;

/** The two ranked queues with a rank: Riot's league entry name and the match queue id of each. */
public enum LolRankQueue {
    SOLO("RANKED_SOLO_5x5", 420),
    FLEX("RANKED_FLEX_SR", 440);

    private final String riotName;
    private final int queueId;

    LolRankQueue(String riotName, int queueId) {
        this.riotName = riotName;
        this.queueId = queueId;
    }

    public String riotName() {
        return riotName;
    }

    public int queueId() {
        return queueId;
    }

    public static Optional<LolRankQueue> ofRiotName(String queueType) {
        return Arrays.stream(values()).filter(q -> q.riotName.equals(queueType)).findFirst();
    }
}
