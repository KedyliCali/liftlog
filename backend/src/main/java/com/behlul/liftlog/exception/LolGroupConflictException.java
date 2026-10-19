package com.behlul.liftlog.exception;

/**
 * A League group rule was broken (409). The rule travels in fieldErrors.group, so the app shows its own
 * Turkish or English text instead of guessing which conflict it got.
 */
public class LolGroupConflictException extends ConflictException {

    public enum Rule { ALREADY_IN_GROUP, GROUP_FULL, ACCOUNT_TAKEN, CANNOT_REMOVE_SELF }

    private final Rule rule;

    public LolGroupConflictException(Rule rule, String message) {
        super(message);
        this.rule = rule;
    }

    public Rule rule() {
        return rule;
    }
}
