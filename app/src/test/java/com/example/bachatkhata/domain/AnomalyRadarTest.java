package com.example.bachatkhata.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class AnomalyRadarTest {

    private static final Date NOW = new Date(1_770_000_000_000L);

    private static Date daysAgo(int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(NOW);
        cal.add(Calendar.DAY_OF_YEAR, -days);
        return cal.getTime();
    }

    private static List<AnomalyRadar.Sample> recent(double... amounts) {
        List<AnomalyRadar.Sample> samples = new ArrayList<>();
        int age = 1;
        for (double amount : amounts) {
            samples.add(new AnomalyRadar.Sample(amount, daysAgo(age++)));
        }
        return samples;
    }

    @Test
    public void flagsASpendWellAboveTheUsualLevel() {
        AnomalyRadar.Result result = AnomalyRadar.check(1000, recent(200, 180, 220, 210), NOW);

        assertTrue(result.anomalous);
        assertEquals(205.0, result.baseline, 0.001);
    }

    @Test
    public void leavesAnOrdinarySpendAlone() {
        assertFalse(AnomalyRadar.check(260, recent(200, 180, 220, 210), NOW).anomalous);
    }

    @Test
    public void oneHugePastSpendDoesNotSilenceLaterAlerts() {
        // The point of the median. With a mean baseline the 50,000 outlier lifts the
        // average to ~10,000, whose 2.5x threshold (~25,000) swallows every later
        // alert — the check goes quiet exactly when it should be loudest.
        List<AnomalyRadar.Sample> samples = recent(200, 180, 220, 210, 50_000);

        AnomalyRadar.Result result = AnomalyRadar.check(1000, samples, NOW);

        assertEquals("median must barely move for one outlier", 210.0, result.baseline, 0.001);
        assertTrue(result.anomalous);
    }

    @Test
    public void ignoresSamplesOlderThanTheWindow() {
        List<AnomalyRadar.Sample> samples = new ArrayList<>(recent(200, 180, 220));
        samples.add(new AnomalyRadar.Sample(90_000, daysAgo(AnomalyRadar.BASELINE_WINDOW_DAYS + 5)));

        AnomalyRadar.Result result = AnomalyRadar.check(1000, samples, NOW);

        assertEquals("a spend from last year is not 'usual'", 200.0, result.baseline, 0.001);
        assertTrue(result.anomalous);
    }

    @Test
    public void staysQuietBelowTheSampleMinimum() {
        assertFalse(AnomalyRadar.check(1000, recent(200, 180), NOW).anomalous);
        assertFalse(AnomalyRadar.check(1000, Collections.emptyList(), NOW).anomalous);
    }

    @Test
    public void staysQuietWhenEveryUsableSampleIsOutsideTheWindow() {
        List<AnomalyRadar.Sample> stale = Arrays.asList(
                new AnomalyRadar.Sample(200, daysAgo(200)),
                new AnomalyRadar.Sample(210, daysAgo(210)),
                new AnomalyRadar.Sample(220, daysAgo(220)));

        assertFalse(AnomalyRadar.check(1000, stale, NOW).anomalous);
    }

    @Test
    public void guardsAgainstUnusableInput() {
        assertFalse(AnomalyRadar.check(Double.NaN, recent(200, 180, 220), NOW).anomalous);
        assertFalse(AnomalyRadar.check(Double.POSITIVE_INFINITY, recent(200, 180, 220), NOW).anomalous);
        assertFalse(AnomalyRadar.check(-50, recent(200, 180, 220), NOW).anomalous);
        assertFalse(AnomalyRadar.check(1000, null, NOW).anomalous);
        assertFalse(AnomalyRadar.check(1000, recent(200, 180, 220), null).anomalous);
    }

    @Test
    public void skipsSamplesWithNoDateOrNoAmount() {
        List<AnomalyRadar.Sample> samples = new ArrayList<>(recent(200, 180, 220));
        samples.add(new AnomalyRadar.Sample(5000, null));
        samples.add(null);

        assertEquals(200.0, AnomalyRadar.check(1000, samples, NOW).baseline, 0.001);
    }

    @Test
    public void medianAveragesTheMiddlePairForAnEvenCount() {
        assertEquals(15.0, AnomalyRadar.median(Arrays.asList(10.0, 20.0)), 0.001);
        assertEquals(20.0, AnomalyRadar.median(Arrays.asList(30.0, 10.0, 20.0)), 0.001);
        assertEquals(0.0, AnomalyRadar.median(Collections.emptyList()), 0.001);
    }
}
