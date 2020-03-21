package io.scalecube.vaultenv;

import com.bettercloud.vault.json.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Signal;
import sun.misc.SignalHandler;

final class ProcessInvoker {

  private static final Logger LOGGER = LoggerFactory.getLogger("VaultEnvironment");

  private static final boolean IS_RUNNING_ON_WINDOWS =
      System.getProperty("os.name").startsWith("Windows");
  private static final String WINDOWS_TASKKILL_FORMAT = "taskkill /F /T /PID %s";

  private final String cmd;
  private final Map<String, String> secrets;
  private final RunningMode mode;

  ProcessInvoker(String cmd, Map<String, String> secrets, RunningMode runningMode) {
    this.cmd = Objects.requireNonNull(cmd, "command");
    this.secrets = Objects.requireNonNull(secrets, "secrets");
    this.mode = Objects.requireNonNull(runningMode, "runningMode");
  }

  void runThenJoin() throws IOException, InterruptedException {
    LOGGER.info(
        "Run [{}], runningMode: {}, environment: {}, secrets: {}",
        cmd,
        mode,
        System.getenv(),
        maskSecrets(secrets));

    // Run process
    Process process = Runtime.getRuntime().exec(cmd, getEnvironment(secrets));
    long pid = process.toHandle().pid();

    if (process.isAlive()) {
      LOGGER.debug("Running [{}] on pid {}", cmd, process.toHandle().pid());

      SignalHandler destroyHandler = newDestroyHandler(process);
      Signal.handle(new Signal("TERM"), destroyHandler);
      Signal.handle(new Signal("INT"), destroyHandler);

      if (mode == RunningMode.INPUT) {
        writeSecrets(process.getOutputStream(), secrets);
      }
    }

    Thread stdout = runOutputFollower(pid + "-stdout", process.getInputStream(), System.out);
    Thread stderr = runOutputFollower(pid + "-stderr", process.getErrorStream(), System.err);

    stdout.join();
    stderr.join();

    int exitCode = process.waitFor();

    LOGGER.info("Exited [{}], exit code {}", cmd, exitCode);
  }

  private String[] getEnvironment(Map<String, String> secrets) {
    Map<String, String> environment = new HashMap<>();

    if (mode == RunningMode.INPUT) {
      environment.putAll(System.getenv());
    }
    if (mode == RunningMode.ENV) {
      environment.putAll(System.getenv()); // parent env first
      environment.putAll(secrets); // secrets second (can override)
    }

    return environment.entrySet().stream()
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .toArray(String[]::new);
  }

  private static Map<String, String> maskSecrets(Map<String, String> secrets) {
    return secrets.entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> mask(entry.getValue())));
  }

  private static String mask(String data) {
    if (data == null || data.isEmpty() || data.length() < 5) {
      return "*****";
    }
    return data.replace(data.substring(2, data.length() - 2), "***");
  }

  private static SignalHandler newDestroyHandler(Process process) {
    return signal -> {
      final long pid = process.toHandle().pid();

      if (!process.isAlive()) {
        return;
      }

      LOGGER.debug("[destroy][{}] destroying process ...", pid);

      try {
        if (!IS_RUNNING_ON_WINDOWS) {
          process.destroyForcibly().waitFor();
        } else {
          Runtime.getRuntime().exec(String.format(WINDOWS_TASKKILL_FORMAT, pid)).waitFor();
        }
        LOGGER.debug("[destroy][{}] destroyed process", pid);
      } catch (Exception e) {
        LOGGER.warn("[destroy][{}] Exception occurred: {}", pid, e.toString());
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

  private static void writeSecrets(OutputStream outputStream, Map<String, String> secrets)
      throws IOException {

    JsonObject jsonObject = new JsonObject();
    secrets.forEach(jsonObject::add);

    try (OutputStreamWriter osw = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
      jsonObject.writeTo(osw);
    }
  }
}
