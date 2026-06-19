package com.calculator.engine;

/**
 * Layer 3 (leaf) of the call chain: addition.
 *
 * The agent's breakpoint fires here.  At this point the call stack is:
 *   AdditionEngine#add          ← deepest / innermost
 *   MathService#calculate
 *   RouteHandler#handle
 *   [JDK HTTP server frames]
 *
 * The agent captures: a, b as local variables; the sum as return value.
 *
 * NO observability code.
 */
public class AdditionEngine {

    public double add(double a, double b) {
        double sum = a + b;
        return sum;
    }
}
