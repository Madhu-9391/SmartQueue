package com.smartqueue.config;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.*;
import java.util.concurrent.TimeUnit;

@Configuration @EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager("historicalAnalytics","doctorStats","queueStatus");
        mgr.setCaffeine(Caffeine.newBuilder().maximumSize(200).expireAfterWrite(5,TimeUnit.MINUTES));
        return mgr;
    }
}
