package com.behlul.liftlog.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class InviteCodeTest {

    private static List<String> codes(long seed, int count) {
        Random random = new Random(seed);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            codes.add(InviteCode.generate(random));
        }
        return codes;
    }

    @Test
    void generatesTenSymbolsFromTheAlphabet() {
        List<String> codes = codes(42, 1_000);

        assertThat(codes).allSatisfy(code -> assertThat(code).matches("^[A-HJKMNP-Z2-9]{10}$"));
        assertThat(codes).doesNotHaveDuplicates();
        // Same seed, same codes: generation depends only on the random source
        assertThat(codes(42, 1_000)).isEqualTo(codes);
    }

    @Test
    void normalizesCaseDashesAndSpaces() {
        assertThat(InviteCode.normalize("k7qxm-2pfd9")).contains("K7QXM2PFD9");
        assertThat(InviteCode.normalize(" K7QXM 2PFD9 ")).contains("K7QXM2PFD9");
        assertThat(InviteCode.normalize("abcde\nfghjk")).contains("ABCDEFGHJK");
    }

    @Test
    void rejectsLookAlikesAndWrongLengths() {
        assertThat(InviteCode.normalize("K7QXM-2PFD0")).isEmpty();
        assertThat(InviteCode.normalize("K7QXO2PFD9")).isEmpty();
        assertThat(InviteCode.normalize("K7QXI2PFD9")).isEmpty();
        assertThat(InviteCode.normalize("ABC")).isEmpty();
        assertThat(InviteCode.normalize("K7QXM2PFD9A")).isEmpty();
        assertThat(InviteCode.normalize("")).isEmpty();
    }
}
