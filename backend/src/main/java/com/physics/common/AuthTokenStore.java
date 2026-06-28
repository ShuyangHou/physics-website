package com.physics.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class AuthTokenStore {
    private static final Logger log = LoggerFactory.getLogger(AuthTokenStore.class);
    private static final long TOKEN_TTL_MILLIS = TimeUnit.HOURS.toMillis(24);

    private final StringRedisTemplate redisTemplate;
    private final Map<Long, LocalToken> localTokens = new ConcurrentHashMap<>();
    private volatile boolean redisAvailable = true;

    public AuthTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(Long userId, String token) {
        if (userId == null || token == null) {
            return;
        }
        localTokens.put(userId, new LocalToken(token, System.currentTimeMillis() + TOKEN_TTL_MILLIS));

        if (!redisAvailable) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(redisKey(userId), token, 24, TimeUnit.HOURS);
        } catch (Exception e) {
            redisAvailable = false;
            log.warn("Redis不可用，登录token临时改用本机内存保存: {}", e.getMessage());
        }
    }

    public boolean validate(Long userId, String token) {
        if (userId == null || token == null) {
            return false;
        }

        if (redisAvailable) {
            try {
                String storedToken = redisTemplate.opsForValue().get(redisKey(userId));
                if (token.equals(storedToken)) {
                    return true;
                }
            } catch (Exception e) {
                redisAvailable = false;
                log.warn("Redis不可用，token校验临时改用本机内存: {}", e.getMessage());
            }
        }

        LocalToken localToken = localTokens.get(userId);
        if (localToken == null) {
            return false;
        }
        if (localToken.expiresAt < System.currentTimeMillis()) {
            localTokens.remove(userId);
            return false;
        }
        return token.equals(localToken.token);
    }

    public void delete(Long userId) {
        if (userId == null) {
            return;
        }
        localTokens.remove(userId);
        if (!redisAvailable) {
            return;
        }
        try {
            redisTemplate.delete(redisKey(userId));
        } catch (Exception e) {
            redisAvailable = false;
            log.warn("Redis不可用，登出时仅清理本机内存token: {}", e.getMessage());
        }
    }

    private String redisKey(Long userId) {
        return "user_token:" + userId;
    }

    private static class LocalToken {
        private final String token;
        private final long expiresAt;

        private LocalToken(String token, long expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }
}
