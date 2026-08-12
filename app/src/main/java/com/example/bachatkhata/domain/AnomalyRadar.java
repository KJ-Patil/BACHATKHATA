package com.example.bachatkhata.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Decides whether a spend is unusual for its category.
 *
 * <p>The baseline is the <b>median</b> of same-category expenses over a rolling
 * 180-day window, and a spend is flagged above {@link #THRESHOLD_MULTIPLIER}× it.
 *
 * <p><b>Median, not mean.</b> A single large past spend permanently lifts an
 * average, and every later alert is then suppressed — the failure direction that
 * matters, because this is a warning that would go quiet exactly when it
 * shouldn't. A median barely moves for one outlier, which is the point: the check
 * exists to detect outliers, so its baseline has to be robust to them.
 *
 * <p><b>180 days, not all time.</b> "Usual" means recent habit, not something from
 * two years ago.
 *
 * <p>The sample list must <b>not</b> already contain the spend being checked, or it
 * inflates its own baseline — call this before the transaction is written.
 */
public final class AnomalyRadar {

    /** "Usual" is recent habit — samples older than this are ignored. */
    public static final int BASELINE_WINDOW_DAYS = 180;

    /** Below this many surviving samples there is no habit to compare against. */
    public static final int MIN_SAMPLES = 3;

    public static final double THRESHOLD_MULTIPLIER = 2.5;

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private AnomalyRadar() {
    }

    /** One past expense in the category being checked. */
    public static final class Sample {
        public final double amount;
        public final Date date;

        public Sample(double amount, Date date) {
            this.amount = amount;
            this.date = date;
        }
    }

    /** The verdict, with the baseline it was measured against. */
    public static final class Result {
        public final boolean anomalous;
        public final double baseline;
        public final double threshold;

        Result(boolean anomalous, double baseline, double threshold) {
            this.anomalous = anomalous;
            this.baseline = baseline;
            this.threshold = threshold;
        }
    }

    private static final Result NOT_ANOMALOUS = new Result(false, 0.0, 0.0);

    /**
     * @param amount  the spend being checked
     * @param samples past expenses in the same category, excluding {@code amount}
     * @param now     evaluation time, injectable so tests don't depend on the clock
     */
    public static Result check(double amount, List<Sample> samples, Date now) {
        if (!isUsableAmount(amount) || samples == null || now == null) {
            return NOT_ANOMALOUS;
        }

        long cutoff = now.getTime() - BASELINE_WINDOW_DAYS * DAY_MS;

        List<Double> recent = new ArrayList<>();
        for (Sample sample : samples) {
            if (sample == null || sample.date == null) continue;
            if (sample.date.getTime() < cutoff) continue;
            if (!isUsableAmount(sample.amount)) continue;
            recent.add(sample.amount);
        }

        if (recent.size() < MIN_SAMPLES) {
            return NOT_ANOMALOUS;
        }

        double baseline = median(recent);
        if (baseline <= 0) {
            return NOT_ANOMALOUS;
        }

        double threshold = baseline * THRESHOLD_MULTIPLIER;
        return new Result(amount > threshold, baseline, threshold);
    }

    /** Middle value, or the mean of the middle pair for an even count. */
    static double median(List<Double> values) {
        if (values == null || values.isEmpty()) return 0.0;

        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        int size = sorted.size();
        int mid = size / 2;
        if (size % 2 == 1) {
            return sorted.get(mid);
        }
        return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }

    private static boolean isUsableAmount(double amount) {
        return !Double.isNaN(amount) && !Double.isInfinite(amount) && amount > 0;
    }
}
