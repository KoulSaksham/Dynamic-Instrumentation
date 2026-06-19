package com.calculator.service;

import com.calculator.engine.AdditionEngine;
import com.calculator.engine.DivisionEngine;
import com.calculator.engine.MultiplicationEngine;
import com.calculator.engine.SubtractionEngine;

/**
 * Layer 2 of the call chain: business logic dispatch.
 *
 * Validates the operation name and routes to the correct engine.
 * The agent will intercept this method to observe: op, a, b as locals
 * and the engine's return value flowing back up.
 *
 * NO observability code.
 */
public class MathService {

    private final AdditionEngine       additionEngine       = new AdditionEngine();
    private final SubtractionEngine    subtractionEngine    = new SubtractionEngine();
    private final MultiplicationEngine multiplicationEngine = new MultiplicationEngine();
    private final DivisionEngine       divisionEngine       = new DivisionEngine();

    public double calculate(String operation, double a, double b) {
        switch (operation.toLowerCase()) {
            case "add":      return additionEngine.add(a, b);
            case "subtract": return subtractionEngine.subtract(a, b);
            case "multiply": return multiplicationEngine.multiply(a, b);
            case "divide":   return divisionEngine.divide(a, b);
            default:
                throw new IllegalArgumentException(
                    "Unknown operation: '" + operation + "'. Supported: add, subtract, multiply, divide"
                );
        }
    }
}
