# Post Quantum Meta-Transactions relay signer
This project provides a transparent layer between a JSON-RPC user/DApp user and a permissioned blockchain. It works by intercepting the raw transaction, adding a Falcon-512 signature, and relaying it to the blockchain for beign executed by a relay hub contract.

## Prerequisites
  * Java +11

## Building
You'll need to setup a Github token in order to download the `liboqs-java` from a Github Package Registry repository. For that matter, [Gradle](https://www.gradle.org) will load the following environment variables.
  * **USERNAME**: your Github username
  * **TOKEN**: a Github personal access token with `repo:read` scope. Please read [Creating a personal access token](https://docs.github.com/en/github/authenticating-to-github/creating-a-personal-access-token) for more information

Once you configure the environment properly, you can build it with the following command
```shell
$ ./gradlew build
```
Once the build cycle completes, you'll find the distribution in `build/distributions/pq-relay-signer.zip`. This zip contains the main executable in `bin/pq-relay-signer`.

## Configuration
The relay signer configuration is accomplished by a JSON file provided to the executable via the `-conf` flag. This JSON file must contain the following fields.
  * **bindingHost**: network interface to bind the signer
  * **bindingPort**: port to bind the signer
  * **remoteHost**: the host name for a JSON-RPC HTTP interface to interact with the blockchain
  * **remotePort**: the port for a JSON-RPC HTTP interface to interact with the blockchain
  * **remoteSsl**: set to true if the JSON-RPC HTTP interface requires a SSL connection
  * **remoteUri**: root of the remote JSON-RPC HTTP interface
  * **falconSecretKey**: Falcon-512 secret key
  * **falconPublicKey**: Falcon-512 public key for the _falconSecretKey_
  * **ethereumSecretKey**: SECP256K1 secret key
  * **relayHubAddress**: Ethereum address for the relayHub contract
E.g.
```json
{
  "bindingHost" : "0.0.0.0",
  "bindingPort" : 5050,
  "remoteHost" : "localhost",
  "remotePort" : 8545,
  "remoteSsl" : false,
  "remoteUri" : "/",
  "falconSecretKey" : "0x...",
  "falconPublicKey" : "0x...",
  "ethereumSecretKey" : "0x...",
  "relayHubAddress" : "0x..."
}
```

## Running
As this is a [Vert.x](https://vertx.io) project, you must pass the following arguments for running the application: `run org.iadb.tech.quantum.MainVerticle -conf /path/to/conf.json`. I.e.
```shell
$ bin/pq-relay-signer run org.iadb.tech.quantum.MainVerticle -conf /path/to/conf.json
```

### Docker
The `Dockerfile` present in this project is a handy way of running the relay signer. This container expects to run with a volume mounted in `/app` pointing to an exploded installation of the project. The container can be configured with the following environment variables matching each of the fields of the JSON file described above:
  * **PQ_RELAY_SIGNER_BINDING_HOST**
  * **PQ_RELAY_SIGNER_BINDING_PORT**
  * **PQ_RELAY_SIGNER_REMOTE_HOST**
  * **PQ_RELAY_SIGNER_REMOTE_PORT**
  * **PQ_RELAY_SIGNER_REMOTE_SSL**
  * **PQ_RELAY_SIGNER_REMOTE_URI**
  * **PQ_RELAY_SIGNER_FALCON_SK**
  * **PQ_RELAY_SIGNER_FALCON_PK**
  * **PQ_RELAY_SIGNER_ETHEREUM_SK**
  * **PQ_RELAY_SIGNER_RELAY_HUB_ADDRESS**
If this is meant to be run in a permissioned environment, intead of passing the `PQ_RELAY_SIGNER_RELAY_HUB_ADDRESS` variable, you can pass the `PQ_RELAY_SIGNER_INGRESS_ADDRESS` and the image will resolve the proper relay hub address on startup.
