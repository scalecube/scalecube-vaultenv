package io.scalecube.vaultenv;

import com.bettercloud.vault.response.LogicalResponse;

public class VaultEnvironmentRunner {

  public static void main(String[] args) throws Exception {
    VaultInvoker.builder().options()
    LogicalResponse response = vault.invoke(vault -> vault.logical().read(secretsPath));
    response.getData().entrySet().stream();
  }
}
