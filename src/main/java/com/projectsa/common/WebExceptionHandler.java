package com.projectsa.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.support.RequestContextUtils;

/** Turns "not allowed right now" errors into a red message on the dashboard instead of an error page. */
@ControllerAdvice
public class WebExceptionHandler {

    @ExceptionHandler(ActionNotAllowedException.class)
    String notAllowed(ActionNotAllowedException e, HttpServletRequest request) {
        return backToDashboard(request, e.getMessage());
    }

    /** Two people saved the same project at the same moment (@Version); the second one is refused. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    String changedMeanwhile(HttpServletRequest request) {
        return backToDashboard(request, "This project was just changed by someone else. Please open it again.");
    }

    /** Several files that are each allowed can still be too large together, so both limits are named. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    String tooLarge(HttpServletRequest request) {
        return backToDashboard(request,
                "The upload is too large. Each file can be at most 50 MB, and all files sent together at most 110 MB.");
    }

    private static String backToDashboard(HttpServletRequest request, String message) {
        RequestContextUtils.getOutputFlashMap(request).put("error", message);
        return "redirect:/dashboard";
    }
}
