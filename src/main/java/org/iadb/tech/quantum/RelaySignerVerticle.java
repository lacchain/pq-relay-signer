package org.iadb.tech.quantum;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.SECP256K1;
import org.apache.tuweni.eth.Address;
import org.apache.tuweni.eth.Transaction;
import org.apache.tuweni.units.bigints.UInt256;
import org.apache.tuweni.units.ethereum.Gas;
import org.apache.tuweni.units.ethereum.Wei;
import org.openquantumsafe.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.FunctionEncoder;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;

public class RelaySignerVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(RelaySignerVerticle.class);

    // taken from liboqs C sig.h header, OQS_SIG_alg_falcon_512
    private static final String SIGNATURE_ALGORITHM = "Falcon-512";

    private final String bindingHost;
    private final int bindingPort;
    private final Signature falconSigner;
    private final byte[] falconPublicKey;
    private final SECP256K1.KeyPair signerEthereumKeyPair;
    private final String remoteHost;
    private final Integer remotePort;
    private final Boolean remoteSsl;
    private final String remoteUri;
    private final Address relayHubAddress;
    private final Address signerAddress;

    private int jsonRpcId = 0;

    public RelaySignerVerticle(String bindingHost,
                               int bindingPort,
                               byte[] falconSecretKey,
                               byte[] falconPublicKey,
                               SECP256K1.KeyPair signerEthereumKeyPair,
                               String remoteHost,
                               Integer remotePort,
                               Boolean remoteSsl,
                               String remoteUri,
                               Address relayHubAddress) {
        this.bindingHost = bindingHost;
        this.bindingPort = bindingPort;
        this.falconSigner = new Signature(SIGNATURE_ALGORITHM, falconSecretKey);
        this.falconPublicKey = falconPublicKey;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.remoteSsl = remoteSsl;
        this.remoteUri = remoteUri;
        this.relayHubAddress = relayHubAddress;
        this.signerEthereumKeyPair = signerEthereumKeyPair;
        this.signerAddress = Address.fromPublicKey(signerEthereumKeyPair.publicKey());
    }

    @Override
    public void start() {
        WebClient webClient = WebClient.create(vertx, new WebClientOptions()
                .setDefaultPort(remotePort)
                .setDefaultHost(remoteHost)
                .setSsl(remoteSsl));

        Router router = Router.router(vertx);
        router.route()
            .method(HttpMethod.POST)
            .failureHandler(failedRoutingContext -> logger.error("Unhandled exception", failedRoutingContext.failure()))
            .handler(LoggerHandler.create())
            .handler(BodyHandler.create())
            .handler(ctx -> {
                JsonObject jsonRpcMethodCall = ctx.getBodyAsJson();
                HttpServerResponse response = ctx.response();
                response.setChunked(true);
                String method = jsonRpcMethodCall.getString("method");
                if ("eth_sendRawTransaction".equals(method)) {
                    String params = jsonRpcMethodCall.getJsonArray("params").getString(0);
                    // decoded here to prevent node roundtrips on invalid data
                    Transaction decodedTransaction = Transaction.fromBytes(Bytes.fromHexString(params));

                    JsonObject jsonRpcGetTransactionCountCall = new JsonObject()
                        .put("jsonrpc", "2.0")
                        .put("id", jsonRpcId++)
                        .put("method", "eth_getTransactionCount")
                        .put("params", new JsonArray(Arrays.asList(signerAddress.toHexString(), "latest")));
                    webClient
                        .post(remoteUri)
                        .as(BodyCodec.jsonObject())
                        .sendJsonObject(jsonRpcGetTransactionCountCall, jsonRpcGetTransactionCountHandler -> {
                            if (jsonRpcGetTransactionCountHandler.succeeded()) {
                                Bytes signingDataHex = Transaction.signatureData(
                                    decodedTransaction.getNonce(),
                                    decodedTransaction.getGasPrice(),
                                    decodedTransaction.getGasLimit(),
                                    decodedTransaction.getTo(),
                                    decodedTransaction.getValue(),
                                    decodedTransaction.getPayload(),
                                    decodedTransaction.getChainId()
                                );
                                Bytes falconSignature = Bytes.wrap(falconSigner.sign(signingDataHex.toArray()));;
                                org.apache.tuweni.crypto.SECP256K1.Signature senderSignature = decodedTransaction.getSignature();
                                org.web3j.abi.datatypes.Function relayMetaTransactionCall;
                                try {
                                    relayMetaTransactionCall = FunctionEncoder.makeFunction(
                                        "relayMetaTx",
                                        Arrays.asList("bytes", "uint8", "bytes32", "bytes32", "bytes", "bytes"),
                                        Arrays.asList(
                                            signingDataHex.toHexString(),
                                            senderSignature.v(),
                                            senderSignature.r().toString(16),
                                            senderSignature.s().toString(16),
                                            Bytes.wrap(falconPublicKey).toHexString(),
                                            Bytes.wrap(falconSignature).toHexString()
                                        ),
                                        Collections.singletonList("bool")
                                    );
                                } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                                    logger.error("Unable to build relayMetaTx call", e);
                                    throw new IllegalStateException(e);
                                }

                                JsonObject getTransactionCount = jsonRpcGetTransactionCountHandler.result().body();
                                Bytes payload = Bytes.fromHexString(FunctionEncoder.encode(relayMetaTransactionCall));
                                Transaction relayHubTransaction = new Transaction(
                                    UInt256.fromHexString(getTransactionCount.getString("result")),
                                    Wei.valueOf(0xFFFFFL),
                                    Gas.valueOf(0xFFFFFFFFFFL),
                                    relayHubAddress,
                                    Wei.valueOf(0),
                                    payload,
                                    signerEthereumKeyPair,
                                    decodedTransaction.getChainId()
                                );
                                JsonObject jsonSendRawTransactionCall = new JsonObject()
                                    .put("jsonrpc", "2.0")
                                    .put("id", jsonRpcId++)
                                    .put("method", "eth_sendRawTransaction")
                                    .put("params", new JsonArray(Collections.singletonList(relayHubTransaction.toBytes().toHexString())));
                                webClient
                                    .post(remoteUri)
                                    .as(BodyCodec.jsonObject())
                                    .sendJsonObject(jsonSendRawTransactionCall, sendRawTransactionHandler -> {
                                        if (sendRawTransactionHandler.succeeded()) {
                                            HttpResponse<JsonObject> sendRawTransactionResponse = sendRawTransactionHandler.result();
                                            JsonObject sendRawTransactionResult = sendRawTransactionResponse.body();
                                            sendRawTransactionResult.put("id", jsonRpcMethodCall.getInteger("id"));
                                            response.end(sendRawTransactionResult.toBuffer());
                                        } else {
                                            logger.error("Problem with eth_sendRawTransaction call");
                                            response.setStatusCode(500).end();
                                        }
                                    });
                            } else {
                                logger.error("Problem with eth_getTransactionCount call");
                                response.setStatusCode(500).end();
                            }
                        });
                } else if ("eth_getTransactionCount".equals(method)) {
                    String transactionCountAddress = jsonRpcMethodCall.getJsonArray("params").getString(0);

                    org.web3j.abi.datatypes.Function getNonceCall;
                    try {
                        getNonceCall = FunctionEncoder.makeFunction(
                            "getNonce",
                            Collections.singletonList("address"),
                            Collections.singletonList(transactionCountAddress),
                            Collections.singletonList("uint")
                        );
                    } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                        logger.error("Unable to build getNonce call", e);
                        throw new IllegalStateException(e);
                    }

                    JsonObject jsonEthCall = new JsonObject()
                        .put("jsonrpc", "2.0")
                        .put("id", jsonRpcId++)
                        .put("method", "eth_call")
                        .put("params", new JsonArray(Arrays.asList(
                            new JsonObject()
                                .put("to", relayHubAddress.toHexString())
                                .put("data", FunctionEncoder.encode(getNonceCall)),
                            "latest"
                        )));
                    webClient
                        .post(remoteUri)
                        .as(BodyCodec.jsonObject())
                        .sendJsonObject(jsonEthCall, jsonEthCallHandler -> {
                            if (jsonEthCallHandler.succeeded()) {
                                HttpResponse<JsonObject> remoteResponse = jsonEthCallHandler.result();
                                JsonObject ethCallResult = remoteResponse.body();
                                ethCallResult.put("id", jsonRpcMethodCall.getInteger("id"));
                                response.end(ethCallResult.toBuffer());
                            } else {
                                logger.error("Problem with eth_call call");
                                response.setStatusCode(500).end();
                            }
                        });
                } else {
                    webClient
                        .post(remoteUri)
                        .sendJsonObject(jsonRpcMethodCall, jsonRpcCallResult -> {
                            if (jsonRpcCallResult.succeeded()) {
                                HttpResponse<Buffer> remoteResponse = jsonRpcCallResult.result();
                                response.end(remoteResponse.body());
                            } else {
                                logger.error("Problem with {} call", method);
                                response.setStatusCode(500).end();
                            }
                        });
                }
        });

        vertx.createHttpServer().requestHandler(router).listen(bindingPort, bindingHost, listen -> {
            if (listen.succeeded()) {
                logger.info("Listening on {}:{}", bindingHost, bindingPort);
            } else {
                logger.error("Unable to start Demo Signer Server", listen.cause());
            }
        });
    }
}
