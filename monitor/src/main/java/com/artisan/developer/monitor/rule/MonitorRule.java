package com.artisan.developer.monitor.rule;

import com.artisan.developer.monitor.MonitorMessage;

public interface MonitorRule {

    boolean shouldMonitor(MonitorMessage message);

    boolean removable(MonitorMessage message);

}
