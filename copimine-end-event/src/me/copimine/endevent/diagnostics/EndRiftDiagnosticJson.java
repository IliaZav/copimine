package me.copimine.endevent.diagnostics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Small deterministic JSON writer for the diagnostic JSONL stream. */
public final class EndRiftDiagnosticJson {
    private EndRiftDiagnosticJson() {
    }

    public static String toJson(EndRiftDiagnosticEvent event) {
        if (event == null) {
            return "null";
        }
        StringBuilder json = new StringBuilder(512).append('{');
        field(json, "sequence", event.sequence(), false);
        field(json, "timestamp", event.timestamp(), true);
        field(json, "serverTick", event.serverTick(), false);
        field(json, "generation", event.generation(), false);
        field(json, "eventId", event.eventId(), true);
        field(json, "category", event.category(), true);
        field(json, "action", event.action(), true);
        field(json, "severity", event.severity(), true);
        field(json, "wave", event.wave(), false);
        field(json, "phase", event.phase(), true);
        field(json, "playerId", event.playerId(), true);
        field(json, "entityId", event.entityId(), true);
        field(json, "relatedEntityId", event.relatedEntityId(), true);
        field(json, "correlationId", event.correlationId(), true);
        field(json, "reason", event.reason(), true);
        field(json, "fields", event.fields(), false);
        return json.append('}').toString();
    }

    public static String toJson(Map<String, Object> values) {
        StringBuilder json = new StringBuilder(256).append('{');
        boolean[] first = {true};
        if (values != null) {
            values.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .forEach(entry -> {
                        if (!first[0]) {
                            json.append(',');
                        }
                        first[0] = false;
                        appendString(json, String.valueOf(entry.getKey()));
                        json.append(':');
                        appendValue(json, entry.getValue());
                    });
        }
        return json.append('}').toString();
    }

    private static void field(StringBuilder json, String name, Object value, boolean stringLike) {
        if (json.length() > 1) {
            json.append(',');
        }
        appendString(json, name);
        json.append(':');
        if (stringLike && value != null) {
            appendString(json, String.valueOf(value));
        } else {
            appendValue(json, value);
        }
    }

    private static void appendValue(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof String || value instanceof Character
                || value instanceof Enum<?> || value instanceof Instant
                || value instanceof java.util.UUID) {
            appendString(json, String.valueOf(value));
        } else if (value instanceof Boolean || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            json.append(value);
        } else if (value instanceof Float floatValue) {
            json.append(Float.isFinite(floatValue) ? floatValue : "null");
        } else if (value instanceof Double doubleValue) {
            json.append(Double.isFinite(doubleValue) ? doubleValue : "null");
        } else if (value instanceof Number) {
            json.append(value);
        } else if (value instanceof Map<?, ?> map) {
            json.append('{');
            boolean first = true;
            List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
            entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey())));
            for (Map.Entry<?, ?> entry : entries) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                appendString(json, String.valueOf(entry.getKey()));
                json.append(':');
                appendValue(json, entry.getValue());
            }
            json.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            json.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                appendValue(json, item);
            }
            json.append(']');
        } else if (value.getClass().isArray()) {
            json.append('[');
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                if (index > 0) {
                    json.append(',');
                }
                appendValue(json, java.lang.reflect.Array.get(value, index));
            }
            json.append(']');
        } else {
            appendString(json, String.valueOf(value));
        }
    }

    private static void appendString(StringBuilder json, String value) {
        json.append('"');
        if (value != null) {
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                switch (character) {
                    case '"' -> json.append("\\\"");
                    case '\\' -> json.append("\\\\");
                    case '\b' -> json.append("\\b");
                    case '\f' -> json.append("\\f");
                    case '\n' -> json.append("\\n");
                    case '\r' -> json.append("\\r");
                    case '\t' -> json.append("\\t");
                    default -> {
                        if (character < 0x20) {
                            json.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) character));
                        } else {
                            json.append(character);
                        }
                    }
                }
            }
        }
        json.append('"');
    }
}
