package com.artisan.developer.monitor;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.artisan.developer.monitor.rule.ManualRemoveRule;

public class ScheduledMonitor extends Monitor {

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    private SchedulerMonitorProcessor schedulerMonitorProcessor = null;

    private final Set<String> lastMonitoredCodeSet = new HashSet<>();

    private int fixedDelay = 60;

    private TimeUnit fixedDelayTimeUnit = TimeUnit.SECONDS;

    private static final ManualRemoveRule DEFAULT_SCHEDULED_RULE = new ManualRemoveRule();

    public void init() {
        super.init();

        if (schedulerMonitorProcessor == null) {
            throw new MonitorException("schedulerMonitorProcessor cannot be null");
        }

        scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                scheduledMonitor();
            } catch (Throwable e) {
                // TODO: 2020/11/30 先打印日志 sender 异常？
                send("定时轮询监控告警异常");
            }
        }, 0, fixedDelay, fixedDelayTimeUnit);
    }

    private void scheduledMonitor() {
        Set<String> monitorCodes = schedulerMonitorProcessor.monitor();
        synchronized (monitorCodeRuleMap) {
            monitorCodes.forEach(code -> {
                monitorCodeRuleMap.computeIfAbsent(code, k -> DEFAULT_SCHEDULED_RULE);
            });
        }
        monitor(monitorCodes);

        Set<String> recovered = lastMonitoredCodeSet.stream().filter(c -> !monitorCodes.contains(c)).collect(Collectors.toSet());
        lastMonitoredCodeSet.addAll(monitorCodes);

        if (recovered.size() > 0) {
            synchronized (monitorMessageMap) {
                recovered.forEach(code -> {
                    monitorMessageMap.remove(code);
                    lastMonitoredCodeSet.remove(code);
                    sendMessage(code + " 已恢复");
                });
            }
        }
    }

    public void setSchedulerMonitorProcessor(SchedulerMonitorProcessor schedulerMonitorProcessor) {
        this.schedulerMonitorProcessor = schedulerMonitorProcessor;
    }

    public void setFixedDelay(int fixedDelay) {
        this.fixedDelay = fixedDelay;
    }

    public void setFixedDelayTimeUnit(TimeUnit fixedDelayTimeUnit) {
        this.fixedDelayTimeUnit = fixedDelayTimeUnit;
    }
}
