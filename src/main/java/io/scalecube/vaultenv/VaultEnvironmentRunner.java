package io.scalecube.vaultenv;

import java.util.Map;
import java.util.Objects;

public class VaultEnvironmentRunner {

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
    Map<String, String> environment = System.getenv();

    final String vaultAddr =
        Objects.requireNonNull(environment.get(VAULT_ADDR_ENV), "vault address");
    final String secretsPath =
        Objects.requireNonNull(environment.get(VAULT_SECRETS_PATH_ENV), "vault secret path");

    final String vaultEngineVersion =
        environment.getOrDefault(VAULT_ENGINE_VERSION_ENV, DEFAULT_VAULT_ENGINE_VERSION);

    Map<String, String> secrets =
        VaultInvoker.builder(secretsPath)
            .options(c -> c.address(vaultAddr))
            .options(c -> c.putSecretsEngineVersionForPath(secretsPath, vaultEngineVersion))
            .tokenSupplier(getVaultTokenSupplier(environment))
            .build()
            .readSecrets();
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
}
