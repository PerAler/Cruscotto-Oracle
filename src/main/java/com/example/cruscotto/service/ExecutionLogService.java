package com.example.cruscotto.service;

import com.example.cruscotto.model.ExecutionLogEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

@Service
public class ExecutionLogService {

    private final int maxSize;
    private final ExecutionLogFileStore fileStore;
    private final Deque<ExecutionLogEntry> logBuffer = new ArrayDeque<>();

    public ExecutionLogService(@Value("${app.logs.max-size:100}") int maxSize,
                               ExecutionLogFileStore fileStore) {
        this.maxSize = maxSize;
        this.fileStore = fileStore;
    }

    @PostConstruct
    public synchronized void warmupFromPersistentStore() {
        fileStore.trimToMaxRows(maxSize);
        List<ExecutionLogEntry> persisted = fileStore.loadLatest(maxSize);
        logBuffer.clear();
        for (ExecutionLogEntry entry : persisted) {
            // I record arrivano dallo store file dal più recente al meno recente.
            logBuffer.addLast(entry);
        }
    }

    public synchronized void add(ExecutionLogEntry entry) {
        logBuffer.addFirst(entry);
        while (logBuffer.size() > maxSize) {
            logBuffer.removeLast();
        }
        fileStore.persist(entry);
        fileStore.trimToMaxRows(maxSize);
    }

    public synchronized List<ExecutionLogEntry> latest() {
        return new ArrayList<>(logBuffer);
    }

    public synchronized void clearErrorsForProcedure(String procedureName) {
        logBuffer.removeIf(e -> "KO".equals(e.status()) && procedureName.equals(e.procedureName()));
        fileStore.deleteErrorsForProcedure(procedureName);
    }

    public synchronized boolean deleteErrorAtIndex(int errorIndex) {
        if (errorIndex < 0) {
            return false;
        }

        int currentErrorIndex = 0;
        Iterator<ExecutionLogEntry> iterator = logBuffer.iterator();
        while (iterator.hasNext()) {
            ExecutionLogEntry entry = iterator.next();
            if (!"KO".equals(entry.status())) {
                continue;
            }

            if (currentErrorIndex == errorIndex) {
                iterator.remove();
                fileStore.deleteOne(entry);
                return true;
            }
            currentErrorIndex++;
        }

        return false;
    }

    public synchronized int deleteAllErrors() {
        int removedInMemory = 0;
        Iterator<ExecutionLogEntry> iterator = logBuffer.iterator();
        while (iterator.hasNext()) {
            ExecutionLogEntry entry = iterator.next();
            if ("KO".equals(entry.status())) {
                iterator.remove();
                removedInMemory++;
            }
        }

        fileStore.deleteAllErrors();
        return removedInMemory;
    }
}
