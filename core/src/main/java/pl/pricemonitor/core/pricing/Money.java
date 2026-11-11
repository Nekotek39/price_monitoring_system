package pl.pricemonitor.core.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Rounding helpers for prices (PLN, 2 decimal places). */
public final class Money {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }

    public static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Returns {@code value * (1 + percent / 100)} rounded to grosz. */
    public static BigDecimal plusPercent(BigDecimal value, BigDecimal percent) {
        return round(value.multiply(BigDecimal.ONE.add(percent.divide(HUNDRED, 10, RoundingMode.HALF_UP))));
    }

    /** Returns {@code value * (1 - percent / 100)} rounded to grosz. */
    public static BigDecimal minusPercent(BigDecimal value, BigDecimal percent) {
        return plusPercent(value, percent.negate());
    }

    /** Formats a price for messages, e.g. {@code 1 350,00 zł}. */
    public static String format(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        String plain = round(value).toPlainString();
        String[] parts = plain.split("\\.");
        StringBuilder integer = new StringBuilder(parts[0]);
        int sign = integer.charAt(0) == '-' ? 1 : 0;
        for (int i = integer.length() - 3; i > sign; i -= 3) {
            integer.insert(i, ' ');
        }
        return integer + "," + parts[1] + " zł";
    }
}
