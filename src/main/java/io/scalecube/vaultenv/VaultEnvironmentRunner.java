package io.scalecube.vaultenv;

import com.bettercloud.vault.VaultException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class VaultEnvironmentRunner {

  // Vault
  private static final String VAULT_ADDR_ENV = "VAULT_ADDR";
  private static final String VAULT_TOKEN_ENV = "VAULT_TOKEN";
  private static final String KUBERNETES_VAULT_ROLE_ENV = "VAULT_ROLE";
  private static final String VAULT_SECRETS_PATH_ENV = "VAULT_SECRETS_PATH";
  private static final String VAULT_ENGINE_VERSION_ENV = "VAULT_ENGINE_VERSION";
  private static final int DEFAULT_VAULT_ENGINE_VERSION = 1;

  /**
   * Main program.
   *
   * @param args args
   * @throws Exception exception
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("wrong args.length (must be 1)");
    }
    if (args[0].isEmpty() || args[0].trim().isEmpty()) {
      throw new IllegalArgumentException("command is required");
    }

    String cmd = args[0];
    Map<String, String> secrets = getSecrets();

    new ProcessInvoker(cmd, secrets).runThenJoin();
  }

  private static Map<String, String> getSecrets() throws VaultException {
    String vaultAddr = Objects.requireNonNull(System.getenv(VAULT_ADDR_ENV), "vault address");
    String secretsPath =
        Objects.requireNonNull(System.getenv(VAULT_SECRETS_PATH_ENV), "vault secret path");

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
}
