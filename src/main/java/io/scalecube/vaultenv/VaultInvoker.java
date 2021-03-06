package io.scalecube.vaultenv;

import com.bettercloud.vault.EnvironmentLoader;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.HealthResponse;
import com.bettercloud.vault.response.LogicalResponse;
import com.bettercloud.vault.response.LookupResponse;
import com.bettercloud.vault.rest.RestResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class VaultInvoker {

  private static final Logger LOGGER = LoggerFactory.getLogger("VaultEnvironment");

  private static final int STATUS_CODE_RESPONSE_OK = 200;

  private final Builder builder;

  private VaultInvoker(Builder builder) {
    this.builder = builder;
  }

  static Builder builder(String secretsPath) {
    return new Builder(secretsPath);
  }

  Map<String, String> readSecrets() throws VaultException {
    LogicalResponse response = createVault().logical().read(builder.secretsPath);

    RestResponse restResponse = response.getRestResponse();
    if (restResponse.getStatus() != STATUS_CODE_RESPONSE_OK) {
      String body = toResponseString(restResponse);
      LOGGER.error("Vault responded with code: {}, message: {}", restResponse.getStatus(), body);
      throw new VaultException(body, restResponse.getStatus());
    }

    return new HashMap<>(response.getData());
  }

  private Vault createVault() throws VaultException {
    VaultConfig vaultConfig =
        builder
            .options
            .apply(new VaultConfig())
            .environmentLoader(builder.environmentLoader)
            .build();

    LOGGER.debug(
        "Created VaultConfig instance: {}, getting vault client token using: {} ...",
        vaultConfig,
        builder.tokenSupplier);

    String token;
    try {
      token = builder.tokenSupplier.getToken(builder.environmentLoader, vaultConfig);
    } catch (Exception e) {
      throw new VaultException(e);
    }

    LOGGER.debug("Obtained vault client token ({}), creating Vault instance ...", mask(token));

    Vault vault = new Vault(vaultConfig.token(token));
    HealthResponse healthResponse = vault.debug().health();
    LookupResponse lookupSelf = vault.auth().lookupSelf();

    LOGGER.info(
        "Created Vault instance: {}, "
            + "lookupSelf.displayName: {}, "
            + "lookupSelf.username: {}, "
            + "lookupSelf.policies: {}, "
            + "lookupSelf.creationTime: {}, "
            + "lookupSelf.creationTtl: {}, "
            + "lookupSelf.ttl: {}, "
            + "healthInfo: {}",
        vault,
        lookupSelf.getDisplayName(),
        lookupSelf.getUsername(),
        lookupSelf.getPolicies(),
        lookupSelf.getCreationTime(),
        lookupSelf.getCreationTTL(),
        lookupSelf.getTTL(),
        toResponseString(healthResponse.getRestResponse()));

    return vault;
  }

  private static String toResponseString(RestResponse response) {
    return new String(response.getBody(), StandardCharsets.UTF_8);
  }

  private static String mask(String data) {
    if (data == null || data.isEmpty() || data.length() < 5) {
      return "*****";
    }
    return data.replace(data.substring(2, data.length() - 2), "***");
  }

  static class Builder {

    private final String secretsPath;
    private Function<VaultConfig, VaultConfig> options = Function.identity();
    private VaultTokenSupplier tokenSupplier = new EnvironmentVaultTokenSupplier();
    private EnvironmentLoader environmentLoader = new EnvironmentLoader();

    private Builder(String secretsPath) {
      this.secretsPath = secretsPath;
    }

    Builder options(UnaryOperator<VaultConfig> config) {
      this.options = this.options.andThen(config);
      return this;
    }

    @SuppressWarnings("unused")
    Builder environmentLoader(EnvironmentLoader environmentLoader) {
      this.environmentLoader = environmentLoader;
      return this;
    }

    Builder tokenSupplier(VaultTokenSupplier supplier) {
      this.tokenSupplier = supplier;
      return this;
    }

    VaultInvoker build() {
      Builder builder = new Builder(secretsPath);
      builder.environmentLoader = environmentLoader;
      builder.options = options;
      builder.tokenSupplier = tokenSupplier;
      return new VaultInvoker(builder);
    }
  }
}
