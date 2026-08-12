package com.example.bachatkhata;

import com.example.bachatkhata.domain.LoanMath;

/**
 * Thin alias kept for the existing call sites. The formula itself lives in
 * {@link LoanMath}, which is also where the outstanding-balance and
 * remaining-payments figures come from — keeping one copy is the whole point,
 * so add new loan math there rather than here.
 */
public class EmiCalculator {

    /**
     * Calculates the Equated Monthly Installment (EMI) for a loan.
     * Formula: EMI = [P x r x (1+r)^n] / [((1+r)^n) - 1]
     */
    public static double calculateEmi(double principal, double annualRate, int tenureMonths) {
        return LoanMath.emi(principal, annualRate, tenureMonths);
    }
}
