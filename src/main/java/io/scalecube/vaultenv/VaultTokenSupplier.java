package io.scalecube.vaultenv;

import com.bettercloud.vault.EnvironmentLoader;
import com.bettercloud.vault.VaultConfig;

@FunctionalInterface
public interface VaultTokenSupplier {

  String getToken(EnvironmentLoader environmentLoader, VaultConfig config) throws Exception;
}
