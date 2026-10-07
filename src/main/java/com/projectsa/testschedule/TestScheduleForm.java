package com.projectsa.testschedule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/** UC05 form. "Must be in the future" is checked in TestScheduleService. */
public class TestScheduleForm {

    /** Matches what {@code <input type="datetime-local">} sends (with or without seconds). */
    @NotNull(message = "Test date and time is required")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm", fallbackPatterns = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime testAt;

    @NotBlank(message = "Test details are required")
    @Size(max = 2000, message = "At most 2000 characters")
    private String detail;

    public LocalDateTime getTestAt() {
        return testAt;
    }

    public void setTestAt(LocalDateTime testAt) {
        this.testAt = testAt;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
