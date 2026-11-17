package com.chunkvault.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StatisticsCalculatorTest {
    @Test
    void calculatesLogicalToPhysicalRatio() {
        assertThat(StatisticsCalculator.ratio(12_000, 4_000)).isEqualByComparingTo(new BigDecimal("3.0000"));
        assertThat(StatisticsCalculator.ratio(0, 0)).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
