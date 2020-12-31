package com.artisan.developer.monitor;

public class MonitorException extends RuntimeException {

    private String errorMsg;

    public MonitorException(String errorMsg) {
        super(errorMsg);
        this.errorMsg = errorMsg;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }
}
