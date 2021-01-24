package org.iadb.tech.quantum;

import io.vertx.core.AbstractVerticle;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import org.apache.tuweni.eth.Address;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Security;

public class MainVerticle extends AbstractVerticle {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }
    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start() throws Exception {
        vertx.deployVerticle(
            new RelaySignerVerticle(
                config().getString("bindingHost"),
                config().getInteger("bindingPort"),
                Bytes.fromHexString(config().getString("falconSecretKey")).toArray(),
                Bytes.fromHexString(config().getString("falconPublicKey")).toArray(),
                SECP256K1.KeyPair.fromSecretKey(SECP256K1.SecretKey.fromBytes(Bytes32.fromHexString(config().getString("ethereumSecretKey")))), config().getString("remoteHost"),
                config().getInteger("remotePort"),
                config().getBoolean("remoteSsl"),
                config().getString("remoteUri"),
                Address.fromHexString(config().getString("relayHubAddress"))
            )
        );
    }
}
