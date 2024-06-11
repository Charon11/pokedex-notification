package com.sfeir.lux.pokedexnotification.config;

import java.time.LocalDateTime;

public record TokenCacheHolder(String token, LocalDateTime expirationDate) {
}
