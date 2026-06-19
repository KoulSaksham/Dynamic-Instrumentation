package com.observatory.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-local bookkeeping for the instrumented call stack.
 *
 * DESIGN
 * ──────
 * Each thread gets its own Deque<FrameSnapshot> (a stack).
 * pushFrame() is called from MethodEntryAdvice — adds a frame.
 * popFrame()  is called from MethodExitAdvice  — enriches the frame with the
 *             return value and moves it to a "completed" list.
 *
 * When depth returns to zero (outermost instrumented method returns), we emit
 * a JSON snapshot of ALL frames in the chain — ordered outermost → innermost.
 *
 * The thread-local is reset after emission so the next request starts clean.
 *
 * OUTPUT FORMAT (per HTTP request):
 * ─────────────────────────────────
 * {
 *   "snapshotId": 1,
 *   "timestamp":  "...",
 *   "thread":     "pool-1-thread-1",
 *   "capturedFrames": [
 *     {
 *       "depth": 0,               ← 0 = outermost (RouteHandler)
 *       "className":    "...",
 *       "methodName":   "...",
 *       "localVariables": { "op":"add", "a":"10.0", "b":"20.0" },
 *       "returnValue":  "30.0"
 *     },
 *     ...                          ← MathService frame
 *     { "depth": 2, ... }          ← AdditionEngine frame (deepest)
 *   ],
 *   "jvmCallStack": [              ← raw JVM stack from deepest point
 *     "com.calculator.engine.AdditionEngine.add(AdditionEngine.java:12)",
 *     ...
 *   ]
 * }
 */
public class CallStackTracker {

    /** Live stack of currently-executing instrumented frames (per thread). */
    private static final ThreadLocal<Deque<FrameSnapshot>> LIVE_STACK =
        ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * Completed frames collected in this request, in pop order
     * (innermost first, we reverse before output).
     */
    private static final ThreadLocal<List<FrameSnapshot>> COMPLETED =
        ThreadLocal.withInitial(ArrayList::new);

    /** Deepest JVM call stack captured so far in this request. */
    private static final ThreadLocal<StackTraceElement[]> DEEPEST_STACK =
        ThreadLocal.withInitial(() -> new StackTraceElement[0]);

    private static final AtomicInteger REQUEST_COUNTER = new AtomicInteger(0);

    private static final ObjectMapper JSON = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private static final String OUTPUT_FILE = "instrumentation-output.json";

    // ─────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────

    public static void pushFrame(
            String className,
            String methodName,
            String signature,
            Map<String, String> localVariables,
            StackTraceElement[] jvmStack
    ) {
        Deque<FrameSnapshot> stack = LIVE_STACK.get();
        int depth = stack.size(); // 0 = outermost

        FrameSnapshot frame = new FrameSnapshot(
            className, methodName, signature, localVariables, depth
        );
        stack.push(frame);

        // Keep the deepest (longest) JVM stack for the snapshot
        StackTraceElement[] filtered = filterStack(jvmStack);
        if (filtered.length > DEEPEST_STACK.get().length) {
            DEEPEST_STACK.set(filtered);
        }
    }

    public static void popFrame(String className, String methodName, String returnValue) {
        Deque<FrameSnapshot> stack = LIVE_STACK.get();
        if (stack.isEmpty()) return;

        FrameSnapshot top = stack.peek();
        if (top.className.equals(className) && top.methodName.equals(methodName)) {
            top.returnValue = returnValue;
            stack.pop();
            COMPLETED.get().add(top);
        }

        // Outermost frame just returned — emit the full snapshot
        if (stack.isEmpty()) {
            emitAndReset();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Emission
    // ─────────────────────────────────────────────────────────────────────

    private static synchronized void emitAndReset() {
        List<FrameSnapshot> completed = COMPLETED.get();
        StackTraceElement[] deepStack  = DEEPEST_STACK.get();

        if (completed.isEmpty()) return;

        // completed is in pop order (innermost first); reverse → outermost first
        List<FrameSnapshot> ordered = new ArrayList<>(completed);
        Collections.reverse(ordered);

        int reqNum = REQUEST_COUNTER.incrementAndGet();

        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("snapshotId", reqNum);
            snapshot.put("timestamp",  Instant.now().toString());
            snapshot.put("thread",     Thread.currentThread().getName());

            List<Map<String, Object>> frameList = new ArrayList<>();
            for (FrameSnapshot f : ordered) {
                Map<String, Object> fm = new LinkedHashMap<>();
                fm.put("depth",          f.depth);
                fm.put("className",      f.className);
                fm.put("methodName",     f.methodName);
                fm.put("signature",      f.signature);
                fm.put("localVariables", f.localVariables);
                fm.put("returnValue",    f.returnValue);
                frameList.add(fm);
            }
            snapshot.put("capturedFrames", frameList);

            List<String> rawStack = new ArrayList<>();
            for (StackTraceElement el : deepStack) rawStack.add(el.toString());
            snapshot.put("jvmCallStack", rawStack);

            String pretty = JSON.writeValueAsString(snapshot);

            // ── stdout ────────────────────────────────────────────────────
            System.out.println("\n" + "═".repeat(70));
            System.out.println("  INSTRUMENTATION SNAPSHOT  #" + reqNum
                + "  [" + Thread.currentThread().getName() + "]");
            System.out.println("═".repeat(70));
            System.out.println(pretty);
            System.out.println("═".repeat(70) + "\n");

            // ── file (NDJSON — one compact JSON object per line) ──────────
            try (PrintWriter fw = new PrintWriter(new FileWriter(OUTPUT_FILE, true))) {
                fw.println(JSON.writer()
                    .without(SerializationFeature.INDENT_OUTPUT)
                    .writeValueAsString(snapshot));
            }

        } catch (Exception e) {
            System.err.println("[Agent] Snapshot emission failed: " + e.getMessage());
        } finally {
            // Always reset thread-local state for the next request
            LIVE_STACK.get().clear();
            COMPLETED.get().clear();
            DEEPEST_STACK.set(new StackTraceElement[0]);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private static StackTraceElement[] filterStack(StackTraceElement[] raw) {
        if (raw == null) return new StackTraceElement[0];
        List<StackTraceElement> out = new ArrayList<>();
        for (StackTraceElement el : raw) {
            String cls = el.getClassName();
            if (cls.startsWith("java.lang.Thread"))      continue;
            if (cls.startsWith("com.observatory.agent")) continue;
            if (cls.contains("$$ByteBuddy"))             continue;
            if (cls.startsWith("net.bytebuddy"))         continue;
            if (cls.startsWith("sun.reflect"))           continue;
            if (cls.startsWith("jdk.internal"))          continue;
            out.add(el);
        }
        return out.toArray(new StackTraceElement[0]);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Frame data holder
    // ─────────────────────────────────────────────────────────────────────

    static class FrameSnapshot {
        final String              className;
        final String              methodName;
        final String              signature;
        final Map<String, String> localVariables;
        final int                 depth;
        String                    returnValue = "<pending>";

        FrameSnapshot(String className, String methodName, String signature,
                      Map<String, String> localVariables, int depth) {
            this.className      = className;
            this.methodName     = methodName;
            this.signature      = signature;
            this.localVariables = localVariables;
            this.depth          = depth;
        }
    }
}
