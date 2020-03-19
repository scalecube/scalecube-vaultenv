package io.scalecube.vaultenv;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VaultEnvironmentRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(VaultEnvironmentRunner.class);

  // Vault
  private static final String VAULT_ADDR_ENV = "VAULT_ADDR";
  private static final String VAULT_TOKEN_ENV = "VAULT_TOKEN";
  private static final String KUBERNETES_VAULT_ROLE_ENV = "VAULT_ROLE";
  private static final String VAULT_SECRETS_PATH_ENV = "VAULT_SECRETS_PATH";
  private static final String VAULT_ENGINE_VERSION_ENV = "VAULT_ENGINE_VERSION";
  private static final String DEFAULT_VAULT_ENGINE_VERSION = "1";

  /**
   * Main program.
   *
   * @param args args
   * @throws Exception exception
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("wrong args.length");
    }
    if (args[0].isEmpty() || args[0].trim().isEmpty()) {
      throw new IllegalArgumentException("cmd is required");
    }

    final String cmd = args[0];

    Map<String, String> env = System.getenv();

    final String vaultAddr = Objects.requireNonNull(env.get(VAULT_ADDR_ENV), "vault address");
    final String secretsPath =
        Objects.requireNonNull(env.get(VAULT_SECRETS_PATH_ENV), "vault secret path");

    String vaultEngineVersion =
        env.getOrDefault(VAULT_ENGINE_VERSION_ENV, DEFAULT_VAULT_ENGINE_VERSION);

    Map<String, String> secrets =
        VaultInvoker.builder(secretsPath)
            .options(c -> c.address(vaultAddr))
            .options(c -> c.putSecretsEngineVersionForPath(secretsPath + "/", vaultEngineVersion))
            .tokenSupplier(getVaultTokenSupplier(env))
            .build()
            .readSecrets();

    LOGGER.info(
        "Executing cmd: [{}], env: {}",
        cmd,
        Arrays.asList(toEnvironment(secrets, env, true /*mask*/)));

    int exitCode =
        Runtime.getRuntime().exec(cmd, toEnvironment(secrets, env, false /*mask*/)).waitFor();

    LOGGER.info("Cmd: [{}] finished with exit code {}", cmd, exitCode);
  }

  private static VaultTokenSupplier getVaultTokenSupplier(Map<String, String> environment) {
    String vaultToken = environment.get(VAULT_TOKEN_ENV);
    String kubernetesVaultRole = environment.get(KUBERNETES_VAULT_ROLE_ENV);

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

  private static String[] toEnvironment(
      Map<String, String> secrets, Map<String, String> parentEnv, boolean mask) {

    Map<String, String> environment = new HashMap<>();

    Map<String, String> secretsEnv =
        secrets.entrySet().stream()
            .collect(
                Collectors.toMap(
                    entry -> toEnvironment(entry.getKey()),
                    entry -> mask ? mask(entry.getValue()) : entry.getValue()));

    environment.putAll(secretsEnv); // secrets first
    environment.putAll(parentEnv); // parent env second

    return environment.entrySet().stream()
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .toArray(String[]::new);
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
}
