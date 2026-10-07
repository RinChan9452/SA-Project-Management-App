package com.projectsa.employee;

import java.util.Locale;

public final class Emails {

    private Emails() {
    }

    /** Emails are compared case-insensitively, so they are always stored trimmed and lower-case. */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
