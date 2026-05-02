package com.carpool.web.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Per-cache TTL configuration.
 *
 * hubs       — 60 min TTL, rarely changes, evicted on admin hub approval
 * users      — 10 min TTL, role/status changes need to reflect quickly
 * hub-search — 5 min TTL, autocomplete results
 */
@Configuration
public class CacheConfig {

    public static final String CACHE_HUBS        = "hubs";
    public static final String CACHE_USERS        = "users";
    public static final String CACHE_HUB_SEARCH  = "hub-search";
    public static final String CACHE_ADMIN_STATS = "adminStats";
    public static final String CACHE_PROFILE_STATS = "profileStats";

    @Bean
    public CacheManager cacheManager() {
        var hubsCache = new CaffeineCache(CACHE_HUBS,
                Caffeine.newBuilder()
                        .maximumSize(200)
                        .expireAfterWrite(60, TimeUnit.MINUTES)
                        .recordStats()
                        .build());

        var usersCache = new CaffeineCache(CACHE_USERS,
                Caffeine.newBuilder()
                        .maximumSize(1000)
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .recordStats()
                        .build());

        var hubSearchCache = new CaffeineCache(CACHE_HUB_SEARCH,
                Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .recordStats()
                        .build());

        var adminStatsCache = new CaffeineCache(CACHE_ADMIN_STATS,
                Caffeine.newBuilder()
                        .maximumSize(1)
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .recordStats()
                        .build());

        var profileStatsCache = new CaffeineCache(CACHE_PROFILE_STATS,
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .recordStats()
                        .build());

        var manager = new SimpleCacheManager();
        manager.setCaches(List.of(hubsCache, usersCache, hubSearchCache, adminStatsCache, profileStatsCache));
        return manager;
    }
}
