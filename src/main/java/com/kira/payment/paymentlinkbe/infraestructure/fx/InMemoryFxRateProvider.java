package com.kira.payment.paymentlinkbe.infraestructure.fx;

import com.kira.payment.paymentlinkbe.domain.fx.FxQuote;
import com.kira.payment.paymentlinkbe.domain.fx.FxRateProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component("inMemoryFxRateProvider")
public class InMemoryFxRateProvider implements FxRateProvider {

    private final Map<String, BigDecimal> baseRates = new ConcurrentHashMap<>();

    private final BigDecimal maxJitterPercent;

    public InMemoryFxRateProvider(
            @Value("${fx.mock.max-jitter-percent:0.01}") BigDecimal maxJitterPercent
    ) {
        this.maxJitterPercent = maxJitterPercent != null
                ? maxJitterPercent
                : new BigDecimal("0.01");
        baseRates.put("USD:COP", new BigDecimal("4000.00"));
        baseRates.put("EUR:USD", new BigDecimal("1.10"));
    }

    @Override
    public FxQuote getQuote(String baseCurrency, String counterCurrency) {
        Objects.requireNonNull(baseCurrency, "baseCurrency must not be null");
        Objects.requireNonNull(counterCurrency, "counterCurrency must not be null");

        String from = baseCurrency.toUpperCase();
        String to   = counterCurrency.toUpperCase();

        if (from.equals(to)) {
            BigDecimal one = BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP);
            return new FxQuote(from, to, one, BigDecimal.ZERO, one, Instant.now());
        }

        String key = pairKey(from, to);
        BigDecimal baseRate = baseRates.get(key);
        if (baseRate == null) {
            throw new IllegalArgumentException("FX rate not configured for pair: " + key);
        }

        BigDecimal jitter = randomJitter();
        BigDecimal effectiveRate = applyJitter(baseRate, jitter);

        log.debug("FX quote for {}: baseRate={}, jitter={}, effectiveRate={}",
                key, baseRate, jitter, effectiveRate);

        return new FxQuote(from, to, baseRate, jitter, effectiveRate, Instant.now());
    }

    private String pairKey(String base, String counter) {
        return base.toUpperCase() + ":" + counter.toUpperCase();
    }

    private BigDecimal randomJitter() {
        if (maxJitterPercent == null
                || maxJitterPercent.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }

        double max = maxJitterPercent.doubleValue();
        double value = ThreadLocalRandom.current().nextDouble(-max, max);
        return BigDecimal.valueOf(value)
                .setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal applyJitter(BigDecimal baseRate, BigDecimal jitter) {
        BigDecimal factor = BigDecimal.ONE.add(jitter);
        return baseRate
                .multiply(factor)
                .setScale(6, RoundingMode.HALF_UP);
    }

    // --------- Métodos para configurar el mock ---------

    /**
     * Configura/actualiza una tasa base para un par de monedas.
     * Útil para tests o para cambiar comportamiento en runtime.
     */
    public void setBaseRate(String baseCurrency, String counterCurrency, BigDecimal rate) {
        Objects.requireNonNull(rate, "rate must not be null");
        baseRates.put(pairKey(baseCurrency, counterCurrency), rate);
    }

    /**
     * Borra una tasa configurada (por si quieres limpiar en tests).
     */
    public void clearRates() {
        baseRates.clear();
    }
}
