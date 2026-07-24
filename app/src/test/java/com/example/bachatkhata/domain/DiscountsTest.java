package com.example.bachatkhata.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DiscountsTest {

    private static final double EPS = 0.0001;

    @Test
    public void percentDiscountReducesAmount() {
        Discounts.Result r = Discounts.apply(1000, 20, Discounts.Mode.PERCENT);
        assertEquals(1000, r.gross, EPS);
        assertEquals(200, r.discount, EPS);
        assertEquals(800, r.net, EPS);
        assertTrue(r.hasDiscount());
    }

    @Test
    public void flatDiscountReducesAmount() {
        Discounts.Result r = Discounts.apply(1000, 150, Discounts.Mode.FLAT);
        assertEquals(150, r.discount, EPS);
        assertEquals(850, r.net, EPS);
    }

    @Test
    public void percentAbove100IsCappedAtFullPrice() {
        Discounts.Result r = Discounts.apply(500, 150, Discounts.Mode.PERCENT);
        assertEquals(500, r.discount, EPS);
        assertEquals(0, r.net, EPS);
    }

    @Test
    public void flatDiscountCannotExceedGross() {
        // A ₹500 coupon on a ₹200 purchase must not produce -300, which would
        // read as income in every rollup.
        Discounts.Result r = Discounts.apply(200, 500, Discounts.Mode.FLAT);
        assertEquals(200, r.discount, EPS);
        assertEquals(0, r.net, EPS);
    }

    @Test
    public void zeroOrNegativeDiscountLeavesAmountUntouched() {
        Discounts.Result r = Discounts.apply(750, 0, Discounts.Mode.FLAT);
        assertEquals(750, r.net, EPS);
        assertEquals(0, r.discount, EPS);
        assertFalse(r.hasDiscount());

        Discounts.Result negative = Discounts.apply(750, -50, Discounts.Mode.PERCENT);
        assertEquals(750, negative.net, EPS);
        assertFalse(negative.hasDiscount());
    }

    @Test
    public void nonPositiveGrossYieldsZeroes() {
        Discounts.Result r = Discounts.apply(0, 20, Discounts.Mode.PERCENT);
        assertEquals(0, r.gross, EPS);
        assertEquals(0, r.net, EPS);
        assertFalse(r.hasDiscount());
    }

    @Test
    public void handlesNonFiniteInput() {
        assertEquals(0, Discounts.apply(Double.NaN, 10, Discounts.Mode.FLAT).net, EPS);
        assertEquals(100, Discounts.apply(100, Double.NaN, Discounts.Mode.FLAT).net, EPS);
        assertEquals(100, Discounts.apply(100, Double.POSITIVE_INFINITY, Discounts.Mode.PERCENT).gross, EPS);
    }
}
