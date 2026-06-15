package com.example.bachatkhata;

public class EmiCalculator {

    /**
     * Calculates the Equated Monthly Installment (EMI) for a loan.
     * Formula: EMI = [P x r x (1+r)^n] / [((1+r)^n) - 1]
     * where:
     * P = Principal loan amount
     * r = Monthly interest rate = (Annual Rate / 12 / 100)
     * n = Loan tenure in months
     */
    public static double calculateEmi(double principal, double annualRate, int tenureMonths) {
        if (principal <= 0 || tenureMonths <= 0) {
            return 0.0;
        }
        if (annualRate <= 0) {
            return principal / tenureMonths;
        }

        double monthlyRate = annualRate / 12.0 / 100.0;
        double emi = (principal * monthlyRate * Math.pow(1.0 + monthlyRate, tenureMonths)) 
                / (Math.pow(1.0 + monthlyRate, tenureMonths) - 1.0);

        return emi;
    }
}
