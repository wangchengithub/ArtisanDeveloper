package com.artisan.developer.monitor.sender;

import com.artisan.developer.monitor.utils.StringUtils;
import com.artisan.developer.monitor.utils.WebUtils;

// TODO: 2020/11/30 支持叮叮的监控规则
public class DingTalkMonitorSender implements MonitorSender {

    private int connectTimeout = 60000; // 默认连接超时时间为60秒

    private int readTimeout = 60000; // 默认响应超时时间为60秒

    private String url = "";

    public DingTalkMonitorSender(String url) {
        StringUtils.checkNotEmpty(url);
        this.url = url;
    }

    @Override
    public void send(String message) {
        String content = "{\"msgtype\":\"text\",\"text\":{\"content\":\"" + message + "\"}}";
        try {
            WebUtils.doPost(url, content, connectTimeout, readTimeout);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
