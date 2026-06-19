# ════════════════════════════════════════════════════════════════════════
#  Dockerfile — Build & Launch the Dynamic Instrumentation PoC
#  (equivalent to run.sh)
# ════════════════════════════════════════════════════════════════════════

# ── 1. Build ─────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /build

COPY pom.xml .
COPY agent/pom.xml agent/
COPY calculator-app/pom.xml calculator-app/
COPY agent/src agent/src
COPY calculator-app/src calculator-app/src

RUN mvn clean package -q -B

# ── 2. Verify JARs exist (build fails if Maven did not produce them) ─────
RUN test -f agent/target/agent-1.0-SNAPSHOT.jar \
    && test -f calculator-app/target/calculator-server.jar

# ── 3. Runtime ───────────────────────────────────────────────────────────
FROM eclipse-temurin:11-jre
WORKDIR /app

COPY --from=build /build/agent/target/agent-1.0-SNAPSHOT.jar /app/agent.jar
COPY --from=build /build/calculator-app/target/calculator-server.jar /app/calculator-server.jar

# Optional custom breakpoints (same as run.sh first argument).
# Examples:
#   docker run -e AGENT_ARGS="com.calculator.engine.AdditionEngine#add" ...
#   docker run -e AGENT_ARGS="com.calculator.service.MathService#calculate,com.calculator.engine.MultiplicationEngine#multiply" ...
# Leave unset to instrument all calculator methods (default).
ENV AGENT_ARGS=""

EXPOSE 8080

# Output also written to instrumentation-output.json inside the container.
CMD ["sh", "-c", "exec java -javaagent:/app/agent.jar${AGENT_ARGS:+=$AGENT_ARGS} -jar /app/calculator-server.jar"]
