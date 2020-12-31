package com.artisan.developer.monitor;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

public class MonitorMessage {

    private AtomicInteger count = new AtomicInteger(0);

    private LocalDateTime firstOccurTime;

    private LocalDateTime lastOccurTime;

    private LocalDateTime firstMonitorTime;

    private LocalDateTime lastMonitorTime;

    public MonitorMessage() {
        this.firstOccurTime = LocalDateTime.now();
    }

    public AtomicInteger getCount() {
        return count;
    }

    public void setCount(AtomicInteger count) {
        this.count = count;
    }

    public LocalDateTime getFirstOccurTime() {
        return firstOccurTime;
    }

    public void setFirstOccurTime(LocalDateTime firstOccurTime) {
        this.firstOccurTime = firstOccurTime;
    }

    public LocalDateTime getLastOccurTime() {
        return lastOccurTime;
    }

    public void setLastOccurTime(LocalDateTime lastOccurTime) {
        this.lastOccurTime = lastOccurTime;
    }

    public LocalDateTime getFirstMonitorTime() {
        return firstMonitorTime;
    }

    public void setFirstMonitorTime(LocalDateTime firstMonitorTime) {
        this.firstMonitorTime = firstMonitorTime;
    }

    public LocalDateTime getLastMonitorTime() {
        return lastMonitorTime;
    }

    public void setLastMonitorTime(LocalDateTime lastMonitorTime) {
        this.lastMonitorTime = lastMonitorTime;
    }

}
