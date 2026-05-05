package com.example.cruscotto.service;

import com.example.cruscotto.model.ExecutionLogEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Service
public class ExecutionLogService {

    private final int maxSize;
    private final ExecutionLogOracleStore oracleStore;
    private final Deque<ExecutionLogEntry> logBuffer = new ArrayDeque<>();

    public ExecutionLogService(@Value("${app.logs.max-size:300}") int maxSize,
                               ExecutionLogOracleStore oracleStore) {
        this.maxSize = maxSize;
        this.oracleStore = oracleStore;
    }

    @PostConstruct
    public synchronized void warmupFromPersistentStore() {
        List<ExecutionLogEntry> persisted = oracleStore.loadLatest(maxSize);
        logBuffer.clear();
        for (ExecutionLogEntry entry : persisted) {
            // I record arrivano dal DB dal più recente al meno recente.
            logBuffer.addLast(entry);
        }
    }

    public synchronized void add(ExecutionLogEntry entry) {
        logBuffer.addFirst(entry);
        while (logBuffer.size() > maxSize) {
            logBuffer.removeLast();
        }
        oracleStore.persist(entry);
    }

    public synchronized List<ExecutionLogEntry> latest() {
        return new ArrayList<>(logBuffer);
    }

    public synchronized void clearErrorsForProcedure(String procedureName) {
        logBuffer.removeIf(e -> "KO".equals(e.status()) && procedureName.equals(e.procedureName()));
    }
}
