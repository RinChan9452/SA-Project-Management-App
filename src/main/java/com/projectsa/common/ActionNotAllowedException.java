package com.projectsa.common;

/**
 * The action is not allowed right now, e.g. the project is in the wrong state (§6).
 * {@link WebExceptionHandler} shows the message on the dashboard.
 */
public class ActionNotAllowedException extends RuntimeException {

    public ActionNotAllowedException(String message) {
        super(message);
    }
}
