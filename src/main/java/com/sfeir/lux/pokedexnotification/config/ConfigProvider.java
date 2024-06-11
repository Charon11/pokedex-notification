package com.sfeir.lux.pokedexnotification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class ConfigProvider {

    @Bean( name = "azureFcmTokens")
    @ConfigurationProperties(prefix = "azure.fcm.tokens")
    public List<String> azureFcmTokens() {
        return new ArrayList<>();
    }

    @Bean( name = "fcmTokens")
    @ConfigurationProperties(prefix = "fcm.tokens")
    public List<String> fcmTokens() {
        return new ArrayList<>();
    }
}
