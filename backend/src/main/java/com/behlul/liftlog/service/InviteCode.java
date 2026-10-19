package com.behlul.liftlog.service;

import java.util.Locale;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * League group invite codes: 10 symbols from a 31-symbol alphabet without look-alikes (no 0/O, 1/I/L).
 * 31^10 is about 8.2e14 codes (49.5 bits), so guessing one is hopeless without needing an attempt limiter.
 */
final class InviteCode {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 10;

    private InviteCode() {
    }

    /** nextInt(bound) rejection-samples, so every symbol is equally likely (no modulo bias). */
    static String generate(RandomGenerator random) {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * Accepts the code as typed or shared: any case, with the display dash or spaces. Locale.ROOT, so a Turkish
     * phone never turns i into a dotted capital İ. Empty when the text cannot be a code at all.
     */
    static Optional<String> normalize(String typed) {
        String code = typed.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        if (code.length() != LENGTH || !code.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0)) {
            return Optional.empty();
        }
        return Optional.of(code);
    }
}
