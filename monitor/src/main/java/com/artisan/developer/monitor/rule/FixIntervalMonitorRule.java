package com.artisan.developer.monitor.rule;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import com.artisan.developer.monitor.MonitorMessage;

public class FixIntervalMonitorRule implements MonitorRule {

    private int interval = 300;

    private TimeUnit timeUnit = TimeUnit.SECONDS;

    public FixIntervalMonitorRule() {
    }

    public FixIntervalMonitorRule(int interval) {
        this.interval = interval;
    }

    public FixIntervalMonitorRule(int interval, TimeUnit timeUnit) {
        this.interval = interval;
        this.timeUnit = timeUnit;
    }

    @Override
    public boolean shouldMonitor(MonitorMessage monitorMessage) {
        LocalDateTime now = LocalDateTime.now();
        return monitorMessage == null || monitorMessage.getLastMonitorTime() == null
                || ChronoUnit.SECONDS.between(monitorMessage.getLastMonitorTime(), now) > timeUnit.toSeconds(interval);
    }

    @Override
    public boolean removable(MonitorMessage message) {
        return shouldMonitor(message);
    }
}
