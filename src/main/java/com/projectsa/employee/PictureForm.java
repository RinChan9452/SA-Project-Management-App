package com.projectsa.employee;

import org.springframework.web.multipart.MultipartFile;

/** F4: upload a new profile picture. The rules are checked in {@link ProfileService}. */
public class PictureForm {

    private MultipartFile picture;

    public MultipartFile getPicture() {
        return picture;
    }

    public void setPicture(MultipartFile picture) {
        this.picture = picture;
    }
}
