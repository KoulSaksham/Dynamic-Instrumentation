package com.calculator;

import com.calculator.handler.RouteHandler;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * ════════════════════════════════════════════════════════════
 *  TARGET APPLICATION — HTTP Calculator Server
 *  ────────────────────────────────────────────
 *  CONSTRAINT: This file contains ZERO logging, tracing, or
 *  observability logic.  It is written as if observability
 *  does not exist.
 * ════════════════════════════════════════════════════════════
 *
 * Starts an HTTP server on port 8080.
 *
 * Endpoint:
 *   GET /calculate?op=add&a=10&b=20
 *   GET /calculate?op=subtract&a=50&b=13
 *   GET /calculate?op=multiply&a=6&b=7
 *   GET /calculate?op=divide&a=100&b=4
 */
public class CalculatorServer {

    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/calculate", new RouteHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println("Calculator server listening on http://localhost:" + PORT + "/calculate");
        System.out.println("Try: curl \"http://localhost:8080/calculate?op=add&a=10&b=20\"");
    }
}
