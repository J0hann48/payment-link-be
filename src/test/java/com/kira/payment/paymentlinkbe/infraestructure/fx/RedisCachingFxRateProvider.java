package com.kira.payment.paymentlinkbe.infraestructure.fx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kira.payment.paymentlinkbe.domain.fx.FxQuote;
import com.kira.payment.paymentlinkbe.domain.fx.FxRateProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("aws")
public class RedisCachingFxRateProvider implements FxRateProvider{

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final @Qualifier("inMemoryFxRateProvider") FxRateProvider delegate;

    @Value("${fx.cache.ttl-seconds:60}")
    private long ttlSeconds;

    @Override
    public FxQuote getQuote(String baseCurrency, String counterCurrency) {
        String from = baseCurrency.toUpperCase(Locale.ROOT);
        String to   = counterCurrency.toUpperCase(Locale.ROOT);

        String key = buildKey(from, to);
        try {
            String cachedJson = redisTemplate.opsForValue().get(key);
            if (cachedJson != null) {
                FxQuote cached = fromJson(cachedJson);
                log.debug("FX cache hit for {} → {}", key, cached.effectiveRate());
                return cached;
            }
        } catch (Exception e) {
            log.warn("Failed to read FX cache for key={}: {}", key, e.getMessage());
        }
        FxQuote fresh = delegate.getQuote(from, to);

        try {
            String json = toJson(fresh);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
            log.debug("Stored FX quote in cache for key={} ttl={}s", key, ttlSeconds);
        } catch (Exception e) {
            log.warn("Failed to store FX cache for key={}: {}", key, e.getMessage());
        }

        return fresh;
    }

    private String buildKey(String from, String to) {
        return "fx:%s:%s".formatted(from, to);
    }

    private String toJson(FxQuote quote) throws JsonProcessingException {
        return objectMapper.writeValueAsString(quote);
    }

    private FxQuote fromJson(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, FxQuote.class);
    }
}
