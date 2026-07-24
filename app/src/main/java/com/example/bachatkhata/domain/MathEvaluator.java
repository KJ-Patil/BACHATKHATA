package com.example.bachatkhata.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Arithmetic expression evaluator for the floating calculator.
 *
 * <p>Shunting-yard: tokenize &rarr; RPN &rarr; evaluate. Supports the four basic
 * operators, decimals, parentheses and unary minus. Deliberately strict — any
 * character other than a digit, dot, parenthesis, operator or whitespace is
 * rejected rather than silently ignored, so a typo produces an error instead of
 * a plausible-looking wrong number.
 *
 * <p>Pure Java, no Android imports: unit-testable like {@link MoneyRule}.
 */
public final class MathEvaluator {

    /** Internal token for a negation sign, kept distinct from binary subtraction. */
    private static final String UNARY_MINUS = "u-";

    private MathEvaluator() {
    }

    /** Thrown for any malformed expression. The message is safe to show to the user. */
    public static class InvalidExpressionException extends IllegalArgumentException {
        public InvalidExpressionException(String message) {
            super(message);
        }
    }

    /**
     * Evaluates an arithmetic expression.
     *
     * @throws InvalidExpressionException on unknown characters, mismatched
     *                                    parentheses, division by zero, or a
     *                                    structurally malformed expression.
     */
    public static double evaluate(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new InvalidExpressionException("Empty expression");
        }
        List<String> tokens = tokenize(expression);
        List<String> rpn = toRpn(tokens);
        return evaluateRpn(rpn);
    }

    /**
     * Convenience for live "as you type" display: returns null instead of throwing
     * so a half-typed expression like {@code "12 +"} simply shows no result.
     */
    public static Double tryEvaluate(String expression) {
        try {
            double value = evaluate(expression);
            return (Double.isNaN(value) || Double.isInfinite(value)) ? null : value;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ── Tokenizer ───────────────────────────────────────────────────────────

    private static List<String> tokenize(String expression) {
        List<String> tokens = new ArrayList<>();
        StringBuilder number = new StringBuilder();

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);

            if (Character.isWhitespace(c)) {
                continue;
            }

            if (Character.isDigit(c) || c == '.') {
                number.append(c);
                continue;
            }

            // A completed number ends here.
            if (number.length() > 0) {
                tokens.add(flushNumber(number));
            }

            if (c == '+' || c == '-' || c == '*' || c == '/' || c == '(' || c == ')') {
                // Unary minus: a '-' at the start, or straight after an operator or '(',
                // is a sign rather than a subtraction. It becomes its own high-precedence
                // right-associative operator. (Rewriting it as "0 - x" would be wrong:
                // in 10*-2 the implicit zero binds to '*' first and yields -2, not -20.)
                if (c == '-' && isUnaryPosition(tokens)) {
                    tokens.add(UNARY_MINUS);
                } else {
                    tokens.add(String.valueOf(c));
                }
            } else {
                throw new InvalidExpressionException("Unexpected character '" + c + "'");
            }
        }

        if (number.length() > 0) {
            tokens.add(flushNumber(number));
        }
        if (tokens.isEmpty()) {
            throw new InvalidExpressionException("Empty expression");
        }
        return tokens;
    }

    private static String flushNumber(StringBuilder number) {
        String literal = number.toString();
        number.setLength(0);
        // Catches "1.2.3" and a bare "." before they reach parseDouble.
        if (literal.indexOf('.') != literal.lastIndexOf('.') || literal.equals(".")) {
            throw new InvalidExpressionException("Malformed number '" + literal + "'");
        }
        return literal;
    }

    /** True when a '-' at this point is a sign, not a subtraction operator. */
    private static boolean isUnaryPosition(List<String> tokens) {
        if (tokens.isEmpty()) return true;
        String previous = tokens.get(tokens.size() - 1);
        return isOperator(previous) || previous.equals("(");
    }

    // ── Shunting-yard ───────────────────────────────────────────────────────

    private static List<String> toRpn(List<String> tokens) {
        List<String> output = new ArrayList<>();
        Deque<String> operators = new ArrayDeque<>();

        for (String token : tokens) {
            if (isOperator(token)) {
                while (!operators.isEmpty() && isOperator(operators.peek())
                        && shouldPopBefore(operators.peek(), token)) {
                    output.add(operators.pop());
                }
                operators.push(token);
            } else if (token.equals("(")) {
                operators.push(token);
            } else if (token.equals(")")) {
                while (!operators.isEmpty() && !operators.peek().equals("(")) {
                    output.add(operators.pop());
                }
                if (operators.isEmpty()) {
                    throw new InvalidExpressionException("Mismatched parentheses");
                }
                operators.pop(); // discard the '('
            } else {
                output.add(token);
            }
        }

        while (!operators.isEmpty()) {
            String operator = operators.pop();
            if (operator.equals("(")) {
                throw new InvalidExpressionException("Mismatched parentheses");
            }
            output.add(operator);
        }
        return output;
    }

    // ── RPN evaluation ──────────────────────────────────────────────────────

    private static double evaluateRpn(List<String> rpn) {
        Deque<Double> stack = new ArrayDeque<>();

        for (String token : rpn) {
            if (token.equals(UNARY_MINUS)) {
                if (stack.isEmpty()) {
                    throw new InvalidExpressionException("Malformed expression");
                }
                stack.push(-stack.pop());
            } else if (isOperator(token)) {
                if (stack.size() < 2) {
                    throw new InvalidExpressionException("Malformed expression");
                }
                double right = stack.pop();
                double left = stack.pop();
                stack.push(apply(token, left, right));
            } else {
                try {
                    stack.push(Double.parseDouble(token));
                } catch (NumberFormatException e) {
                    throw new InvalidExpressionException("Malformed number '" + token + "'");
                }
            }
        }

        if (stack.size() != 1) {
            throw new InvalidExpressionException("Malformed expression");
        }
        return stack.pop();
    }

    private static double apply(String operator, double left, double right) {
        switch (operator) {
            case "+":
                return left + right;
            case "-":
                return left - right;
            case "*":
                return left * right;
            case "/":
                if (right == 0) {
                    throw new InvalidExpressionException("Cannot divide by zero");
                }
                return left / right;
            default:
                throw new InvalidExpressionException("Unknown operator '" + operator + "'");
        }
    }

    private static boolean isOperator(String token) {
        return token.equals("+") || token.equals("-") || token.equals("*")
                || token.equals("/") || token.equals(UNARY_MINUS);
    }

    private static int precedence(String operator) {
        if (operator.equals(UNARY_MINUS)) return 3;
        return (operator.equals("*") || operator.equals("/")) ? 2 : 1;
    }

    /**
     * Whether {@code stacked} should be emitted before {@code incoming} is pushed.
     *
     * <p>The binary operators are left-associative, so an equal-precedence operator
     * on the stack pops too — 8/4/2 must be (8/4)/2, not 8/(4/2). Unary minus is
     * right-associative, so it does not pop its equals: --5 is -(-5).
     */
    private static boolean shouldPopBefore(String stacked, String incoming) {
        int stackedPrecedence = precedence(stacked);
        int incomingPrecedence = precedence(incoming);
        if (stackedPrecedence > incomingPrecedence) return true;
        return stackedPrecedence == incomingPrecedence && !incoming.equals(UNARY_MINUS);
    }
}
