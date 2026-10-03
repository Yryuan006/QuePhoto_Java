package com.quephoto.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayDeque;

@Component
public class LoginRateLimiter {
    private final ArrayDeque<Instant> attempts = new ArrayDeque<>();
    public synchronized void checkAndRecord() {
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(60);
        //  清理已经达到60s的记录
        while (!attempts.isEmpty()
        && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.removeFirst();
        }
        // 窗口中已经有5次尝试，拒绝当前请求
        if (attempts.size() >= 5) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "登录过于频繁，请60s后重试"
            );
        }
        // 还有额度，记录本次尝试
        attempts.addLast(now);

    }
}
