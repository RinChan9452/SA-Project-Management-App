package com.projectsa.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** F4: change my name. */
public class NameForm {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
