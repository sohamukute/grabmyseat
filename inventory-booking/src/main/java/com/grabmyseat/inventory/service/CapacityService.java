package com.grabmyseat.inventory.service;

import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Service
public class CapacityService {

    private static final String ZONE_CAPACITY_KEY = "zone:capacity:";
    private static final String RESERVATION_HOLD_KEY = "reservation:hold:";

    private final StringRedisTemplate redis;
    private final DataSource dataSource;

    public CapacityService(StringRedisTemplate redis, DataSource dataSource) {
        this.redis = redis;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void syncFromDatabase() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, capacity FROM zones")) {
            while (rs.next()) {
                long zoneId = rs.getLong("id");
                int capacity = rs.getInt("capacity");
                String key = capacityKey(zoneId);
                redis.opsForValue().setIfAbsent(key, String.valueOf(capacity));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to sync zone capacities from postgres", ex);
        }
    }

    public long available(long zoneId) {
        String value = redis.opsForValue().get(capacityKey(zoneId));
        return value == null ? 0L : Long.parseLong(value);
    }

    public boolean reserve(long zoneId, int count) {
        if (count <= 0) return false;
        String lua = """
                local key = KEYS[1]
                local current = redis.call('GET', key)
                if current == false then
                    return -1
                end
                if tonumber(current) >= tonumber(ARGV[1]) then
                    return redis.call('DECRBY', key, ARGV[1])
                end
                return -1
                """;
        var script = new DefaultRedisScript<Long>(lua, Long.class);
        Long result = redis.execute(script, List.of(capacityKey(zoneId)), String.valueOf(count));
        return result != null && result >= 0;
    }

    public void release(long zoneId, int count) {
        if (count <= 0) return;
        redis.opsForValue().increment(capacityKey(zoneId), count);
    }

    public void initialize(long zoneId, int capacity) {
        redis.opsForValue().set(capacityKey(zoneId), String.valueOf(capacity));
    }

    public void setHold(String token, long eventId, Duration ttl) {
        redis.opsForValue().set(holdKey(token), String.valueOf(eventId), ttl);
    }

    public boolean holdExists(String token) {
        return Boolean.TRUE.equals(redis.hasKey(holdKey(token)));
    }

    public void removeHold(String token) {
        redis.delete(holdKey(token));
    }

    private String capacityKey(long zoneId) {
        return ZONE_CAPACITY_KEY + zoneId;
    }

    private String holdKey(String token) {
        return RESERVATION_HOLD_KEY + token;
    }
}
