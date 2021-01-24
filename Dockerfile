FROM openjdk:11-jdk as base

RUN apt-get update && apt-get install --no-install-recommends -yV \
    dpkg-dev \
    wget \
    ca-certificates

RUN mkdir /debs/
RUN wget --directory-prefix=/debs/ https://github.com/lacchain/liboqs-debian/releases/download/0.4.0/liboqs_0.4.0_amd64.deb
RUN wget --directory-prefix=/debs/ https://github.com/lacchain/liboqs-debian/releases/download/0.4.0/SHA256SUMS
RUN cd /debs/ && sha256sum --check --ignore-missing --status SHA256SUMS && dpkg-scanpackages . /dev/null | gzip -9c > Packages.gz
RUN echo "deb [trusted=yes] file:/debs ./" >> /etc/apt/sources.list

FROM base as runner

RUN apt-get update && apt-get install --no-install-recommends -yV \
    liboqs \
    netcat \
    curl \
    jq

COPY ./build/install/pq-relay-signer/ /app/

RUN echo '#!/bin/sh\n\
set -x\n\
REMOTE_PORT=${PQ_RELAY_SIGNER_REMOTE_PORT:-8545}\n\
if [ ! -z ${PQ_RELAY_SIGNER_INGRESS_ADDRESS} ]; then\n\
  PQ_RELAY_SIGNER_RELAY_HUB_ADDRESS=$(curl -s -X POST --data '\''{"jsonrpc":"2.0","id":4,"method":"eth_call","params":[{"to":"'\''${PQ_RELAY_SIGNER_INGRESS_ADDRESS}'\''", "data":"0x0d2020dd72756c6573000000000000000000000000000000000000000000000000000000"}, "latest"]}'\'' http://${PQ_RELAY_SIGNER_REMOTE_HOST}:${REMOTE_PORT} | jq .result | sed -n '\''s/"0x.*\(.\{40\}\)"$/{"jsonrpc":"2.0","id":4,"method":"eth_call","params":[{"to":"0x\\1", "data":"0x7bdf2ec7"}, "latest"]}/p'\'' | curl -s -X POST --data-binary @- http://${PQ_RELAY_SIGNER_REMOTE_HOST}:${REMOTE_PORT} | jq .result | sed -n '\''s/"0x.*\(.\{40\}\)"$/0x\\1/p'\'')\n\
fi\n\
cat > /conf.json <<_EOF_\n\
{\n\
  "bindingHost" : "${PQ_RELAY_SIGNER_BINDING_HOST:-0.0.0.0}",\n\
  "bindingPort" : ${PQ_RELAY_SIGNER_BINDING_PORT:-5050},\n\
  "remoteHost" : "${PQ_RELAY_SIGNER_REMOTE_HOST}",\n\
  "remotePort" : ${REMOTE_PORT},\n\
  "remoteSsl" : ${PQ_RELAY_SIGNER_REMOTE_SSL:-false},\n\
  "remoteUri" : "${PQ_RELAY_SIGNER_REMOTE_URI:-/}",\n\
  "falconSecretKey" : "${PQ_RELAY_SIGNER_FALCON_SK}",\n\
  "falconPublicKey" : "${PQ_RELAY_SIGNER_FALCON_PK}",\n\
  "ethereumSecretKey" : "${PQ_RELAY_SIGNER_ETHEREUM_SK}",\n\
  "relayHubAddress" : "${PQ_RELAY_SIGNER_RELAY_HUB_ADDRESS}"\n\
}\n\
_EOF_\n\
\n\
export PQ_RELAY_SIGNER_OPTS="${PQ_RELAY_SIGNER_OPTS} -Dlogback.configurationFile=file:/app/logback.xml"\n\
/app/bin/pq-relay-signer run org.iadb.tech.quantum.MainVerticle -conf /conf.json\n'\
>> /run.sh

RUN cat /run.sh

RUN chmod +x /run.sh

ENTRYPOINT ["/run.sh"]

HEALTHCHECK --start-period=5s --interval=5s CMD nc -z localhost 5005 || exit 1
