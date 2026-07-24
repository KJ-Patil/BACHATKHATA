package com.example.bachatkhata.domain;

/**
 * Discount maths for the Add-Transaction screen.
 *
 * <p>The user types the pre-discount price plus either a percentage or a flat
 * amount. The discount is clamped to {@code 0…gross} — a 150% off coupon or a
 * ₹500 discount on a ₹200 purchase must not produce negative spending, which
 * would read as income everywhere downstream.
 *
 * <p>Pure Java so the clamping rules are unit-testable.
 */
public final class Discounts {

    private Discounts() {
    }

    /** Discount input mode: a percentage of the gross, or a flat currency amount. */
    public enum Mode { PERCENT, FLAT }

    /** Resolved discount: what to store on the transaction. */
    public static final class Result {
        /** Pre-discount price as typed. */
        public final double gross;
        /** Amount knocked off, always within 0…gross. */
        public final double discount;
        /** What actually hits the ledger: gross − discount, never negative. */
        public final double net;

        Result(double gross, double discount, double net) {
            this.gross = gross;
            this.discount = discount;
            this.net = net;
        }

        /** True when there is a saving worth storing and showing. */
        public boolean hasDiscount() {
            return discount > 0;
        }
    }

    /**
     * Applies a discount to a gross amount.
     *
     * @param gross     the pre-discount price; values &lt;= 0 yield an all-zero result
     * @param rawValue  the percentage (0–100) or flat amount the user typed
     * @param mode      how to interpret {@code rawValue}
     */
    public static Result apply(double gross, double rawValue, Mode mode) {
        if (gross <= 0 || Double.isNaN(gross) || Double.isInfinite(gross)) {
            return new Result(0, 0, 0);
        }
        if (rawValue <= 0 || Double.isNaN(rawValue) || Double.isInfinite(rawValue)) {
            return new Result(gross, 0, gross);
        }

        double discount = (mode == Mode.PERCENT)
                // Cap the percentage itself first, so 150% behaves as 100% rather
                // than relying on the amount clamp below to rescue it.
                ? gross * (Math.min(rawValue, 100d) / 100d)
                : rawValue;

        discount = Math.min(discount, gross);
        return new Result(gross, discount, gross - discount);
    }
}
