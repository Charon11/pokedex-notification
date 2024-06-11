package com.sfeir.lux.pokedexnotification.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CachingConfig {

    @Bean
    @Qualifier("tokenCacheManager")
    public CacheManager tokenCacheManager() {
        return new ConcurrentMapCacheManager("apns-token-cache");
    }
}
