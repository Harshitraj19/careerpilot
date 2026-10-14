package com.placify.web.gateway;

import java.util.Map;

/** An error answer from the API (status + the platform's error code and message). */
public class GatewayException extends RuntimeException {

    private final int status;
    private final String code;
    private final Map<String, String> fieldErrors;

    public GatewayException(int status, String code, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors == null ? Map.of() : fieldErrors;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    /** Message plus the first field error, for showing on a form. */
    public String display() {
        if (fieldErrors.isEmpty()) {
            return getMessage();
        }
        Map.Entry<String, String> first = fieldErrors.entrySet().iterator().next();
        return getMessage() + " (" + first.getKey() + ": " + first.getValue() + ")";
    }
}
