package com.artisan.developer.monitor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.artisan.developer.monitor.rule.AlwaysSendRule;
import com.artisan.developer.monitor.rule.MonitorRule;
import com.artisan.developer.monitor.sender.MonitorSender;

import static com.artisan.developer.monitor.utils.DateTimeUtils.YYYY_MM_DD_HH_MM_SS;

public class Monitor {

    protected final Map<String, MonitorMessage> monitorMessageMap = new ConcurrentHashMap<>();

    protected final Map<String, MonitorRule> monitorCodeRuleMap = new ConcurrentHashMap<>();

    protected MonitorSender sender = null;

    // TODO: 2020/11/27 定义线程池的名字
    // 发送的任务数最大为maxMonitorMessageSize的两倍
    private final ExecutorService senderExecutorService = Executors.newSingleThreadScheduledExecutor();

    private static final MonitorRule DEFAULT_MONITOR_RULE = new AlwaysSendRule();

    private String appName = "Monitor";

    private int maxMonitorMessageSize = 1000;

    private boolean testMode = false;

    public void init() {
        if (sender == null) {
            throw new MonitorException("sender cannot be null");
        }
    }

    public void monitor(String monitorCode) {
        monitor(Collections.singletonList(monitorCode));
    }

    public void monitor(Collection<String> monitorCodes) {
        if (testMode) {
            // TODO: 2020/12/1 打个日志
            return;
        }
        synchronized (monitorMessageMap) {
            clearInvalidMessage();
            if (monitorMessageMap.size() >= maxMonitorMessageSize) {
                // TODO: 2020/11/30 打点日志
                return;
            }

            for (String monitorCode : monitorCodes) {
                LocalDateTime now = LocalDateTime.now();
                MonitorMessage monitorMessage = monitorMessageMap.computeIfAbsent(monitorCode, key -> new MonitorMessage());
                monitorMessage.getCount().incrementAndGet();
                monitorMessage.setLastOccurTime(now);

                MonitorRule monitorRule = monitorCodeRuleMap.getOrDefault(monitorCode, DEFAULT_MONITOR_RULE);
                if (monitorRule.shouldMonitor(monitorMessage)) {
                    // TODO: 2020/12/2 如果发送的时候再设置时间，fixDelay规则下，在发送前进来两次就会发两次
                    // TODO: 2020/12/3 先记时间，再发送，如果发送有问题可能导致发送不出来
                    if (monitorMessage.getFirstMonitorTime() == null) {
                        monitorMessage.setFirstMonitorTime(now);
                    }
                    monitorMessage.setLastMonitorTime(now);
                    send(monitorCode);
                }
            }
        }
    }

    private void clearInvalidMessage() {
        Iterator<Map.Entry<String, MonitorMessage>> it = monitorMessageMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, MonitorMessage> entry = it.next();
            String monitorCode = entry.getKey();
            MonitorMessage message = entry.getValue();
            MonitorRule rule = monitorCodeRuleMap.getOrDefault(monitorCode, DEFAULT_MONITOR_RULE);
            if (rule.removable(message)) {
                it.remove();
            }
        }
    }

    protected String generateMessage(String message) {
        return "监控报警：" + appName + "\n告警时间：" + YYYY_MM_DD_HH_MM_SS.format(LocalDateTime.now()) + "\n告警内容：" + message;
    }

    protected void sendMessage(String message) {
        senderExecutorService.submit(() -> {
            sender.send(generateMessage(message));
        });
    }

    protected void send(String monitorCode) {
        senderExecutorService.submit(() -> {
            int retryLimit = 3;
            for (int retry = 1; retry <= retryLimit; retry++) {
                try {
                    sender.send(generateMessage(monitorCode + " 发生异常"));
                    return;
                } catch (Exception e) {
                    if (retry >= retryLimit) {
                        // TODO: 2020/12/3 加日志
                        throw e;
                    }
                }
            }
        });
    }

    public void registerRule(String monitorCode, MonitorRule monitorRule) {
        monitorCodeRuleMap.put(monitorCode, monitorRule);
    }

    public void setSender(MonitorSender sender) {
        this.sender = sender;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public void setMaxMonitorMessageSize(int maxMonitorMessageSize) {
        this.maxMonitorMessageSize = maxMonitorMessageSize;
    }

    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }
}
