package io.scalecube.vaultenv;

import com.bettercloud.vault.VaultException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class VaultEnvironmentRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger("VaultEnvironment");

  // Vault
  private static final String VAULT_ADDR_ENV = "VAULT_ADDR";
  private static final String VAULT_TOKEN_ENV = "VAULT_TOKEN";
  private static final String KUBERNETES_VAULT_ROLE_ENV = "VAULT_ROLE";
  private static final String VAULT_SECRETS_PATH_ENV = "VAULT_SECRETS_PATH";
  private static final String VAULT_ENGINE_VERSION_ENV = "VAULT_ENGINE_VERSION";
  private static final int DEFAULT_VAULT_ENGINE_VERSION = 1;

  static {
    // JUL support
    LogManager.getLogManager().reset();
    SLF4JBridgeHandler.install();
  }

  /**
   * Main program.
   *
   * @param args args
   * @throws Exception exception
   */
  public static void main(String[] args) throws Exception {
    LOGGER.info("Starting, arguments: {}", Arrays.asList(args));

    checkArgs(args);

    String cmd = args[0];
    RunningMode runningMode = getRunningMode(args);
    Map<String, String> secrets = readSecrets();

    new ProcessInvoker(cmd, secrets, runningMode).runThenJoin();
  }

  private static Map<String, String> readSecrets() throws VaultException {
    String vaultAddr = Objects.requireNonNull(System.getenv(VAULT_ADDR_ENV), VAULT_ADDR_ENV);
    String secretsPath =
        Objects.requireNonNull(System.getenv(VAULT_SECRETS_PATH_ENV), VAULT_SECRETS_PATH_ENV);

    int vaultEngineVersion =
        Optional.ofNullable(System.getenv(VAULT_ENGINE_VERSION_ENV))
            .map(Integer::parseInt)
            .orElse(DEFAULT_VAULT_ENGINE_VERSION);

    return VaultInvoker.builder(secretsPath)
        .options(c -> c.address(vaultAddr))
        .options(c -> c.engineVersion(vaultEngineVersion))
        .tokenSupplier(getVaultTokenSupplier())
        .build()
        .readSecrets();
  }

  private static VaultTokenSupplier getVaultTokenSupplier() {
    String vaultToken = System.getenv(VAULT_TOKEN_ENV);
    String kubernetesVaultRole = System.getenv(KUBERNETES_VAULT_ROLE_ENV);

    if (vaultToken == null && kubernetesVaultRole == null) {
      throw new IllegalArgumentException("Vault auth scheme is required");
    }
    if (vaultToken != null && kubernetesVaultRole != null) {
      throw new IllegalArgumentException("Vault auth scheme is unclear");
    }

    // Direct token provider
    if (vaultToken != null) {
      return new EnvironmentVaultTokenSupplier();
    }
    // Kub8s token provider
    return new KubernetesVaultTokenSupplier();
  }

  private static void checkArgs(String[] args) {
    if (args.length < 2) {
      throw new IllegalArgumentException("wrong number of arguments (must be 2)");
    }
    if (args[0].isEmpty() || args[0].trim().isEmpty()) {
      throw new IllegalArgumentException("command is required");
    }
    if (args[1].isEmpty() || args[1].trim().isEmpty()) {
      throw new IllegalArgumentException("running mode is required");
    }
  }

  private static RunningMode getRunningMode(String[] args) {
    switch (args[1]) {
      case "--env":
        return RunningMode.ENV;
      case "--input":
        return RunningMode.INPUT;
      default:
        throw new IllegalArgumentException("wrong running mode: " + args[1]);
    }
  }
}
