package com.sbnz.frontend;

import java.time.LocalDateTime;

public class RunHistoryEntry {
    private final LocalDateTime createdAt;
    private final String report;

    public RunHistoryEntry(LocalDateTime createdAt, String report) {
        this.createdAt = createdAt;
        this.report = report;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getReport() {
        return report;
    }
}
