# Dynamic Instrumentation PoC — Java / ByteBuddy

A minimal proof-of-concept for a dynamic observability agent.  
Two logical components in one Maven multi-module project.

---

## Architecture

```
dynamic-instrumentation/
├── pom.xml                        ← root multi-module POM
├── run.sh                         ← build + launch script
│
├── agent/                         ← MODULE 1: Instrumentation Agent
│   └── src/main/java/com/observatory/agent/
│       ├── ObservabilityAgent.java     ← premain() entry, ByteBuddy wiring
│       ├── MethodEntryAdvice.java      ← captures params + call stack on entry
│       ├── MethodExitAdvice.java       ← captures return value on exit
│       └── CallStackTracker.java       ← thread-local stack + JSON emitter
│
└── target/                        ← MODULE 2: HTTP Calculator (zero observability)
    └── src/main/java/com/calculator/
        ├── CalculatorServer.java       ← main(), JDK HttpServer
        ├── handler/RouteHandler.java   ← Layer 1: HTTP parsing & routing
        ├── service/MathService.java    ← Layer 2: operation dispatch
        └── engine/
            ├── AdditionEngine.java     ← Layer 3: a + b
            ├── SubtractionEngine.java  ← Layer 3: a - b
            ├── MultiplicationEngine.java ← Layer 3: a * b
            └── DivisionEngine.java     ← Layer 3: a / b
```

---

## Prerequisites

- Java 11+
- Maven 3.6+

---

## Quick Start

```bash
chmod +x run.sh
./run.sh
```

Then in another terminal:

```bash
curl "http://localhost:8080/calculate?op=add&a=10&b=20"
curl "http://localhost:8080/calculate?op=multiply&a=6&b=7"
curl "http://localhost:8080/calculate?op=divide&a=100&b=4"
curl "http://localhost:8080/calculate?op=subtract&a=50&b=13"
```

---

## How It Works

### The Constraint

The target application (`com.calculator.*`) contains **zero** logging, tracing,
or observability code. It uses only the JDK built-in HTTP server.

### The Mechanism

1. **`-javaagent` flag** — The JVM loads `agent.jar` before `CalculatorServer.main()`
   runs, calling `ObservabilityAgent.premain()`.

2. **ByteBuddy AgentBuilder** — Watches every class as it loads. When a class
   matching a configured breakpoint (e.g. `com.calculator.engine.AdditionEngine`)
   is loaded, ByteBuddy **weaves Advice bytecode** directly into the method.

3. **Advice weaving (not proxies)** — `MethodEntryAdvice` and `MethodExitAdvice`
   are compiled into the target method's bytecode inline. No reflection at call time,
   no interface changes, no wrapping. The target class never knew it happened.

4. **On each HTTP request**, the call chain fires:
   ```
   RouteHandler#handle
     └─ MathService#calculate
          └─ AdditionEngine#add    ← breakpoint fires here
   ```
   At each intercepted frame:
   - **Entry**: parameters (= initial local variables) are captured
   - **Exit**: return value is captured
   - **JVM stack**: `Thread.currentThread().getStackTrace()` gives the real call chain

5. **Output** — A JSON snapshot is printed to stdout AND appended to
   `instrumentation-output.json`. The HTTP response completes normally.

---

## What the Snapshot Contains

```json
{
  "snapshotId": 1,
  "timestamp": "2024-01-15T10:30:45.123Z",
  "thread": "pool-1-thread-1",
  "trigger": "com.calculator.engine.AdditionEngine#add",
  "capturedFrames": [
    {
      "depth": 0,
      "className": "com.calculator.engine.AdditionEngine",
      "methodName": "add",
      "localVariables": {
        "this": "AdditionEngine",
        "a": "10.0",
        "b": "20.0"
      },
      "returnValue": "30.0"
    }
  ],
  "jvmCallStack": [
    "com.calculator.engine.AdditionEngine.add(AdditionEngine.java:12)",
    "com.calculator.service.MathService.calculate(MathService.java:18)",
    "com.calculator.handler.RouteHandler.handle(RouteHandler.java:45)",
    "com.sun.net.httpserver.ServerImpl$Exchange.run(ServerImpl.java:...)",
    "..."
  ]
}
```

---

## Custom Breakpoints

Instrument only a specific method by passing it as an argument:

```bash
# Single breakpoint
./run.sh "com.calculator.engine.DivisionEngine#divide"

# Multiple breakpoints (comma-separated)
./run.sh "com.calculator.service.MathService#calculate,com.calculator.engine.MultiplicationEngine#multiply"
```

## The ByteBuddy Workaround Explained

**Challenge:** ByteBuddy Advice cannot read *arbitrary mid-method locals* the way
JDWP can. It operates at bytecode-weave time, not at runtime debug level.

**Workaround:** We instrument at **method boundaries** (entry + exit) rather than
arbitrary line numbers. At a method's entry point, the only locals in scope *are*
the parameters (slots 0..N in the local variable table). By capturing all parameters
on entry and the return value on exit for every frame in the chain, we reconstruct
the complete data flow — which is the assignment's observable goal.

For true mid-method local variable reading at arbitrary lines, the JPDA/JDI approach
(Approach 1) would be needed. ByteBuddy gives us zero-overhead production-style
instrumentation that satisfies the assignment's intent.
