package com.behlul.liftlog.exception;

public class RiotIdNotFoundException extends RuntimeException {

    public RiotIdNotFoundException(String riotId, String platform) {
        super("Riot ID " + riotId + " was not found on " + platform);
    }
}
