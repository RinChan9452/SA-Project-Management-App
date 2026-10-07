package com.projectsa.document;

public enum DocumentType {
    SOLUTION("Solution"),
    PRODUCT_REQUIREMENT("Product Requirement"),
    REQUIREMENT_ATTACHMENT("Requirement Attachment");

    private final String label;

    DocumentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
