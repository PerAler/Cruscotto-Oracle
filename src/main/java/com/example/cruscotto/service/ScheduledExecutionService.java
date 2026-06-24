package com.example.cruscotto.service;

import com.example.cruscotto.model.ScheduledJobInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ScheduledExecutionService {
    private static final Logger log = LoggerFactory.getLogger(ScheduledExecutionService.class);
    private static final DateTimeFormatter LOCAL_DATETIME_LABEL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TaskScheduler taskScheduler;
    private final OracleProcedureExecutorService executorService;

    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();
    private final Map<String, ScheduledJobInfo> jobs = new ConcurrentHashMap<>();

    public ScheduledExecutionService(TaskScheduler taskScheduler, OracleProcedureExecutorService executorService) {
        this.taskScheduler = taskScheduler;
        this.executorService = executorService;
    }

    public synchronized void schedule(String procedureName, String cronExpression, Map<String, Object> params) {
        cancel(procedureName);

        Map<String, Object> safeParams = new LinkedHashMap<>(params == null ? Map.of() : params);
        Runnable task = () -> executeScheduledProcedure(procedureName, safeParams, false);
        ScheduledFuture<?> future = taskScheduler.schedule(task, new CronTrigger(cronExpression));
        if (future == null) {
            throw new IllegalStateException("Impossibile pianificare la procedura " + procedureName);
        }

        futures.put(procedureName, future);
        jobs.put(procedureName, new ScheduledJobInfo(
                procedureName,
                "CRON",
                cronExpression,
                safeParams,
                "ACTIVE",
                null,
                null));
    }

    public void scheduleOnce(String procedureName, Instant runAt, Map<String, Object> params) {
        synchronized (this) {
            cancel(procedureName);
        }

        Map<String, Object> safeParams = new LinkedHashMap<>(params == null ? Map.of() : params);
        AtomicReference<ScheduledFuture<?>> ownFutureRef = new AtomicReference<>();

        Runnable task = () -> executeScheduledProcedure(procedureName, safeParams, true, ownFutureRef);

        ScheduledFuture<?> future = taskScheduler.schedule(task, Date.from(runAt));
        if (future == null) {
            throw new IllegalStateException("Impossibile pianificare il lancio singolo della procedura " + procedureName);
        }

        ownFutureRef.set(future);
        synchronized (this) {
            futures.put(procedureName, future);
            jobs.put(procedureName, new ScheduledJobInfo(
                    procedureName,
                    "ONCE",
                    runAt.atZone(ZoneId.systemDefault()).format(LOCAL_DATETIME_LABEL),
                    safeParams,
                    "PENDING",
                    null,
                    null));
        }
    }

    public synchronized void cancel(String procedureName) {
        ScheduledFuture<?> future = futures.remove(procedureName);
        if (future != null) {
            future.cancel(false);
        }
        jobs.remove(procedureName);
    }

    public List<ScheduledJobInfo> listJobs() {
        return jobs.values().stream().sorted((a, b) -> a.procedureName().compareToIgnoreCase(b.procedureName())).toList();
    }

    private void executeScheduledProcedure(String procedureName, Map<String, Object> params, boolean removeAfterRun) {
        executeScheduledProcedure(procedureName, params, removeAfterRun, null);
    }

    private void executeScheduledProcedure(String procedureName,
                                           Map<String, Object> params,
                                           boolean removeAfterRun,
                                           AtomicReference<ScheduledFuture<?>> ownFutureRef) {
        Instant startedAt = Instant.now();
        boolean success = false;
        markRunning(procedureName, startedAt);
        try {
            executorService.runProcedure(procedureName, params);
            success = true;
        } catch (Exception ex) {
            log.error("Errore esecuzione schedulata {}: {}", procedureName, ex.getMessage(), ex);
        } finally {
            long durationMillis = Math.max(0, java.time.Duration.between(startedAt, Instant.now()).toMillis());
            synchronized (this) {
                if (removeAfterRun) {
                    ScheduledFuture<?> current = futures.get(procedureName);
                    if (ownFutureRef == null || current == ownFutureRef.get()) {
                        futures.remove(procedureName);
                        jobs.remove(procedureName);
                    }
                } else if (success) {
                    markIdle(procedureName, durationMillis);
                } else {
                    markError(procedureName, durationMillis);
                }
            }
        }
    }

    private synchronized void markError(String procedureName, Long lastDurationMillis) {
        ScheduledJobInfo current = jobs.get(procedureName);
        if (current == null) {
            return;
        }
        jobs.put(procedureName, new ScheduledJobInfo(
                current.procedureName(),
                current.scheduleType(),
                current.scheduleExpression(),
                current.parameters(),
                "ERROR",
                null,
                lastDurationMillis != null ? lastDurationMillis : current.lastDurationMillis()));
    }

    private synchronized void markRunning(String procedureName, Instant startedAt) {
        ScheduledJobInfo current = jobs.get(procedureName);
        if (current == null) {
            return;
        }
        jobs.put(procedureName, new ScheduledJobInfo(
                current.procedureName(),
                current.scheduleType(),
                current.scheduleExpression(),
                current.parameters(),
                "RUNNING",
                startedAt,
                current.lastDurationMillis()));
    }

    private synchronized void markIdle(String procedureName, long lastDurationMillis) {
        ScheduledJobInfo current = jobs.get(procedureName);
        if (current == null) {
            return;
        }
        jobs.put(procedureName, new ScheduledJobInfo(
                current.procedureName(),
                current.scheduleType(),
                current.scheduleExpression(),
                current.parameters(),
                "ACTIVE",
                null,
                lastDurationMillis));
    }
}
