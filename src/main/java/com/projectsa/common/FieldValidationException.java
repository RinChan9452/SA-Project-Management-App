package com.projectsa.common;

import java.util.List;
import java.util.function.Supplier;
import org.springframework.validation.BindingResult;

/**
 * Business-rule failures tied to form fields, so the controller can show each message next to its field.
 * Services collect every failure they find and throw once, so the user sees all problems in one round.
 */
public class FieldValidationException extends RuntimeException {

    /** One message for one form field. */
    public record FieldMessage(String field, String message) {
    }

    private final List<FieldMessage> errors;

    public FieldValidationException(String field, String message) {
        this(List.of(new FieldMessage(field, message)));
    }

    public FieldValidationException(List<FieldMessage> errors) {
        super(errors.getFirst().message());
        this.errors = List.copyOf(errors);
    }

    /** Throws when {@code errors} is not empty. */
    public static void throwIfAny(List<FieldMessage> errors) {
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    /** Runs a check; if it fails, adds its messages to {@code errors} and returns null instead of throwing. */
    public static <T> T collect(List<FieldMessage> errors, Supplier<T> check) {
        try {
            return check.get();
        } catch (FieldValidationException e) {
            errors.addAll(e.errors);
            return null;
        }
    }

    /** Field of the first failure. */
    public String getField() {
        return errors.getFirst().field();
    }

    public List<FieldMessage> getErrors() {
        return errors;
    }

    /**
     * Shows the messages on the form. A field that already has an error (e.g. from {@code @NotBlank})
     * keeps only that one, so the same problem is never reported twice.
     */
    public void rejectInto(BindingResult result) {
        for (FieldMessage e : errors) {
            if (!result.hasFieldErrors(e.field())) {
                result.rejectValue(e.field(), "invalid", e.message());
            }
        }
    }
}
