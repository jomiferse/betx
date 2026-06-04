package com.betx.application;

import java.math.BigDecimal;

/** Absolute and percentage movement for one numeric market value. */
public record NumericChange(
    BigDecimal previous,
    BigDecimal current,
    BigDecimal absoluteDelta,
    BigDecimal percentageDelta
) {
}
