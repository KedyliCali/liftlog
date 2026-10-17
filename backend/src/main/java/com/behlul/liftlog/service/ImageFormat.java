package com.behlul.liftlog.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * Identifies an image by its leading "magic" bytes. The Content-Type header is chosen by the client
 * and can lie, so the stored and served type always comes from the actual file contents.
 */
public enum ImageFormat {
    JPEG("image/jpeg"),
    PNG("image/png"),
    WEBP("image/webp");

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final String mediaType;

    ImageFormat(String mediaType) {
        this.mediaType = mediaType;
    }

    public String mediaType() {
        return mediaType;
    }

    public static Optional<ImageFormat> detect(byte[] data) {
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (data.length >= PNG_SIGNATURE.length && Arrays.equals(data, 0, PNG_SIGNATURE.length, PNG_SIGNATURE, 0, PNG_SIGNATURE.length)) {
            return Optional.of(PNG);
        }
        // WebP: "RIFF" <4-byte size> "WEBP"
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }
}
