package com.calculator.handler;

import com.calculator.service.MathService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Layer 1 of the call chain: HTTP routing.
 *
 * Parses the query string, validates required parameters, and delegates
 * to MathService for the actual computation.
 *
 * Call chain:
 *   RouteHandler#handle → MathService#calculate → [Op]Engine#[op]
 *
 * NO observability code anywhere in this file.
 */
public class RouteHandler implements HttpHandler {

    private final MathService mathService = new MathService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if (!"GET".equalsIgnoreCase(method)) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        Map<String, String> params = parseQuery(exchange.getRequestURI());

        String op = params.get("op");
        String aStr = params.get("a");
        String bStr = params.get("b");

        if (op == null || aStr == null || bStr == null) {
            sendResponse(exchange, 400,
                "{\"error\":\"Missing parameters. Required: op, a, b\"}");
            return;
        }

        try {
            double a = Double.parseDouble(aStr);
            double b = Double.parseDouble(bStr);

            double result = mathService.calculate(op, a, b);

            String body = String.format(
                "{\"operation\":\"%s\",\"a\":%s,\"b\":%s,\"result\":%s}",
                op, aStr, bStr, formatResult(result)
            );
            sendResponse(exchange, 200, body);

        } catch (NumberFormatException e) {
            sendResponse(exchange, 400, "{\"error\":\"Parameters a and b must be numbers\"}");
        } catch (IllegalArgumentException e) {
            sendResponse(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
        } catch (ArithmeticException e) {
            sendResponse(exchange, 422, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private Map<String, String> parseQuery(URI uri) {
        Map<String, String> params = new HashMap<>();
        String query = uri.getQuery();
        if (query == null) return params;

        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(kv[0], kv[1]);
            }
        }
        return params;
    }

    private String formatResult(double result) {
        // Return integer representation if result is a whole number
        if (result == Math.floor(result) && !Double.isInfinite(result)) {
            return String.valueOf((long) result);
        }
        return String.valueOf(result);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
