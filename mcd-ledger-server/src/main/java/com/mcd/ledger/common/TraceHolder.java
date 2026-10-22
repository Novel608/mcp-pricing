package com.mcd.ledger.common;

import java.util.UUID;

/** 请求级 traceId（简化实现：8 位随机 hex）。 */
public final class TraceHolder {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceHolder() {}

    public static void set(String id) { CURRENT.set(id); }

    public static String id() {
        String id = CURRENT.get();
        if (id == null) {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            CURRENT.set(id);
        }
        return id;
    }

    public static void clear() { CURRENT.remove(); }
}
