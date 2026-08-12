package com.example.bachatkhata.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class LoanMathTest {

    /** ₹10,00,000 at 9% for 20 years — a typical home loan. */
    private static LoanMath.LoanTerms homeLoan(int monthsPaid) {
        return new LoanMath.LoanTerms(1_000_000, 9.0, 240, monthsPaid);
    }

    @Test
    public void emiMatchesTheReducingBalanceFormula() {
        double emi = LoanMath.emi(1_000_000, 9.0, 240);
        assertEquals(8997.26, emi, 0.5);
    }

    @Test
    public void zeroInterestSplitsThePrincipalEvenly() {
        assertEquals(1000.0, LoanMath.emi(12000, 0.0, 12), 0.001);
    }

    @Test
    public void settleTodayBalanceIsLessThanTheSumOfRemainingInstalments() {
        LoanMath.LoanTerms loan = homeLoan(60);

        double outstanding = LoanMath.outstandingPrincipal(loan);
        double left = LoanMath.remainingPayments(loan);

        // The gap between them is the interest an early payoff avoids. On a long
        // tenure it is large — conflating the two would overstate the debt hugely.
        assertTrue("settle-today must be cheaper than paying every instalment",
                outstanding < left);
        assertTrue("the gap should be the bulk of the remaining interest",
                left - outstanding > 500_000);
    }

    @Test
    public void outstandingIsNotPrincipalMinusInstalmentsPaid() {
        LoanMath.LoanTerms loan = homeLoan(60);

        double naive = 1_000_000 - LoanMath.emi(1_000_000, 9.0, 240) * 60;
        double actual = LoanMath.outstandingPrincipal(loan);

        // The naive subtraction amortizes far too fast, because an EMI is principal
        // AND interest. After 5 years of a 20-year loan the real balance is still
        // most of the principal.
        assertTrue(actual > naive + 400_000);
        assertTrue(actual > 850_000);
    }

    @Test
    public void aFreshLoanOwesItsWholePrincipal() {
        assertEquals(1_000_000, LoanMath.outstandingPrincipal(homeLoan(0)), 1.0);
    }

    @Test
    public void aRepaidLoanOwesNothing() {
        assertEquals(0.0, LoanMath.outstandingPrincipal(homeLoan(240)), 0.001);
        assertEquals(0.0, LoanMath.remainingPayments(homeLoan(240)), 0.001);

        // monthsPaid beyond the tenure must not go negative.
        assertEquals(0.0, LoanMath.outstandingPrincipal(homeLoan(300)), 0.001);
    }

    @Test
    public void zeroInterestBalanceIsTheUnpaidShareOfPrincipal() {
        LoanMath.LoanTerms loan = new LoanMath.LoanTerms(12000, 0.0, 12, 3);
        assertEquals(9000.0, LoanMath.outstandingPrincipal(loan), 0.001);
        // With no interest the two figures coincide.
        assertEquals(9000.0, LoanMath.remainingPayments(loan), 0.001);
    }

    @Test
    public void totalInterestIsTheScheduleMinusThePrincipal() {
        double interest = LoanMath.totalInterest(homeLoan(0));
        double payable = LoanMath.emi(1_000_000, 9.0, 240) * 240;
        assertEquals(payable - 1_000_000, interest, 0.5);
        assertEquals(0.0, LoanMath.totalInterest(new LoanMath.LoanTerms(12000, 0, 12, 0)), 0.001);
    }

    @Test
    public void totalDebtFallsAsInstalmentsArePaid() {
        double early = LoanMath.totalDebt(Arrays.asList(homeLoan(12), homeLoan(12)));
        double later = LoanMath.totalDebt(Arrays.asList(homeLoan(200), homeLoan(200)));

        assertTrue("debt must move as the loans amortize", later < early);
        assertEquals(0.0, LoanMath.totalDebt(Arrays.asList(homeLoan(240), homeLoan(240))), 0.001);
    }

    @Test
    public void amortizationAgreesWithTheIndividualHelpers() {
        LoanMath.LoanTerms loan = homeLoan(60);
        LoanMath.Amortization schedule = LoanMath.amortize(loan);

        assertEquals(LoanMath.emi(1_000_000, 9.0, 240), schedule.emi, 0.001);
        assertEquals(LoanMath.outstandingPrincipal(loan), schedule.outstandingBalance, 0.001);
        assertEquals(LoanMath.remainingPayments(loan), schedule.remainingPayments, 0.001);
        assertEquals(60, schedule.paidMonths);
        assertEquals(180, schedule.remainingMonths);
        assertEquals(25.0, schedule.progressPct, 0.001);
    }

    @Test
    public void degenerateInputsDoNotThrow() {
        assertEquals(0.0, LoanMath.emi(0, 9, 240), 0.001);
        assertEquals(0.0, LoanMath.emi(1000, 9, 0), 0.001);
        assertEquals(0.0, LoanMath.outstandingPrincipal(null), 0.001);
        assertEquals(0.0, LoanMath.remainingPayments(null), 0.001);
        assertEquals(0.0, LoanMath.totalDebt(null), 0.001);
        assertEquals(0.0, LoanMath.amortize(null).emi, 0.001);
    }
}
