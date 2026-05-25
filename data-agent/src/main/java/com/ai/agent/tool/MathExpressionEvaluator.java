package com.ai.agent.tool;

/**
 * 为 Agent 工具计算简单算术表达式。
 *
 * @author data-agent
 */
public class MathExpressionEvaluator {

    private static final char SPACE_CHAR = ' ';

    private static final char PLUS_OPERATOR = '+';

    private static final char MINUS_OPERATOR = '-';

    private static final char MULTIPLY_OPERATOR = '*';

    private static final char DIVIDE_OPERATOR = '/';

    private static final char LEFT_PARENTHESES = '(';

    private static final char RIGHT_PARENTHESES = ')';

    private static final char DECIMAL_POINT = '.';

    private static final char ZERO_CHAR = '0';

    private static final char NINE_CHAR = '9';

    private final String expression;

    private int position = -1;

    private int currentChar;

    public MathExpressionEvaluator(String expression) {
        this.expression = expression.trim();
    }

    public double evaluate() {
        nextChar();
        double value = parseExpression();
        if (position < expression.length()) {
            throw new IllegalArgumentException("Unexpected: " + (char) currentChar);
        }
        return value;
    }

    private void nextChar() {
        currentChar = (++position < expression.length()) ? expression.charAt(position) : -1;
    }

    private boolean eat(int expectedChar) {
        while (currentChar == SPACE_CHAR) {
            nextChar();
        }
        if (currentChar == expectedChar) {
            nextChar();
            return true;
        }
        return false;
    }

    private double parseExpression() {
        double value = parseTerm();
        while (true) {
            if (eat(PLUS_OPERATOR)) {
                value += parseTerm();
            } else if (eat(MINUS_OPERATOR)) {
                value -= parseTerm();
            } else {
                return value;
            }
        }
    }

    private double parseTerm() {
        double value = parseFactor();
        while (true) {
            if (eat(MULTIPLY_OPERATOR)) {
                value = parseMultiplyOrPower(value);
            } else if (eat(DIVIDE_OPERATOR)) {
                value /= parseFactor();
            } else {
                return value;
            }
        }
    }

    private double parseMultiplyOrPower(double leftValue) {
        if (currentChar == MULTIPLY_OPERATOR) {
            nextChar();
            return Math.pow(leftValue, parseFactor());
        }
        return leftValue * parseFactor();
    }

    private double parseFactor() {
        if (eat(PLUS_OPERATOR)) {
            return parseFactor();
        }
        if (eat(MINUS_OPERATOR)) {
            return -parseFactor();
        }

        int startPosition = position;
        if (eat(LEFT_PARENTHESES)) {
            double value = parseExpression();
            eat(RIGHT_PARENTHESES);
            return value;
        }
        if (isDigitOrDecimalPoint(currentChar)) {
            while (isDigitOrDecimalPoint(currentChar)) {
                nextChar();
            }
            return Double.parseDouble(expression.substring(startPosition, position));
        }
        throw new IllegalArgumentException("Unexpected: " + (char) currentChar);
    }

    private static boolean isDigitOrDecimalPoint(int value) {
        return (value >= ZERO_CHAR && value <= NINE_CHAR) || value == DECIMAL_POINT;
    }
}
