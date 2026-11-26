package com.civicflow.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Lightweight zero-dependency JSON serializer and flat-object parser for the embedded Core Java HTTP server.
 */
public final class JsonUtil {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private JsonUtil() {
    }

    public static String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return quote(s);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Enum<?> e) return quote(e.name());
        if (value instanceof LocalDateTime ldt) return quote(ldt.format(DT_FMT));
        if (value instanceof LocalDate ld) return quote(ld.toString());
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(quote(String.valueOf(entry.getKey()))).append(':').append(toJson(entry.getValue()));
            }
            sb.append('}');
            return sb.toString();
        }
        if (value instanceof Collection<?> col) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : col) {
                if (!first) sb.append(',');
                first = false;
                sb.append(toJson(item));
            }
            sb.append(']');
            return sb.toString();
        }
        return quote(value.toString());
    }

    public static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * Parses a flat JSON object {"key":"value","num":123,"flag":true} into a Map<String, String>.
     */
    public static Map<String, String> parseFlatObject(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return result;
        String s = json.trim();
        if (!s.startsWith("{") || !s.endsWith("}")) return result;
        int i = 1;
        int n = s.length() - 1;
        while (i < n) {
            i = skipWs(s, i, n);
            if (i >= n) break;
            if (s.charAt(i) == ',') { i++; continue; }
            if (s.charAt(i) != '"') break;
            int[] keyEnd = new int[1];
            String key = readString(s, i, keyEnd);
            i = skipWs(s, keyEnd[0], n);
            if (i >= n || s.charAt(i) != ':') break;
            i = skipWs(s, i + 1, n);
            if (i >= n) break;
            String val;
            if (s.charAt(i) == '"') {
                int[] valEnd = new int[1];
                val = readString(s, i, valEnd);
                i = valEnd[0];
            } else {
                int start = i;
                while (i < n && s.charAt(i) != ',' && s.charAt(i) != '}') i++;
                String raw = s.substring(start, i).trim();
                val = "null".equals(raw) ? null : raw;
            }
            result.put(key, val);
        }
        return result;
    }

    private static int skipWs(String s, int i, int n) {
        while (i < n && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    private static String readString(String s, int startQuote, int[] endPos) {
        StringBuilder sb = new StringBuilder();
        int i = startQuote + 1;
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '\\' && i < s.length()) {
                char esc = s.charAt(i++);
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    default -> sb.append(esc);
                }
            } else if (c == '"') {
                endPos[0] = i;
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        endPos[0] = i;
        return sb.toString();
    }
}
