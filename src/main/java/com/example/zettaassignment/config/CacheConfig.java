package com.example.zettaassignment.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final Duration RATE_TTL = Duration.ofSeconds(60);
    private static final int RATE_CACHE_MAX_SIZE = 512;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("rates");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(RATE_TTL)
                .maximumSize(RATE_CACHE_MAX_SIZE));
        return cacheManager;
    }
}
