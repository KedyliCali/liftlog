package com.behlul.liftlog.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageFormatTest {

    @Test
    void detectsFormatsFromMagicBytes() {
        assertThat(ImageFormat.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00})).contains(ImageFormat.JPEG);
        assertThat(ImageFormat.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0})).contains(ImageFormat.PNG);
        assertThat(ImageFormat.detect("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1))).contains(ImageFormat.WEBP);
    }

    @Test
    void rejectsNonImagesEvenIfTheyClaimToBeOne() {
        assertThat(ImageFormat.detect("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageFormat.detect(new byte[]{(byte) 0xFF, (byte) 0xD8})).isEmpty(); // truncated
        assertThat(ImageFormat.detect(new byte[0])).isEmpty();
    }
}
