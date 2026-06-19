#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════
#  run.sh — Build & Launch the Dynamic Instrumentation PoC
# ════════════════════════════════════════════════════════════════════════
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

AGENT_JAR="agent/target/agent-1.0-SNAPSHOT.jar"
TARGET_JAR="calculator-app/target/calculator-server.jar"

# ── 1. Build ─────────────────────────────────────────────────────────────
echo "▶ Building all modules..."
mvn clean package -q
echo "✔ Build complete."
echo ""

# ── 2. Verify JARs exist ─────────────────────────────────────────────────
if [ ! -f "$AGENT_JAR" ]; then
  echo "✘ Agent JAR not found at $AGENT_JAR"; exit 1
fi
if [ ! -f "$TARGET_JAR" ]; then
  echo "✘ Target JAR not found at $TARGET_JAR"; exit 1
fi

# ── 3. Optional: custom breakpoints via first arg ─────────────────────────
#  Usage: ./run.sh "com.calculator.engine.AdditionEngine#add"
#  Leave empty to instrument all calculator layers (default)
AGENT_ARGS="${1:-}"

# ── 4. Launch ─────────────────────────────────────────────────────────────
echo "▶ Starting Calculator Server with Observability Agent..."
echo "  Agent JAR  : $AGENT_JAR"
echo "  Target JAR : $TARGET_JAR"
echo "  Breakpoints: ${AGENT_ARGS:-<all calculator methods (default)>}"
echo ""
echo "  Test with:"
echo "    curl \"http://localhost:8080/calculate?op=add&a=10&b=20\""
echo "    curl \"http://localhost:8080/calculate?op=multiply&a=6&b=7\""
echo "    curl \"http://localhost:8080/calculate?op=divide&a=100&b=4\""
echo "    curl \"http://localhost:8080/calculate?op=subtract&a=50&b=13\""
echo ""
echo "  Output also written to: instrumentation-output.json"
echo "════════════════════════════════════════════════════════════════════════"
echo ""

java \
  -javaagent:"$AGENT_JAR${AGENT_ARGS:+=$AGENT_ARGS}" \
  -jar "$TARGET_JAR"
