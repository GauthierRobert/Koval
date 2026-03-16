package com.koval.trainingplannerbackend.config.exceptions;

public class ResourceNotFoundException extends RuntimeException {

    private final String code;

    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(resourceType + " not found: " + resourceId);
        this.code = "RESOURCE_NOT_FOUND";
    }

    public ResourceNotFoundException(String message) {
        super(message);
        this.code = "RESOURCE_NOT_FOUND";
    }

    public String getCode() {
        return code;
    }
}
