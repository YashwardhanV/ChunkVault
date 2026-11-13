package com.chunkvault.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class StatisticsCalculator {
    private StatisticsCalculator() { }

    public static BigDecimal ratio(long logicalBytes, long physicalBytes) {
        if (physicalBytes == 0) return BigDecimal.ZERO.setScale(4);
        return BigDecimal.valueOf(logicalBytes)
                .divide(BigDecimal.valueOf(physicalBytes), 4, RoundingMode.HALF_UP);
    }
}
