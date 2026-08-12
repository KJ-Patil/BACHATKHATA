package com.example.bachatkhata.domain;

/**
 * Reducing-balance loan math — the single source of truth for every EMI and debt
 * figure in the app (EMI Tracker, the Add-Loan preview, the calendar's due-date
 * projection, and the health score's debt input).
 *
 * <p><b>Outstanding balance is not the same thing as remaining payments.</b> Both
 * are legitimate figures and they answer different questions:
 *
 * <ul>
 *   <li>{@link #outstandingPrincipal} — what would <em>settle the loan today</em>:
 *       the present value of the instalments still due. Label it "Outstanding" or
 *       "Settle-today balance".</li>
 *   <li>{@link #remainingPayments} — the <em>sum of future instalments</em>, i.e.
 *       principal plus every future interest charge. Label it "left to pay",
 *       never "outstanding".</li>
 * </ul>
 *
 * <p>{@code EMI × k} is strictly larger than the settle-today balance; the gap is
 * the interest avoided by paying the loan off early. On a long tenure that gap is
 * most of the interest cost, so reporting it as remaining principal overstates the
 * debt enormously — and the health score reads the debt figure, so the error
 * spreads beyond the EMI screen.
 *
 * <p>Never derive the balance as {@code principal − EMI × monthsPaid}: an EMI is
 * principal <em>and</em> interest, so subtracting whole instalments from principal
 * amortizes the loan far too fast and reaches zero well before it is repaid.
 */
public final class LoanMath {

    private LoanMath() {
    }

    /** The inputs every helper here needs. */
    public static final class LoanTerms {
        public final double principal;
        public final double annualInterestRate;
        public final int tenureMonths;
        public final int monthsPaid;

        public LoanTerms(double principal, double annualInterestRate,
                         int tenureMonths, int monthsPaid) {
            this.principal = principal;
            this.annualInterestRate = annualInterestRate;
            this.tenureMonths = tenureMonths;
            this.monthsPaid = monthsPaid;
        }
    }

    /** Every derived figure for one loan, computed once. */
    public static final class Amortization {
        public final double emi;
        public final double totalPayable;
        public final double totalInterest;
        /** Instalments already paid. */
        public final int paidMonths;
        /** Instalments still due. */
        public final int remainingMonths;
        /** Sum of instalments already paid. */
        public final double amountPaid;
        /** Settle-today balance. */
        public final double outstandingBalance;
        /** Sum of instalments still due — always &ge; outstandingBalance. */
        public final double remainingPayments;
        /** 0–100. */
        public final double progressPct;

        Amortization(double emi, double totalPayable, double totalInterest,
                     int paidMonths, int remainingMonths, double amountPaid,
                     double outstandingBalance, double remainingPayments,
                     double progressPct) {
            this.emi = emi;
            this.totalPayable = totalPayable;
            this.totalInterest = totalInterest;
            this.paidMonths = paidMonths;
            this.remainingMonths = remainingMonths;
            this.amountPaid = amountPaid;
            this.outstandingBalance = outstandingBalance;
            this.remainingPayments = remainingPayments;
            this.progressPct = progressPct;
        }
    }

    /**
     * Equated Monthly Installment on a reducing balance:
     * {@code P·r(1+r)^n / ((1+r)^n − 1)}, or the straight-line {@code P/n} at 0%.
     */
    public static double emi(double principal, double annualRate, int tenureMonths) {
        if (principal <= 0 || tenureMonths <= 0) return 0.0;
        if (annualRate <= 0) return principal / tenureMonths;

        double r = annualRate / 12.0 / 100.0;
        double growth = Math.pow(1.0 + r, tenureMonths);
        return (principal * r * growth) / (growth - 1.0);
    }

    /**
     * The <b>settle-today balance</b>: the present value of the instalments still
     * due, {@code EMI × (1 − (1+r)^−k) / r} where {@code k} is instalments left.
     *
     * <p>Short-circuits: a fully repaid loan is 0, and at 0% interest the balance
     * is simply the unpaid share of the principal ({@code P × k / n}).
     */
    public static double outstandingPrincipal(LoanTerms loan) {
        if (loan == null) return 0.0;
        int k = remainingMonths(loan);
        if (k <= 0 || loan.principal <= 0 || loan.tenureMonths <= 0) return 0.0;

        if (loan.annualInterestRate <= 0) {
            return loan.principal * k / (double) loan.tenureMonths;
        }

        double r = loan.annualInterestRate / 12.0 / 100.0;
        double instalment = emi(loan.principal, loan.annualInterestRate, loan.tenureMonths);
        double balance = instalment * (1.0 - Math.pow(1.0 + r, -k)) / r;
        return Math.max(0.0, balance);
    }

    /**
     * The <b>sum of instalments still due</b> — principal plus all future interest.
     * This is "left to pay", not the outstanding balance.
     */
    public static double remainingPayments(LoanTerms loan) {
        if (loan == null) return 0.0;
        int k = remainingMonths(loan);
        if (k <= 0) return 0.0;
        return emi(loan.principal, loan.annualInterestRate, loan.tenureMonths) * k;
    }

    /** Total interest over the full original schedule. */
    public static double totalInterest(LoanTerms loan) {
        if (loan == null || loan.principal <= 0 || loan.tenureMonths <= 0) return 0.0;
        double payable = emi(loan.principal, loan.annualInterestRate, loan.tenureMonths)
                * loan.tenureMonths;
        return Math.max(0.0, payable - loan.principal);
    }

    /** Every figure for one loan, so a screen computes the schedule once. */
    public static Amortization amortize(LoanTerms loan) {
        if (loan == null) {
            return new Amortization(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        double instalment = emi(loan.principal, loan.annualInterestRate, loan.tenureMonths);
        int paid = paidMonths(loan);
        int left = remainingMonths(loan);
        double payable = instalment * Math.max(0, loan.tenureMonths);
        double progress = loan.tenureMonths > 0
                ? (paid * 100.0) / loan.tenureMonths
                : 0.0;

        return new Amortization(
                instalment,
                payable,
                totalInterest(loan),
                paid,
                left,
                instalment * paid,
                outstandingPrincipal(loan),
                remainingPayments(loan),
                progress);
    }

    /** {@code monthsPaid} clamped into {@code 0…tenureMonths}. */
    public static int paidMonths(LoanTerms loan) {
        if (loan == null || loan.tenureMonths <= 0) return 0;
        return Math.max(0, Math.min(loan.monthsPaid, loan.tenureMonths));
    }

    /** Instalments still due. */
    public static int remainingMonths(LoanTerms loan) {
        if (loan == null || loan.tenureMonths <= 0) return 0;
        return loan.tenureMonths - paidMonths(loan);
    }

    /**
     * Total debt across a portfolio — the sum of every loan's settle-today balance.
     *
     * <p>This is what the health score's debt input must read. Summing a stored
     * "outstanding" field instead would report the original principal forever,
     * because nothing writes that field back as instalments are paid.
     */
    public static double totalDebt(Iterable<LoanTerms> loans) {
        if (loans == null) return 0.0;
        double sum = 0.0;
        for (LoanTerms loan : loans) {
            sum += outstandingPrincipal(loan);
        }
        return sum;
    }
}
