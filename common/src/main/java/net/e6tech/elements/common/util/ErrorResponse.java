package net.e6tech.elements.common.util;

/**
 * Created by futeh.
 */
public class ErrorResponse {

    private String responseCode;
    private String message;

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String errorCode) {
        this.responseCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
