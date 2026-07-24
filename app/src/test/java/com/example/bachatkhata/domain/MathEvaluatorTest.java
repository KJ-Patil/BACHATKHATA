package com.example.bachatkhata.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

public class MathEvaluatorTest {

    private static final double EPS = 0.000001;

    private static void assertEvaluates(String expression, double expected) {
        assertEquals(expression, expected, MathEvaluator.evaluate(expression), EPS);
    }

    private static void assertRejects(String expression) {
        try {
            double result = MathEvaluator.evaluate(expression);
            fail("Expected '" + expression + "' to be rejected, got " + result);
        } catch (MathEvaluator.InvalidExpressionException expected) {
            // pass
        }
    }

    @Test
    public void evaluatesBasicOperators() {
        assertEvaluates("2+3", 5);
        assertEvaluates("10-4", 6);
        assertEvaluates("6*7", 42);
        assertEvaluates("9/3", 3);
    }

    @Test
    public void respectsOperatorPrecedence() {
        assertEvaluates("2+3*4", 14);
        assertEvaluates("2*3+4", 10);
        assertEvaluates("100-10/2", 95);
    }

    @Test
    public void operatorsAreLeftAssociative() {
        // 8/4/2 must be (8/4)/2 == 1, not 8/(4/2) == 4.
        assertEvaluates("8/4/2", 1);
        assertEvaluates("100-50-25", 25);
    }

    @Test
    public void respectsParentheses() {
        assertEvaluates("(2+3)*4", 20);
        assertEvaluates("2*(3+4)", 14);
        assertEvaluates("((1+2)*(3+4))", 21);
    }

    @Test
    public void handlesDecimals() {
        assertEvaluates("0.1+0.2", 0.3);
        assertEvaluates("1250.75*2", 2501.5);
        assertEvaluates("99.99", 99.99);
    }

    @Test
    public void handlesUnaryMinus() {
        assertEvaluates("-5", -5);
        assertEvaluates("-5+10", 5);
        assertEvaluates("10*-2", -20);
        assertEvaluates("(-3)*(-4)", 12);
        assertEvaluates("-(2+3)", -5);
    }

    @Test
    public void ignoresWhitespace() {
        assertEvaluates("  12  +   8  ", 20);
        assertEvaluates("1 200", 1200); // spaces inside a number are simply skipped
    }

    @Test
    public void rejectsUnknownCharacters() {
        assertRejects("2^3");
        assertRejects("5%2");
        assertRejects("abc");
        assertRejects("2+3;DROP");
    }

    @Test
    public void rejectsMismatchedParentheses() {
        assertRejects("(2+3");
        assertRejects("2+3)");
        assertRejects("((2+3)");
    }

    @Test
    public void rejectsDivisionByZero() {
        assertRejects("5/0");
        assertRejects("5/(3-3)");
    }

    @Test
    public void rejectsMalformedStructure() {
        assertRejects("");
        assertRejects("   ");
        assertRejects("2+");
        assertRejects("*5");
        assertRejects("2 3 +");
        assertRejects("1.2.3");
        assertRejects(".");
    }

    @Test
    public void tryEvaluateReturnsNullInsteadOfThrowing() {
        assertNull(MathEvaluator.tryEvaluate("2+"));
        assertNull(MathEvaluator.tryEvaluate("5/0"));
        assertNull(MathEvaluator.tryEvaluate(""));
        assertEquals(5.0, MathEvaluator.tryEvaluate("2+3"), EPS);
    }
}
