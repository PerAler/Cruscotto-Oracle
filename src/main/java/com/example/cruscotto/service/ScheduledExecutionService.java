package com.example.cruscotto.service;

import com.example.cruscotto.model.ScheduledJobInfo;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ScheduledExecutionService {

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
        Runnable task = () -> executorService.runProcedure(procedureName, safeParams);
        ScheduledFuture<?> future = taskScheduler.schedule(task, new CronTrigger(cronExpression));
        if (future == null) {
            throw new IllegalStateException("Impossibile pianificare la procedura " + procedureName);
        }

        futures.put(procedureName, future);
        jobs.put(procedureName, new ScheduledJobInfo(procedureName, "CRON", cronExpression, safeParams));
    }

    public void scheduleOnce(String procedureName, Instant runAt, Map<String, Object> params) {
        synchronized (this) {
            cancel(procedureName);
        }

        Map<String, Object> safeParams = new LinkedHashMap<>(params == null ? Map.of() : params);
        AtomicReference<ScheduledFuture<?>> ownFutureRef = new AtomicReference<>();

        Runnable task = () -> {
            try {
                executorService.runProcedure(procedureName, safeParams);
            } finally {
                synchronized (ScheduledExecutionService.this) {
                    ScheduledFuture<?> current = futures.get(procedureName);
                    if (current == ownFutureRef.get()) {
                        futures.remove(procedureName);
                        jobs.remove(procedureName);
                    }
                }
            }
        };

        ScheduledFuture<?> future = taskScheduler.schedule(task, Date.from(runAt));
        if (future == null) {
            throw new IllegalStateException("Impossibile pianificare il lancio singolo della procedura " + procedureName);
        }

        ownFutureRef.set(future);
        synchronized (this) {
            futures.put(procedureName, future);
            jobs.put(procedureName, new ScheduledJobInfo(procedureName, "ONCE", runAt.toString(), safeParams));
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
}
