package com.example.ai.build;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Objects still being placed (their parts appear a few at a time). While one is, it cannot be
 * mined; when it is removed mid-way, the parts still waiting are not placed.
 */
public final class ObjectBuilds {

    /** Tag -> when placing started (ms); forgotten after {@link #MAX_MS} in case its queue was cleared. */
    private static final java.util.Map<String, Long> BUILDING = new ConcurrentHashMap<>();
    static final long MAX_MS = 120_000;
    private static final Set<String> CANCELLED = ConcurrentHashMap.newKeySet();

    private ObjectBuilds() {
    }

    /** Placing the object with this tag starts. */
    public static void start(String tag) {
        if (tag != null) {
            CANCELLED.remove(tag);
            BUILDING.put(tag, System.currentTimeMillis());
        }
    }

    /** Whether the object with this tag is still being placed. */
    public static boolean building(String tag) {
        if (tag == null) {
            return false;
        }
        Long since = BUILDING.get(tag);
        if (since != null && System.currentTimeMillis() - since > MAX_MS) {
            finish(tag); // its placing was stopped some other way
            return false;
        }
        return since != null;
    }

    /** It was removed while being placed: skip its remaining parts. */
    public static void cancel(String tag) {
        if (building(tag)) {
            CANCELLED.add(tag);
        }
    }

    public static boolean cancelled(String tag) {
        return tag != null && CANCELLED.contains(tag);
    }

    /** Its last part is placed (or skipped). */
    public static void finish(String tag) {
        if (tag != null) {
            BUILDING.remove(tag);
            CANCELLED.remove(tag);
        }
    }

    /** Tests and server stop. */
    public static void reset() {
        BUILDING.clear();
        CANCELLED.clear();
    }
}
