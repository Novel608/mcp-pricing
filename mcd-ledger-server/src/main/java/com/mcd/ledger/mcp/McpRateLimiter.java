package com.mcd.ledger.mcp;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.stereotype.Component;

/**
 * 令牌桶限流（设计 NF-6 / §3.5）：≤ 600 次/分钟。
 * Guava RateLimiter 封装，McpClient 每次调用前 acquire。
 */
@Component
public class McpRateLimiter {

    private final RateLimiter limiter;

    public McpRateLimiter() {
        // 每秒许可数 = 次/分钟 / 60
        this.limiter = RateLimiter.create(600.0 / 60.0);
    }

    public McpRateLimiter(double perMinute) {
        this.limiter = RateLimiter.create(Math.max(perMinute, 1.0) / 60.0);
    }

    /** 阻塞获取一个许可 */
    public void acquire() {
        limiter.acquire();
    }

    /** 尝试获取，超时返回 false */
    public boolean tryAcquire(long timeoutMs) {
        return limiter.tryAcquire(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
