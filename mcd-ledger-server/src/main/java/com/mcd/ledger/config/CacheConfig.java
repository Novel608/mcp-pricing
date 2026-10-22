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
 * Caffeine 缓存（设计 §7.4）。菜单/详情/券/试算 TTL 递减，减少 MCP 配额消耗。
 * meals 10min、mealDetail 30min、coupons 5min、pricing 5min、orders 5min。
 */
@Configuration
public class CacheConfig {

    public static final String ORDERS_CACHE = "mcp:orders";
    public static final String MEALS_CACHE = "meals";
    public static final String MEAL_DETAIL_CACHE = "mealDetail";
    public static final String COUPONS_CACHE = "coupons";
    public static final String PRICING_CACHE = "pricing";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCache orders = new CaffeineCache(ORDERS_CACHE,
                Caffeine.newBuilder().maximumSize(200).expireAfterWrite(Duration.ofMinutes(5)).build());
        CaffeineCache meals = new CaffeineCache(MEALS_CACHE,
                Caffeine.newBuilder().maximumSize(100).expireAfterWrite(Duration.ofMinutes(10)).build());
        CaffeineCache mealDetail = new CaffeineCache(MEAL_DETAIL_CACHE,
                Caffeine.newBuilder().maximumSize(300).expireAfterWrite(Duration.ofMinutes(30)).build());
        CaffeineCache coupons = new CaffeineCache(COUPONS_CACHE,
                Caffeine.newBuilder().maximumSize(50).expireAfterWrite(Duration.ofMinutes(5)).build());
        CaffeineCache pricing = new CaffeineCache(PRICING_CACHE,
                Caffeine.newBuilder().maximumSize(200).expireAfterWrite(Duration.ofMinutes(5)).build());

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(orders, meals, mealDetail, coupons, pricing));
        return manager;
    }
}
