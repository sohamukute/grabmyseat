package com.grabmyseat.limit;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RateLimit {

    private static final String WEIGHTED = """
            SELECT w.hits + coalesce((SELECT p.hits FROM rate_hit p WHERE p.key = :key
                                      AND p.window_start = w.window_start - make_interval(secs => :window)), 0)
                          * (1 - extract(epoch FROM now() - w.window_start) / :window)
            FROM now_window w
            """;

    private final JdbcClient jdbc;

    public RateLimit(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public double hit(String key, int windowSeconds) {
        return jdbc.sql("""
                        WITH now_window AS (
                            INSERT INTO rate_hit (key, window_start, hits)
                            VALUES (:key, to_timestamp(floor(extract(epoch FROM now()) / :window) * :window), 1)
                            ON CONFLICT (key, window_start) DO UPDATE SET hits = rate_hit.hits + 1
                            RETURNING window_start, hits)
                        """ + WEIGHTED)
                .param("key", key)
                .param("window", windowSeconds)
                .query(Double.class).single();
    }

    public double count(String key, int windowSeconds) {
        return jdbc.sql("""
                        WITH now_window AS (
                            SELECT to_timestamp(floor(extract(epoch FROM now()) / :window) * :window) AS window_start,
                                   coalesce((SELECT hits FROM rate_hit WHERE key = :key
                                             AND window_start = to_timestamp(floor(extract(epoch FROM now()) / :window) * :window)), 0) AS hits)
                        """ + WEIGHTED)
                .param("key", key)
                .param("window", windowSeconds)
                .query(Double.class).single();
    }

    @Scheduled(fixedDelay = 600_000)
    public void sweep() {
        jdbc.sql("DELETE FROM rate_hit WHERE window_start < now() - interval '1 day'").update();
    }
}
