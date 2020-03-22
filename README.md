# Scalecube VaultEnvironment

Scalecube VaultEnvironment is an utility for reading static secrets from Vault server 
and running with them a command or a script. 

## Usage

- Get a binary `scalecube-vaultenv-VERSION-shaded.jar`
- Install Java (11+)
- Export configuration environment variables (see below) 
- Execute in terminal: `java -jar scalecube-vaultenv-x.y.z-shaded.jar "[CMD]" [RUNNING_MODE]`.

`CMD` -- command to run, can be for example `npm start` or `python ./start_app.py`, 
and must come with double quotes. 

`RUNNING_MODE` -- there're two running modes: `--input` (vaultenv shall pass secrets to `stdin` of the `CMD` process) and `--env` (vaultenv shall pass secrets as environment variables of the `CMD` process). **NOTE: the latter approach is not recommended on prod environments ([finding env variables in kubernetes](https://blog.nillsf.com/index.php/2020/02/24/dont-use-environment-variables-in-kubernetes-to-consume-secrets/), [show env variables on linux](https://ma.ttias.be/show-the-environment-variables-of-a-running-process-in-linux/)).

## Child process

Given in program arguments CMD will be executed in separate process with following semantic: 
- Output(both stdout and stderr) of the forked child process will be redirected to the console 
and maintained by parent scalecube-vaultenv java process.
- If scalecube-vaultenv java exits then forked child process exits as well (by SIGINT or SIGTERM).**NOTE: on windows you have to have `taskkill` installed for proper child process destroy. 
- An opposite is also true, if forked child process exits then scalecube-vaultenv java exits as well.
- Forked CMD process inherits working directory and environment variables of the parent scalecube-vaultenv java runner.

## Config

Environment variables to run a jar: 

- `VAULT_ADDR` -- vault server address (required)
- `VAULT_SECRETS_PATH` -- vault secrets path (required)
- `VAULT_ENGINE_VERSION` -- vault KV engine version, by default `1` (being set globally)
- `VAULT_TOKEN` -- vault token for token-auth backend (optional, if `VAULT_ROLE` is set)
- `VAULT_ROLE` -- vault role for lubernetes-auth backend (optional, if `VAULT_TOKEN` is set)


## Running in container

To run scalecube-vaultenv in container you have to have image with java (11+), this is the only requirement. For example, this is how to integrate it to a nodejs:

```dockerfile
FROM timbru31/java-node:11-jre

...
RUN wget -O ./scalecube-vaultenv.jar https://oss.sonatype.org/service/local/repositories/releases/content/io/scalecube/scalecube-vaultenv/0.1.1/scalecube-vaultenv-0.1.1-shaded.jar
...
CMD ["java","-jar", "./scalecube-vaultenv.jar", "npm run-script robokit-start", "--input"]
```

## Maven 

Binaries and dependency information for Maven can be found at 
[http://search.maven.org](http://search.maven.org/#search%7Cga%7C1%7Cio.scalecube.config).

Maven dependency: 

``` xml
<dependency>
  <groupId>io.scalecube</groupId>
  <artifactId>scalecube-vaultenv</artifactId>
  <version>x.y.z</version>
</dependency>
```

## Bugs and Feedback

For bugs, questions and discussions please use the [GitHub Issues](https://github.com/scalecube/scalecube-vaultenv/issues).

## License

[Apache License, Version 2.0](https://github.com/scalecube/scalecube-vaultenv/blob/master/LICENSE.txt)
