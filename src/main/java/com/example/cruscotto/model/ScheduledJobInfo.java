package com.example.cruscotto.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public record ScheduledJobInfo(String procedureName,
							   String scheduleType,
							   String scheduleExpression,
							   Map<String, Object> parameters,
                               String status,
                               Instant runningSince,
                               Long lastDurationMillis) {

    public String statusLabel() {
        if ("RUNNING".equalsIgnoreCase(status)) {
            return "IN ESECUZIONE";
        }
        if ("PENDING".equalsIgnoreCase(status)) {
            return "IN ATTESA";
        }
        if ("ERROR".equalsIgnoreCase(status)) {
            return "ERRORE";
        }
        return "ATTIVA";
    }

    public String runningTimeLabel() {
        if ("RUNNING".equalsIgnoreCase(status) && runningSince != null) {
            Duration running = Duration.between(runningSince, Instant.now());
            if (running.isNegative()) {
                running = Duration.ZERO;
            }
            return formatDuration(running);
        }
        if (lastDurationMillis != null && lastDurationMillis > 0) {
            return "ultimo: " + formatDuration(Duration.ofMillis(lastDurationMillis));
        }
        return "-";
    }

    private String formatDuration(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format("%dh %02dm %02ds", hours, minutes, secs);
        }
        return String.format("%dm %02ds", minutes, secs);
    }
}
