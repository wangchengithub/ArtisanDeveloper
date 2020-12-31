package com.artisan.developer.monitor.rule;

import com.artisan.developer.monitor.MonitorMessage;

// TODO: 2020/12/1 提供个单例
public class ManualRemoveRule implements MonitorRule {

    @Override
    public boolean shouldMonitor(MonitorMessage message) {
        return message.getLastMonitorTime() == null;
    }

    @Override
    public boolean removable(MonitorMessage message) {
        return false;
    }
}
