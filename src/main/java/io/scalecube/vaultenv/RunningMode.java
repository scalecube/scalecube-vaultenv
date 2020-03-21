package io.scalecube.vaultenv;

public enum RunningMode {

  /**
   * VaultEnvironment running mode where secrets will be passed to process as environment variables.
   * While not recommened, this option still can be set explicitly in cmd line args for {@link
   * VaultEnvironmentRunner}.
   */
  ENV,

  /**
   * VaultEnvironment running mode where secrets will be passed to process standard input. This is
   * recommended running mode.
   */
  INPUT
}
