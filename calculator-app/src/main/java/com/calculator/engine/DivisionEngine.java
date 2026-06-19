package com.calculator.engine;

/** Layer 3 leaf: division. NO observability code. */
public class DivisionEngine {
    public double divide(double a, double b) {
        if (b == 0) {
            throw new ArithmeticException("Division by zero");
        }
        double quotient = a / b;
        return quotient;
    }
}
