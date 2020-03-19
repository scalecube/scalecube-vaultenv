package io.scalecube.vaultenv;

import com.bettercloud.vault.EnvironmentLoader;
import com.bettercloud.vault.VaultConfig;
import java.util.Objects;

public final class EnvironmentVaultTokenSupplier implements VaultTokenSupplier {

  public String getToken(EnvironmentLoader environmentLoader, VaultConfig config) {
    return Objects.requireNonNull(config.getToken(), "vault token");
  }
}
