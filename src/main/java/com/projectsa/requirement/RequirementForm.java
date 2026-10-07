package com.projectsa.requirement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

/** UC06 form. "Due date ≥ today" and the attachment rules are checked in RequirementService. */
public class RequirementForm {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "At most 200 characters")
    private String title;

    @NotBlank(message = "Detail is required")
    @Size(max = 4000, message = "At most 4000 characters")
    private String detail;

    @NotNull(message = "Please choose a priority")
    private Priority priority;

    @NotNull(message = "Customer due date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueDate;

    @Size(max = 200, message = "At most 200 characters")
    private String relatedFeature;

    /** Optional; an empty file input still sends one empty part, see {@link #uploadedAttachments()}. */
    private List<MultipartFile> attachments = new ArrayList<>();

    /** The attachments that actually contain a file. */
    public List<MultipartFile> uploadedAttachments() {
        return attachments == null ? List.of()
                : attachments.stream().filter(f -> f != null && !f.isEmpty()).toList();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getRelatedFeature() {
        return relatedFeature;
    }

    public void setRelatedFeature(String relatedFeature) {
        this.relatedFeature = relatedFeature;
    }

    public List<MultipartFile> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<MultipartFile> attachments) {
        this.attachments = attachments;
    }
}
