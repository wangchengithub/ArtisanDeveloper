package com.artisan.developer.monitor.utils;

import java.util.List;
import java.util.Map;

public class HttpResponseData {

    private String body;

    private Map<String, List<String>> headers;

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, List<String>> headers) {
        this.headers = headers;
    }

}
