package com.grabmyseat.waitingroom;

/**
 * Single source of truth for every Redis key prefix the waiting-room module
 * reads or writes. Keeping these as public constants (instead of duplicating
 * the raw strings in QueueService / AdmissionService / cross-module callers)
 * means a rename here propagates everywhere and a typo can only happen once.
 *
 * Other modules (notably the load-test seed controller in inventory-booking)
 * reference these constants directly so the production and load-test code
 * stay in lock-step on the key shape.
 */
public final class RedisKeys {

    /** Top-level namespace for every waiting-room key. */
    public static final String NAMESPACE = "waiting-room:";

    /** Queue position ZSET (member = queueToken, score = enqueue timestamp). */
    public static final String QUEUE_PREFIX = NAMESPACE;

    /** Hash mapping currently-active queueToken -> eventId. */
    public static final String ACTIVE_TOKEN_PREFIX = NAMESPACE + "active-token:";

    /** Hash holding per-token metadata: {userId, eventId, ...}. */
    public static final String TOKEN_META_PREFIX = NAMESPACE + "token:";

    /** String: queueToken -> issued permit token. */
    public static final String PERMIT_PREFIX = NAMESPACE + "permit:";

    /** Optional reverse index: permit token -> queueToken (for lookup-by-permit). */
    public static final String PERMIT_TOKEN_PREFIX = NAMESPACE + "permit-token:";

    /** Set of eventIds that currently have an active waiting room. */
    public static final String ACTIVE_EVENTS_KEY = NAMESPACE + "active-events";

    /** Token-bucket rate-limit keys (one per queueToken). */
    public static final String RATE_PREFIX = NAMESPACE + "rate:";

    private RedisKeys() {
        // utility class
    }
}
