package com.civicflow.security;

/**
 * Neutralizes CSV spreadsheet formula injection (CWE-1236) and applies RFC 4180 escaping.
 */
public final class CsvSanitizer {

    private CsvSanitizer() {
    }

    /**
     * Neutralizes leading formula trigger characters (=, +, -, @, tab, CR) and quotes cell safely.
     */
    public static String sanitizeCell(String value) {
        if (value == null) {
            return "\"\"";
        }
        String cleaned = value.replace("\r\n", " ").replace("\n", " ").replace("\r", " ");
        String trimmedStart = cleaned.stripLeading();
        if (!trimmedStart.isEmpty()) {
            char first = trimmedStart.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t') {
                cleaned = "'" + cleaned;
            }
        }
        String escaped = cleaned.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    /**
     * Produces a safe filename without path traversal or OS-forbidden characters.
     */
    public static String sanitizeFileName(String rawName, String extension) {
        String base = (rawName == null || rawName.isBlank()) ? "civicflow_report" : rawName.trim();
        base = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        base = base.replaceAll("\\.\\.+", "_");
        if (base.length() > 80) {
            base = base.substring(0, 80);
        }
        String ext = extension.startsWith(".") ? extension : "." + extension;
        if (!base.toLowerCase().endsWith(ext.toLowerCase())) {
            base = base + ext;
        }
        return base;
    }
}
