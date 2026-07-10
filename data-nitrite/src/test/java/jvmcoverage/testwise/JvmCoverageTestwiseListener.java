package jvmcoverage.testwise;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jacoco.agent.rt.IAgent;
import org.jacoco.agent.rt.RT;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

/**
 * Per-test JaCoCo collector. Resets the in-JVM agent probes before each test and dumps the
 * isolated execution data to {@code <dir>/<class>#<name>.exec} after it, where {@code <dir>}
 * is the system property {@code jvm-coverage-mcp.testwise.dir} (default {@code build/jvm-coverage/testwise}).
 * Activate testwise mode by setting {@code jvm-coverage-mcp.testwise.enabled=true} — the
 * listener is a no-op in all other test runs so your regular {@code test} task is unaffected.
 *
 * <p><b>Requires</b> the JaCoCo {@code -javaagent} attached to the test JVM (the Gradle
 * {@code jacoco} plugin / Maven {@code prepare-agent} do this). If no agent is present the
 * listener silently does nothing.
 *
 * <p><b>Caveat:</b> reset/dump-per-test assumes <b>sequential</b> test execution — one shared
 * agent per JVM. Parallel or forked execution cross-contaminates probes and is unsupported.
 */
public class JvmCoverageTestwiseListener implements TestExecutionListener {

  private static final Path DIR =
      Path.of(System.getProperty("jvm-coverage-mcp.testwise.dir", "build/jvm-coverage/testwise"));

  private IAgent agent() {
    try {
      return RT.getAgent();
    } catch (Throwable noAgent) {
      return null; // -javaagent not attached
    }
  }

  @Override
  public void executionStarted(TestIdentifier id) {
    if (!Boolean.getBoolean("jvm-coverage-mcp.testwise.enabled")) return;
    if (!id.isTest()) return;
    IAgent a = agent();
    if (a != null) a.reset();
  }

  private static boolean warnedNoAgent = false;

  private static synchronized void warnNoAgent() {
    if (warnedNoAgent) return;
    warnedNoAgent = true;
    System.err.println(
        "[testwise] NO JaCoCo agent in this test JVM — 0 per-test .exec files will be"
            + " written, and the testwise MCP tools will stay unavailable. Check:\n"
            + "  1. the jacoco plugin (Gradle) / prepare-agent (Maven) attaches -javaagent"
            + " to the test JVM;\n"
            + "  2. jacoco toolVersion MATCHES the org.jacoco.agent:runtime dependency"
            + " (0.8.15) — a version skew makes RT.getAgent() return null silently;\n"
            + "  3. tests run sequentially (maxParallelForks=1 / forkCount=1).");
  }

  @Override
  public void executionFinished(TestIdentifier id, TestExecutionResult result) {
    if (!Boolean.getBoolean("jvm-coverage-mcp.testwise.enabled")) return;
    if (!id.isTest()) return;
    IAgent a = agent();
    if (a == null) {
      warnNoAgent();
      return;
    }
    try {
      Files.createDirectories(DIR);
      byte[] data = a.getExecutionData(false);
      Files.write(DIR.resolve(fileName(id) + ".exec"), data);
    } catch (Exception e) {
      System.err.println("[testwise] dump failed for " + id.getDisplayName() + ": " + e);
    }
  }

  private static String fileName(TestIdentifier id) {
    String testClass = "unknown";
    String testName = id.getDisplayName();
    Object src = id.getSource().orElse(null);
    if (src instanceof MethodSource ms) {
      // Standard JUnit / TestNG
      testClass = ms.getClassName();
      testName = ms.getMethodName();
    } else if (src != null
        && src.getClass().getSimpleName().equals("UriSource")) {
      // Cucumber / Gherkin: source is a URI pointing to the .feature file.
      // Use the feature filename (sans extension) as the class surrogate so
      // scenarios from the same feature are grouped together in the DB.
      try {
        java.net.URI uri = (java.net.URI) src.getClass().getMethod("getUri").invoke(src);
        String path = uri.getPath();
        String file = path.substring(path.lastIndexOf('/') + 1);
        testClass = file.contains(".") ? file.substring(0, file.lastIndexOf('.')) : file;
      } catch (Exception ignored) {
        // fall through to "unknown"
      }
      // Display name is the scenario name — use as-is (sanitize handles special chars)
    }
    return sanitize(testClass) + "#" + sanitize(testName);
  }

  private static String sanitize(String s) {
    return s.replaceAll("[^A-Za-z0-9._-]", "_");
  }
}
