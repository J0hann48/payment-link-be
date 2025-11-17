package com.kira.payment.paymentlinkbe.domain.fx;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
@Primary
public class RandomFxRateProvider implements FxRateProvider {

    @Value("${fx.base-rates:USD/MXN=17.20}")
    private String baseRatesConfig;

    @Value("${fx.jitter-bps:50}")
    private int jitterBps;

    private final Random random = new Random();
    private Map<String, BigDecimal> baseRates;

    @PostConstruct
    void init() {
        Map<String, BigDecimal> map = new HashMap<>();
        String[] pairs = baseRatesConfig.split(",");
        for (String pair : pairs) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) continue;

            String[] parts = trimmed.split("=");
            if (parts.length != 2) {
                log.warn("Invalid fx.base-rates entry: {}", trimmed);
                continue;
            }
            String key = parts[0].trim().toUpperCase(); // ej "USD/MXN"
            BigDecimal rate = new BigDecimal(parts[1].trim());
            map.put(key, rate);
        }
        this.baseRates = Map.copyOf(map);
        log.info("Initialized RandomFxRateProvider with baseRates={} jitterBps={}", baseRates, jitterBps);
    }

    @Override
    public FxQuote getQuote(String baseCurrency, String counterCurrency) {
        String pairKey = (baseCurrency + "/" + counterCurrency).toUpperCase();

        BigDecimal baseRate = baseRates.get(pairKey);
        if (baseRate == null) {
            throw new IllegalArgumentException(
                    "No base FX rate configured for pair " + pairKey +
                            ". Configure fx.base-rates in application.yml"
            );
        }

        int bpsDelta = jitterBps > 0 ? random.nextInt(jitterBps + 1) : 0; // 0..jitterBps
        int sign = random.nextBoolean() ? 1 : -1;
        int signedBps = sign * bpsDelta;
        BigDecimal jitterFactor = BigDecimal.valueOf(signedBps)
                .movePointLeft(4); // bps / 10_000

        BigDecimal jitterApplied = jitterFactor
                .multiply(baseRate)
                .setScale(6, RoundingMode.HALF_UP);

        BigDecimal effectiveRate = baseRate
                .add(jitterApplied)
                .setScale(6, RoundingMode.HALF_UP);

        FxQuote quote = new FxQuote(
                baseCurrency.toUpperCase(),
                counterCurrency.toUpperCase(),
                baseRate.setScale(6, RoundingMode.HALF_UP),
                jitterApplied,
                effectiveRate,
                Instant.now()
        );

        log.info(
                "FX mock quote generated: pair={} baseRate={} jitterBps={} appliedBps={} jitterApplied={} effectiveRate={}",
                pairKey, baseRate, jitterBps, signedBps, jitterApplied, effectiveRate
        );

        return quote;
    }
}
