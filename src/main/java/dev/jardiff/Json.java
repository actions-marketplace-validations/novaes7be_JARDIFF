package dev.jardiff;

import java.util.Collection;
import java.util.Map;

/** Escritor de JSON mínimo (Map, Collection, String, Number, Boolean, null). */
final class Json {
    private Json() {}

    static String write(Object o, boolean pretty) {
        StringBuilder sb = new StringBuilder();
        w(sb, o, pretty, 0);
        return sb.toString();
    }

    private static void w(StringBuilder sb, Object o, boolean p, int d) {
        if (o == null) sb.append("null");
        else if (o instanceof String s) str(sb, s);
        else if (o instanceof Number || o instanceof Boolean) sb.append(o);
        else if (o instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                nl(sb, p, d + 1);
                str(sb, String.valueOf(e.getKey()));
                sb.append(p ? ": " : ":");
                w(sb, e.getValue(), p, d + 1);
            }
            if (!m.isEmpty()) nl(sb, p, d);
            sb.append('}');
        } else if (o instanceof Collection<?> c) {
            sb.append('[');
            boolean first = true;
            for (Object x : c) {
                if (!first) sb.append(',');
                first = false;
                nl(sb, p, d + 1);
                w(sb, x, p, d + 1);
            }
            if (!c.isEmpty()) nl(sb, p, d);
            sb.append(']');
        } else str(sb, o.toString());
    }

    private static void nl(StringBuilder sb, boolean p, int d) {
        if (!p) return;
        sb.append('\n');
        sb.append("  ".repeat(d));
    }

    private static void str(StringBuilder sb, String s) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
