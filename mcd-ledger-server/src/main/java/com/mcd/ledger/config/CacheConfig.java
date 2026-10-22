package com.mcd.ledger.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caffeine 缓存（设计 §1.2 / §7.4）。事后主线主要用「订单快照入库后不再调 MCP」，
 * 此处为 MCP 代理查询提供进程内 TTL 缓存，减少配额消耗。
 */
@Configuration
public class CacheConfig {

    public static final String ORDERS_CACHE = "mcp:orders";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCache orders = new CaffeineCache(
                ORDERS_CACHE,
                Caffeine.newBuilder().maximumSize(200).expireAfterWrite(Duration.ofMinutes(5)).build());

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(orders));
        return manager;
    }
}
