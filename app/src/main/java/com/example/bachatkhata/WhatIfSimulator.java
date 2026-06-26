package com.example.bachatkhata;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure future-value math for SIP-style investing. Mirrors the web app's
 * {@code simulateWhatIf} — no app data, no Firestore, just compounding.
 *
 * Model: an annuity-due. Each month the contribution is deposited at the
 * <em>start</em> of the month, then a full month of growth at {@code rate/12}
 * is applied to the running balance (which includes the lump sum).
 */
public class WhatIfSimulator {

    /** Result of a simulation. {@code yearlyValues} has {@code years + 1} points (index 0 = start). */
    public static class Result {
        public final double futureValue;
        public final double totalInvested;
        public final double totalReturns;
        public final List<Double> yearlyValues;

        Result(double futureValue, double totalInvested, double totalReturns, List<Double> yearlyValues) {
            this.futureValue = futureValue;
            this.totalInvested = totalInvested;
            this.totalReturns = totalReturns;
            this.yearlyValues = yearlyValues;
        }
    }

    public static Result simulate(double monthlyContribution, double initialLumpSum,
                                  int years, double annualRatePercent) {
        if (monthlyContribution < 0) monthlyContribution = 0;
        if (initialLumpSum < 0) initialLumpSum = 0;
        if (years < 0) years = 0;

        double monthlyRate = annualRatePercent / 100.0 / 12.0;
        int totalMonths = years * 12;

        double balance = initialLumpSum;
        List<Double> yearlyValues = new ArrayList<>();
        yearlyValues.add(balance); // year 0 = starting lump sum

        for (int month = 1; month <= totalMonths; month++) {
            // Annuity-due: deposit at the start of the month, then grow for the month.
            balance += monthlyContribution;
            balance *= (1.0 + monthlyRate);

            if (month % 12 == 0) {
                yearlyValues.add(balance);
            }
        }

        double totalInvested = initialLumpSum + (monthlyContribution * totalMonths);
        double futureValue = balance;
        double totalReturns = futureValue - totalInvested;

        return new Result(futureValue, totalInvested, totalReturns, yearlyValues);
    }
}
