package io.scalecube.vaultenv;

import com.bettercloud.vault.EnvironmentLoader;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.Collectors;

public final class KubernetesVaultTokenSupplier implements VaultTokenSupplier {

  private static final String VAULT_ROLE = "VAULT_ROLE";
  private static final String SERVICE_ACCOUNT_TOKEN_PATH =
      "/var/run/secrets/kubernetes.io/serviceaccount/token";

  @Override
  public String getToken(EnvironmentLoader environmentLoader, VaultConfig config) {
    String role = Objects.requireNonNull(environmentLoader.loadVariable(VAULT_ROLE), "vault role");
    try {
      String jwt = Files.lines(Paths.get(SERVICE_ACCOUNT_TOKEN_PATH)).collect(Collectors.joining());
      return Objects.requireNonNull(
          new Vault(config).auth().loginByKubernetes(role, jwt).getAuthClientToken(),
          "vault token");
    } catch (Exception e) {
      throw ThrowableUtil.propagate(e);
    }
  }
}
