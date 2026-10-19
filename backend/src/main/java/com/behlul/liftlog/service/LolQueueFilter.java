package com.behlul.liftlog.service;

/** Which games the detailed stats count, by Riot queue id. */
public enum LolQueueFilter {
    ALL, SOLO, FLEX, ARAM, OTHER;

    static final int SOLO_QUEUE_ID = 420;
    static final int FLEX_QUEUE_ID = 440;
    static final int ARAM_QUEUE_ID = 450;

    public boolean includes(int queueId) {
        return switch (this) {
            case ALL -> true;
            case SOLO -> queueId == SOLO_QUEUE_ID;
            case FLEX -> queueId == FLEX_QUEUE_ID;
            case ARAM -> queueId == ARAM_QUEUE_ID;
            // Normals, Arena, event modes: everything the chips above do not name
            case OTHER -> queueId != SOLO_QUEUE_ID && queueId != FLEX_QUEUE_ID && queueId != ARAM_QUEUE_ID;
        };
    }
}
