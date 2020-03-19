package io.scalecube.vaultenv;

import com.bettercloud.vault.EnvironmentLoader;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.LookupResponse;
import com.bettercloud.vault.response.VaultResponse;
import com.bettercloud.vault.rest.RestResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VaultInvoker {

  private static final Logger LOGGER = LoggerFactory.getLogger(VaultInvoker.class);

  private static final int STATUS_CODE_FORBIDDEN = 403;
  private static final int STATUS_CODE_HELTH_OK = 200;
  private static final int STATUS_CODE_RESPONSE_OK = 200;
  private static final int STATUS_CODE_RESPONSE_NO_DATA = 204;

  private final Builder builder;

  private volatile Vault vault;

  private VaultInvoker(Builder builder) {
    this.builder = builder;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Invokes a given call with vault.
   *
   * @param call call
   * @return vault response
   */
  public <T extends VaultResponse> T invoke(VaultCall<T> call) throws VaultException {
    Vault vault = this.vault;
    try {
      if (vault == null) {
        vault = recreateVault(null);
      }
      T response = call.apply(vault);
      checkResponse(response.getRestResponse());
      return response;
    } catch (VaultException e) {
      // try recreate Vault according to https://www.vaultproject.io/api/overview#http-status-codes
      if (e.getHttpStatusCode() == STATUS_CODE_FORBIDDEN) {
        LOGGER.warn("Authentication details are incorrect, occurred during invoking Vault", e);
        return call.apply(recreateVault(vault));
      }
      throw e;
    }
  }

  private synchronized Vault recreateVault(Vault prev) throws VaultException {
    try {
      if (!Objects.equals(prev, vault) && vault != null) {
        return vault;
      }
      vault = null;

      VaultConfig vaultConfig =
          builder
              .options
              .apply(new VaultConfig())
              .environmentLoader(builder.environmentLoader)
              .build();
      String token = builder.tokenSupplier.getToken(builder.environmentLoader, vaultConfig);
      Vault vault = new Vault(vaultConfig.token(token));
      checkVault(vault);
      LookupResponse lookupSelf = vault.auth().lookupSelf();
      LOGGER.info("Initialized new Vault");
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("More Vault details: {}", toResponseString(lookupSelf.getRestResponse()));
      }
      this.vault = vault;
    } catch (VaultException e) {
      LOGGER.error("Could not initialize and validate the vault", e);
      throw e;
    }
    return vault;
  }

  /**
   * Checks vault is active. See
   * https://www.vaultproject.io/api/system/health.html#read-health-information.
   *
   * @param vault vault
   */
  private void checkVault(Vault vault) throws VaultException {
    RestResponse restResponse = vault.debug().health().getRestResponse();
    if (restResponse.getStatus() == STATUS_CODE_HELTH_OK) {
      return;
    }
    throw new VaultException(toResponseString(restResponse), restResponse.getStatus());
  }

  /**
   * Checks rest response. See https://www.vaultproject.io/api/overview#http-status-codes.
   *
   * @param restResponse rest response
   */
  private void checkResponse(RestResponse restResponse) throws VaultException {
    if (restResponse == null) {
      return;
    }
    int status = restResponse.getStatus();
    switch (status) {
      case STATUS_CODE_RESPONSE_OK:
      case STATUS_CODE_RESPONSE_NO_DATA:
        return;
      default:
        String body = toResponseString(restResponse);
        LOGGER.warn("Vault responded with code: {}, message: {}", status, body);
        throw new VaultException(body, status);
    }
  }

  private String toResponseString(RestResponse response) {
    return new String(response.getBody(), StandardCharsets.UTF_8);
  }

  @FunctionalInterface
  public interface VaultCall<T extends VaultResponse> {

    T apply(Vault vault) throws VaultException;
  }

  public static class Builder {

    private Function<VaultConfig, VaultConfig> options = Function.identity();
    private VaultTokenSupplier tokenSupplier = new EnvironmentVaultTokenSupplier();
    private EnvironmentLoader environmentLoader = new EnvironmentLoader();

    private Builder() {}

    public Builder options(UnaryOperator<VaultConfig> config) {
      this.options = this.options.andThen(config);
      return this;
    }

    public Builder environmentLoader(EnvironmentLoader environmentLoader) {
      this.environmentLoader = environmentLoader;
      return this;
    }

    public Builder tokenSupplier(VaultTokenSupplier supplier) {
      this.tokenSupplier = supplier;
      return this;
    }

    /**
     * Builds vault invoker.
     *
     * @return instance of {@link VaultInvoker}
     */
    public VaultInvoker build() {
      Builder builder = new Builder();
      builder.environmentLoader = environmentLoader;
      builder.options = options;
      builder.tokenSupplier = tokenSupplier;
      return new VaultInvoker(builder);
    }
  }
}
