package com.spendlocker.util;

import java.text.NumberFormat;
import java.util.Locale;

/** Indian-grouped currency (₹12,34,567) everywhere in the app, per the reference design's
 *  explicit formatting spec — not the JVM's default locale, which mis-groups Indian rupees
 *  in Western thousands (₹1,234,567 instead of ₹12,34,567). */
public final class MoneyFormat {

    private static final Locale INDIA = new Locale("en", "IN");
    private static final ThreadLocal<NumberFormat> CURRENCY =
            ThreadLocal.withInitial(() -> {
                NumberFormat format = NumberFormat.getCurrencyInstance(INDIA);
                format.setMaximumFractionDigits(2);
                return format;
            });

    private MoneyFormat() {
    }

    public static String currency(double amount) {
        return CURRENCY.get().format(amount);
    }
}
