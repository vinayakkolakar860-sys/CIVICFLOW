package com.civicflow.reporting;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Structured tabular report payload used by UI previews, CSV exports, and PDF exports.
 */
public class ReportData {
    private final String title;
    private final String subtitle;
    private final String watermark;
    private final String generatedBy;
    private final LocalDateTime generatedAt;
    private final List<String> headers;
    private final List<List<String>> rows;

    public ReportData(String title, String subtitle, String watermark, String generatedBy,
                      LocalDateTime generatedAt, List<String> headers, List<List<String>> rows) {
        this.title = title;
        this.subtitle = subtitle;
        this.watermark = watermark;
        this.generatedBy = generatedBy;
        this.generatedAt = generatedAt;
        this.headers = headers;
        this.rows = rows;
    }

    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getWatermark() { return watermark; }
    public String getGeneratedBy() { return generatedBy; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public List<String> getHeaders() { return headers; }
    public List<List<String>> getRows() { return rows; }
}
