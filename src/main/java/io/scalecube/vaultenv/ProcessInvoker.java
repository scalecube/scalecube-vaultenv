package io.scalecube.vaultenv;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Signal;
import sun.misc.SignalHandler;

public final class ProcessInvoker {

  private static final Logger LOGGER = LoggerFactory.getLogger("VaultEnvironment");

  private final String cmd;
  private final Map<String, String> secrets;

  public ProcessInvoker(String cmd, Map<String, String> secrets) {
    this.secrets = Objects.requireNonNull(secrets, "secrets");
    this.cmd = Objects.requireNonNull(cmd, "command");
  }

  /**
   * Run the join.
   *
   * @throws IOException exception
   * @throws InterruptedException exption
   */
  public void runThenJoin() throws IOException, InterruptedException {
    LOGGER.info(
        "Run [{}], env: {}, secrets: {}", cmd, System.getenv(), toEnvironment(secrets, true));

    // Run process
    Process process = Runtime.getRuntime().exec(cmd, mergeSecrets(toEnvironment(secrets, false)));

    if (process.isAlive()) {
      SignalHandler handler = newSignalHandler(process, cmd);
      Signal.handle(new Signal("TERM"), handler);
      Signal.handle(new Signal("INT"), handler);
    }

    Thread stdout = runOutputFollower("stdout", process.getInputStream(), System.out);
    Thread stderr = runOutputFollower("stderr", process.getErrorStream(), System.err);

    stdout.join();
    stderr.join();

    LOGGER.info("Exited [{}], exit code {}", cmd, process.exitValue());
  }

  private static String[] mergeSecrets(Map<String, String> secretsEnv) {
    Map<String, String> environment = new HashMap<>();

    environment.putAll(System.getenv()); // parent env first
    environment.putAll(secretsEnv); // secrets second (can override)

    return environment.entrySet().stream()
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .toArray(String[]::new);
  }

  private static Map<String, String> toEnvironment(Map<String, String> secrets, boolean mask) {
    return secrets.entrySet().stream()
        .collect(
            Collectors.toMap(
                entry -> toEnvironment(entry.getKey()),
                entry -> mask ? mask(entry.getValue()) : entry.getValue()));
  }

  private static String toEnvironment(String key) {
    return key.replaceAll("[.\\-/#]", "_").toUpperCase();
  }

  private static String mask(String data) {
    if (data == null || data.isEmpty() || data.length() < 5) {
      return "*****";
    }
    return data.replace(data.substring(2, data.length() - 2), "***");
  }

  private static SignalHandler newSignalHandler(Process process, String cmd) {
    return signal -> {
      try {
        if (!process.isAlive()) {
          return;
        }
        LOGGER.debug("[destroy][{}] destroying process ...", cmd);
        process.destroyForcibly().waitFor();
        LOGGER.debug("[destroy][{}] destroyed process", cmd);
      } catch (InterruptedException e) {
        LOGGER.warn("InterruptedException occurred: {}", e.toString());
      }
    };
  }

  private static Runnable newOutputFollower(InputStream is, PrintStream ps) {
    return () -> {
      try (LineNumberReader reader = new LineNumberReader(new InputStreamReader(is))) {
        for (String s; (s = reader.readLine()) != null; ) {
          ps.println(s);
        }
      } catch (Exception e) {
        LOGGER.warn("Exception occurred: {}", e.toString());
      }
    };
  }

  private static Thread runOutputFollower(String name, InputStream is, PrintStream ps) {
    Thread thread = new Thread(newOutputFollower(is, ps), name);
    thread.start();
    return thread;
  }
}
