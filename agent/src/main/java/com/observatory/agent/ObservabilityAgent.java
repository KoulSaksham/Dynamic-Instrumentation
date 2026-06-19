package com.observatory.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;

/**
 * Entry point for the Java instrumentation agent.
 *
 * HOW IT WORKS
 * ─────────────
 * The JVM calls premain() before the application's main() runs, handing us
 * an Instrumentation handle.  We hand that to ByteBuddy's AgentBuilder which:
 *
 *   1. Watches every class as it is loaded (TYPE_LOADING phase).
 *   2. When a class whose name matches one of our "breakpoint" targets is
 *      loaded, ByteBuddy weaves Advice bytecode into the chosen methods.
 *   3. The Advice classes (MethodEntryAdvice / MethodExitAdvice) fire at the
 *      start and end of each instrumented method.  They capture parameters
 *      (= initial local variables), the return value, and reconstruct the
 *      logical call stack via a thread-local frame list maintained in
 *      CallStackTracker.
 *
 * CONSTRAINT SATISFIED
 * ─────────────────────
 * The target application (CalculatorServer) is never modified.  All
 * instrumentation is injected purely through the -javaagent JVM flag.
 *
 * BREAKPOINT CONFIG
 * ──────────────────
 * Controlled by the agent argument string passed after the JAR path:
 *
 *   -javaagent:agent.jar=AdditionEngine#add
 *
 * If no argument is given the agent defaults to instrumenting ALL three
 * calculator classes so you can see the full call chain.
 */
public class ObservabilityAgent {

    // Default set of breakpoints (className#methodName) when none supplied
    private static final String[] DEFAULT_BREAKPOINTS = {
        "com.calculator.handler.RouteHandler#handle",
        "com.calculator.service.MathService#calculate",
        "com.calculator.engine.AdditionEngine#add",
        "com.calculator.engine.SubtractionEngine#subtract",
        "com.calculator.engine.MultiplicationEngine#multiply",
        "com.calculator.engine.DivisionEngine#divide"
    };

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║       OBSERVABILITY AGENT ATTACHED (ByteBuddy)          ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        String[] breakpoints = parseBreakpoints(agentArgs);

        AgentBuilder builder = new AgentBuilder.Default()
            // Don't instrument JDK internals or ByteBuddy itself
            .ignore(ElementMatchers.nameStartsWith("net.bytebuddy.")
                .or(ElementMatchers.nameStartsWith("sun."))
                .or(ElementMatchers.nameStartsWith("jdk."))
                .or(ElementMatchers.nameStartsWith("java."))
                .or(ElementMatchers.nameStartsWith("com.fasterxml.")));

        for (String bp : breakpoints) {
            builder = wireBreakpoint(builder, bp);
        }

        builder.installOn(inst);

        System.out.println("[Agent] Breakpoints armed: " + breakpoints.length);
        for (String bp : breakpoints) {
            System.out.println("        → " + bp);
        }
        System.out.println("[Agent] Ready. Waiting for HTTP requests...\n");
    }

    // Also supports agentmain for dynamic attach
    public static void agentmain(String agentArgs, Instrumentation inst) {
        premain(agentArgs, inst);
    }

    /**
     * Wire one "className#methodName" breakpoint into the AgentBuilder chain.
     * ByteBuddy will match the class by name and inject Advice into the method.
     */
    private static AgentBuilder wireBreakpoint(AgentBuilder builder, String breakpoint) {
        String[] parts = breakpoint.split("#");
        if (parts.length != 2) {
            System.err.println("[Agent] Skipping malformed breakpoint: " + breakpoint);
            return builder;
        }
        String className  = parts[0].trim();
        String methodName = parts[1].trim();

        return builder
            .type(ElementMatchers.named(className))
            .transform((b, typeDescription, classLoader, module, protectionDomain) ->
                b.visit(
                    Advice.to(MethodEntryAdvice.class, MethodExitAdvice.class)
                          .on(ElementMatchers.named(methodName))
                )
            );
    }

    private static String[] parseBreakpoints(String agentArgs) {
        if (agentArgs == null || agentArgs.isBlank()) {
            return DEFAULT_BREAKPOINTS;
        }
        // Support comma-separated list: e.g. "AdditionEngine#add,MathService#calculate"
        return agentArgs.split(",");
    }
}
