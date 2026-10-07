package com.projectsa.document;

import java.util.Arrays;

/** File types the app accepts, recognised by their first bytes (not by the file name). */
public enum FileKind {
    PDF("application/pdf", ".pdf", new byte[] {'%', 'P', 'D', 'F', '-'}),
    PNG("image/png", ".png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
    JPG("image/jpeg", ".jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

    private final String mime;
    private final String extension;
    private final byte[] signature;

    FileKind(String mime, String extension, byte[] signature) {
        this.mime = mime;
        this.extension = extension;
        this.signature = signature;
    }

    public String getMime() {
        return mime;
    }

    public String getExtension() {
        return extension;
    }

    boolean matches(byte[] head) {
        return head.length >= signature.length
                && Arrays.equals(Arrays.copyOf(head, signature.length), signature);
    }

    static int longestSignature() {
        return Arrays.stream(values()).mapToInt(k -> k.signature.length).max().orElse(0);
    }
}
