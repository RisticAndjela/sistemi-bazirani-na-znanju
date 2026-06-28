package com.sbnz.frontend.web;

public class ApiResponse {
    public boolean success;
    public Object data;
    public String message;

    public ApiResponse(boolean success, Object data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }
}
