package com.fly.hsbchomework.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDistributedLock {
    
    private final StringRedisTemplate redisTemplate;
    private static final long DEFAULT_TIMEOUT = 30;
    private static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;
    
    /**
     * Try to acquire lock
     * @param lockKey Lock key
     * @param requestId Request identifier
     * @return true if lock acquired, false otherwise
     */
    public boolean tryLock(String lockKey, String requestId) {
        return tryLock(lockKey, requestId, DEFAULT_TIMEOUT, DEFAULT_TIME_UNIT);
    }
    
    /**
     * Try to acquire lock with custom timeout
     * @param lockKey Lock key
     * @param requestId Request identifier
     * @param timeout Timeout duration
     * @param unit Time unit
     * @return true if lock acquired, false otherwise
     */
    public boolean tryLock(String lockKey, String requestId, long timeout, TimeUnit unit) {
        try {
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, requestId, timeout, unit);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Failed to acquire lock: {}", lockKey, e);
            return false;
        }
    }
    
    /**
     * Release lock
     * @param lockKey Lock key
     * @param requestId Request identifier
     * @return true if lock released, false otherwise
     */
    public boolean releaseLock(String lockKey, String requestId) {
        try {
            String value = redisTemplate.opsForValue().get(lockKey);
            if (requestId.equals(value)) {
                return Boolean.TRUE.equals(redisTemplate.delete(lockKey));
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to release lock: {}", lockKey, e);
            return false;
        }
    }
} 