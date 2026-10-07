package com.projectsa.employee;

import java.nio.charset.StandardCharsets;

public final class Passwords {

    /** BCrypt refuses longer passwords (counted in UTF-8 bytes, so a Thai letter counts as 3). */
    public static final int MAX_BYTES = 72;

    public static final String TOO_LONG = "Password is too long (at most 72 characters; a Thai letter counts as 3)";

    private Passwords() {
    }

    /** Whether BCrypt would refuse to hash this password. */
    public static boolean isTooLong(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }
}
